package com.piercingxx.txxt.core

import java.nio.charset.CodingErrorAction

/** The header facts TxxT extracts from an inbound MM PDU. */
data class MmsPduInfo(
    /** MM message-type octet value (e.g. 0x82 m-notification-ind, 0x84 m-retrieve-conf), null when absent. */
    val messageType: Int?,
    /** Originator address exactly as encoded (e.g. "+15551234567/TYPE=PLMN"); null when absent/unparseable. */
    val from: String?,
    /** DATE field as epoch milliseconds; null when absent. */
    val dateMillis: Long?,
    /** CONTENT-TYPE as text when text-representable (e.g. "application/vnd.wap.multipart.related", "text/plain"); null otherwise. */
    val contentType: String?,
    /** X-Mms-Content-Location (the MMSC URL to retrieve); null when absent. */
    val contentLocation: String? = null,
    /** X-Mms-Transaction-Id; null when absent. */
    val transactionId: String? = null,
    /** X-Mms-Message-ID; null when absent. */
    val messageId: String? = null,
)

/**
 * Minimal, pure-Kotlin parser for the MM **header** portion of an inbound MMS
 * PDU — the byte payload delivered to a `WAP_PUSH_DELIVER` receiver for the
 * `application/vnd.wap.mms-message` MIME type.
 *
 * Scope (honest version):
 *
 * - This covers only the documented subset of OMA-MMS-ENC (v1.2, "Multimedia
 *   Messaging Service Encapsulation Protocol") header encodings: the leading
 *   X-Mms-Message-Type octet, FROM, DATE, CONTENT-TYPE (extension-media form),
 *   and safe skip-over for the bounded-shape fields listed below. Field-name
 *   assignments follow OMA-MMS-ENC v1.2 section 7.3 Table 21; on the wire a
 *   field name is a WSP Short-integer, i.e. the assigned number `| 0x80`
 *   (BCC=0x81, CC=0x82, CONTENT-TYPE=0x84, DATE=0x85, EXPIRY=0x88, FROM=0x89,
 *   MESSAGE-CLASS=0x8A, MESSAGE-ID=0x8B, MESSAGE-TYPE=0x8C, MMS-VERSION=0x8D,
 *   MESSAGE-SIZE=0x8E, PRIORITY=0x8F, READ-REPORT=0x90, REPORT-ALLOWED=0x91,
 *   RESPONSE-STATUS=0x92, RESPONSE-TEXT=0x93, SENDER-VISIBILITY=0x94,
 *   STATUS=0x95, SUBJECT=0x96, TO=0x97, TRANSACTION-ID=0x98).
 *
 * - Supported value shapes, per OMA-MMS-ENC section 7.2 ABNF:
 *     - FROM (`From-value = Value-length (Address-present-token
 *       Encoded-string-value | Insert-address-token)`): a Value-length span
 *       containing an optional 0x80/0x81 token, an optional charset octet
 *       (present only when its high bit is set; read and discarded — the text
 *       is always decoded as UTF-8), then a null-terminated UTF-8 string.
 *     - DATE (`Date-value = Long-integer`): length-octet (1..8) then
 *       big-endian bytes holding seconds since the epoch; converted to
 *       milliseconds by multiplying by 1000.
 *     - CONTENT-TYPE in three WSP forms: Constrained-media (a single
 *       well-known Short-integer, octet >= 0x80, no parameters),
 *       Content-general-form (Value-length then media type + parameters,
 *       skipped as a bounded span), and extension-media (null-terminated
 *       US-ASCII, e.g. "application/vnd.wap.multipart.related"). Verizon
 *       notification-ind PDUs use the well-known `application/vnd.wap.mms-message`
 *       code; rejecting that form dropped the photo before it was stored.
 *     - Skip-over (no extraction) for bounded-shape fields: TO/CC/BCC/SUBJECT/
 *       RESPONSE-TEXT/RETRIEVE-TEXT (bare `Encoded-string-value`: optional
 *       charset octet, then null-terminated text), TRANSACTION-ID/MESSAGE-ID/
 *       CONTENT-LOCATION (`Text-string`, null-terminated), MESSAGE-CLASS
 *       (`Class-identifier` single octet 128..131, or `Token-text` up to the
 *       null terminator), single-octet fields (DELIVERY-REPORT,
 *       MESSAGE-TYPE-as-later-field, MMS-VERSION, PRIORITY, READ-REPORT,
 *       REPORT-ALLOWED, RESPONSE-STATUS, SENDER-VISIBILITY, STATUS),
 *       MESSAGE-SIZE (`Long-integer`), and Value-length-delimited EXPIRY and
 *       DELIVERY-TIME.
 *
 * - Unknown field names are skipped with the WSP value-shape heuristic
 *   (Value-length span, single high-bit octet, or null-terminated text).
 *   Truncated data, unterminated strings, absurd lengths, and malformed
 *   UTF-8 still fail closed: [parse] returns null. It never throws on input.
 *
 * - Leading message-type handling: per the TxxT receive pipeline convention
 *   the PDU begins with the X-Mms-Message-Type **value** octet (e.g. 0x82).
 *   A PDU that instead begins with the X-Mms-Message-Type field-name octet
 *   0x8C followed by the value octet (the full WSP framing) is also accepted.
 *   An octet < 0x80 can be neither, so such input is rejected.
 *
 * - Attachment/body inspection is out of scope here by design; deep content
 *   inspection stays deferred to the tap-initiated download path
 *   (docs/PRIVACY.md section 8.1 — auto-download off).
 */
object MmsPduHeader {

    // Wire field names: OMA-MMS-ENC v1.2 section 7.3 Table 21, encoded as
    // WSP Short-integer (assigned number | 0x80).
    private const val FIELD_BCC = 0x81
    private const val FIELD_CC = 0x82
    private const val FIELD_CONTENT_LOCATION = 0x83
    private const val FIELD_CONTENT_TYPE = 0x84
    private const val FIELD_DATE = 0x85
    private const val FIELD_DELIVERY_REPORT = 0x86
    private const val FIELD_DELIVERY_TIME = 0x87
    private const val FIELD_EXPIRY = 0x88
    private const val FIELD_FROM = 0x89
    private const val FIELD_MESSAGE_CLASS = 0x8A
    private const val FIELD_MESSAGE_ID = 0x8B
    private const val FIELD_MESSAGE_TYPE = 0x8C
    private const val FIELD_MMS_VERSION = 0x8D
    private const val FIELD_MESSAGE_SIZE = 0x8E
    private const val FIELD_PRIORITY = 0x8F
    private const val FIELD_READ_REPORT = 0x90
    private const val FIELD_REPORT_ALLOWED = 0x91
    private const val FIELD_RESPONSE_STATUS = 0x92
    private const val FIELD_RESPONSE_TEXT = 0x93
    private const val FIELD_SENDER_VISIBILITY = 0x94
    private const val FIELD_STATUS = 0x95
    private const val FIELD_SUBJECT = 0x96
    private const val FIELD_TO = 0x97
    private const val FIELD_TRANSACTION_ID = 0x98
    private const val FIELD_RETRIEVE_TEXT = 0x9A
    private const val FIELD_RETRIEVE_STATUS = 0x99
    private const val FIELD_READ_STATUS = 0x9B
    private const val FIELD_REPLY_CHARGING = 0x9C
    private const val FIELD_REPLY_CHARGING_DEADLINE = 0x9D
    private const val FIELD_REPLY_CHARGING_ID = 0x9E
    private const val FIELD_REPLY_CHARGING_SIZE = 0x9F
    private const val FIELD_PREVIOUSLY_SENT_BY = 0xA0
    private const val FIELD_PREVIOUSLY_SENT_DATE = 0xA1
    private const val FIELD_STORED = 0xA7
    private const val FIELD_DISTRIBUTION_INDICATOR = 0xB1
    private const val FIELD_RECOMMENDED_RETRIEVAL_MODE = 0xB4
    private const val FIELD_APPLIC_ID = 0xB7
    private const val FIELD_REPLY_APPLIC_ID = 0xB8
    private const val FIELD_AUX_APPLIC_INFO = 0xB9
    private const val FIELD_CONTENT_CLASS = 0xBA
    private const val FIELD_DRM_CONTENT = 0xBB
    private const val FIELD_ADAPTATION_ALLOWED = 0xBC

    private const val END_OF_HEADER = 0x00

    // From-value tokens (OMA-MMS-ENC section 7.2.11).
    private const val TOKEN_ADDRESS_PRESENT = 0x80
    private const val TOKEN_INSERT_ADDRESS = 0x81

    // Value-length = Short-length | (Length-quote Uintvar-value); WAP-230-WSP.
    // Length-quote is octet 31 (0x1F), not 29 — a 0x1D is Short-length 29.
    private const val LENGTH_QUOTE = 0x1F
    private const val MAX_SHORT_LENGTH = 30
    private const val MAX_UINTVAR_OCTETS = 5
    private const val MAX_LONG_INTEGER_OCTETS = 8

    /**
     * Parses the MM headers of an inbound PDU. Returns null on malformed input — fail closed.
     *
     * When the payload is still WSP-wrapped (first octet < 0x80), the parser
     * looks for an X-Mms-Message-Type field and retries from there so a
     * GrapheneOS/OEM `data` extra that still includes WSP headers is not
     * dropped.
     */
    fun parse(pdu: ByteArray): MmsPduInfo? {
        parseAt(pdu, 0)?.let { return it }
        val first = u(pdu, 0) ?: return null
        if (first >= 0x80) return null
        var i = 1
        while (i < pdu.size - 1) {
            if ((pdu[i].toInt() and 0xFF) == FIELD_MESSAGE_TYPE) {
                parseAt(pdu, i)?.let { return it }
            }
            i++
        }
        return null
    }

    private fun parseAt(pdu: ByteArray, origin: Int): MmsPduInfo? {
        val first = u(pdu, origin) ?: return null
        var pos: Int
        val messageType: Int
        when {
            first == FIELD_MESSAGE_TYPE -> {
                val value = u(pdu, origin + 1) ?: return null
                if (value < 0x80) return null
                messageType = value
                pos = origin + 2
            }
            first >= 0x80 -> {
                messageType = first
                pos = origin + 1
            }
            else -> return null
        }

        var from: String? = null
        var dateMillis: Long? = null
        var contentType: String? = null
        var contentLocation: String? = null
        var transactionId: String? = null
        var messageId: String? = null

        while (pos < pdu.size) {
            val field = u(pdu, pos)
            pos++
            when (field) {
                END_OF_HEADER -> break
                FIELD_FROM -> {
                    val parsed = readFrom(pdu, pos) ?: return null
                    pos = parsed.second
                    if (from == null) from = parsed.first
                }
                FIELD_DATE -> {
                    val parsed = readLongInteger(pdu, pos) ?: return null
                    pos = parsed.second
                    if (dateMillis == null) {
                        val seconds = parsed.first
                        if (seconds < 0L || seconds > Long.MAX_VALUE / 1000L) return null
                        dateMillis = seconds * 1000L
                    }
                }
                FIELD_CONTENT_TYPE -> {
                    val parsed = readContentType(pdu, pos) ?: return null
                    pos = parsed.second
                    if (contentType == null) contentType = parsed.first
                }
                FIELD_BCC, FIELD_CC, FIELD_TO, FIELD_SUBJECT, FIELD_RESPONSE_TEXT, FIELD_RETRIEVE_TEXT ->
                    pos = skipEncodedStringValue(pdu, pos) ?: return null
                FIELD_CONTENT_LOCATION -> {
                    val parsed = readTextString(pdu, pos) ?: return null
                    pos = parsed.second
                    if (contentLocation == null) contentLocation = parsed.first
                }
                FIELD_TRANSACTION_ID -> {
                    val parsed = readTextString(pdu, pos) ?: return null
                    pos = parsed.second
                    if (transactionId == null) transactionId = parsed.first
                }
                FIELD_MESSAGE_ID -> {
                    val parsed = readTextString(pdu, pos) ?: return null
                    pos = parsed.second
                    if (messageId == null) messageId = parsed.first
                }
                FIELD_MESSAGE_CLASS ->
                    pos = skipMessageClass(pdu, pos) ?: return null
                FIELD_DELIVERY_REPORT, FIELD_MESSAGE_TYPE, FIELD_MMS_VERSION, FIELD_PRIORITY,
                FIELD_READ_REPORT, FIELD_REPORT_ALLOWED, FIELD_RESPONSE_STATUS,
                FIELD_SENDER_VISIBILITY, FIELD_STATUS, FIELD_RETRIEVE_STATUS,
                FIELD_READ_STATUS, FIELD_REPLY_CHARGING, FIELD_STORED,
                FIELD_DISTRIBUTION_INDICATOR, FIELD_RECOMMENDED_RETRIEVAL_MODE,
                FIELD_CONTENT_CLASS, FIELD_DRM_CONTENT, FIELD_ADAPTATION_ALLOWED -> {
                    if (u(pdu, pos) == null) return null
                    pos++
                }
                FIELD_DELIVERY_TIME, FIELD_EXPIRY, FIELD_REPLY_CHARGING_DEADLINE,
                FIELD_PREVIOUSLY_SENT_BY, FIELD_PREVIOUSLY_SENT_DATE ->
                    pos = skipValueLengthSpan(pdu, pos) ?: return null
                FIELD_MESSAGE_SIZE, FIELD_REPLY_CHARGING_SIZE ->
                    pos = skipLongInteger(pdu, pos) ?: return null
                FIELD_REPLY_CHARGING_ID, FIELD_APPLIC_ID, FIELD_REPLY_APPLIC_ID,
                FIELD_AUX_APPLIC_INFO -> {
                    val parsed = readTextString(pdu, pos) ?: return null
                    pos = parsed.second
                }
                else -> pos = skipUnknownValue(pdu, pos) ?: return null
            }
        }

        return MmsPduInfo(
            messageType,
            from,
            dateMillis,
            contentType,
            contentLocation,
            transactionId,
            messageId,
        )
    }

    /**
     * The HTTP URL [SmsManager.downloadMultimediaMessage] should GET.
     *
     * Verizon notification-ind PDUs send Content-Location as
     * `http://…/servlets/mms?message-id=` and put the token in
     * X-Mms-Transaction-Id (or Message-ID). GETting the bare prefix is rejected
     * immediately and the photo never lands.
     */
    fun retrieveUrl(info: MmsPduInfo): String? {
        val location = info.contentLocation?.trim().orEmpty()
        if (location.isEmpty()) return null
        if (!location.endsWith('=')) return location
        val token = info.transactionId?.trim().orEmpty()
            .ifEmpty { info.messageId?.trim().orEmpty() }
        return if (token.isNotEmpty()) location + token else location
    }

    /** Text-string: null-terminated printable US-ASCII. Empty yields null text without failing. */
    private fun readTextString(pdu: ByteArray, start: Int): Pair<String?, Int>? {
        val terminator = indexOfNul(pdu, start) ?: return null
        val decoded = decodeStrictUtf8(pdu, start, terminator) ?: return null
        val text = decoded.takeIf { it.isNotEmpty() && isPrintableAscii(it) }
            ?: return if (decoded.isEmpty()) Pair(null, terminator + 1) else null
        return Pair(text, terminator + 1)
    }

    /** Unsigned octet read, null past the end. */
    private fun u(pdu: ByteArray, index: Int): Int? =
        if (index in pdu.indices) pdu[index].toInt() and 0xFF else null

    /** Index of the terminating NUL at or after [start], null when unterminated. */
    private fun indexOfNul(pdu: ByteArray, start: Int): Int? {
        var i = start
        while (i < pdu.size) {
            if (pdu[i].toInt() == 0) return i
            i++
        }
        return null
    }

    private fun skipNullTerminated(pdu: ByteArray, start: Int): Int? =
        indexOfNul(pdu, start)?.plus(1)

    /**
     * Skips a bare Encoded-string-value (optional high-bit charset octet, then
     * null-terminated text). Returns the position past the terminator.
     */
    private fun skipEncodedStringValue(pdu: ByteArray, start: Int): Int? {
        var pos = start
        val lead = u(pdu, pos) ?: return null
        if (lead and 0x80 != 0) pos++
        return skipNullTerminated(pdu, pos)
    }

    /**
     * Decodes an Encoded-string-value inside [start, end): optional charset
     * octet (high bit set — discarded, text is decoded as UTF-8 either way),
     * then a null-terminated string. Null when the terminator or the region
     * boundary is missing or the text is not well-formed UTF-8.
     */
    private fun decodeEncodedString(pdu: ByteArray, start: Int, end: Int): String? {
        var pos = start
        val lead = u(pdu, pos) ?: return null
        if (lead and 0x80 != 0) pos++
        if (pos >= end || pos >= pdu.size) return null
        var terminator: Int? = null
        var i = pos
        while (i < end && i < pdu.size) {
            if (pdu[i].toInt() == 0) {
                terminator = i
                break
            }
            i++
        }
        val t = terminator ?: return null
        return decodeStrictUtf8(pdu, pos, t)
    }

    private fun decodeStrictUtf8(pdu: ByteArray, start: Int, end: Int): String? = try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(java.nio.ByteBuffer.wrap(pdu, start, end - start)).toString()
    } catch (_: Exception) {
        null
    }

    private fun isPrintableAscii(value: String): Boolean =
        value.all { it.code in 0x20..0x7E }

    /**
     * Reads a Value-length span (Short-length, or Length-quote + Uintvar).
     * Returns the inclusive start and exclusive end of the spanned value.
     */
    private fun valueLengthSpan(pdu: ByteArray, start: Int): Pair<Int, Int>? {
        val lead = u(pdu, start) ?: return null
        val length: Int
        var valueStart = start + 1
        if (lead == LENGTH_QUOTE) {
            val uintvar = readUintvar(pdu, valueStart) ?: return null
            length = uintvar.first
            valueStart = uintvar.second
        } else {
            if (lead > MAX_SHORT_LENGTH) return null
            length = lead
        }
        if (length < 0) return null
        val end = valueStart + length
        if (end > pdu.size) return null
        return Pair(valueStart, end)
    }

    private fun skipValueLengthSpan(pdu: ByteArray, start: Int): Int? =
        valueLengthSpan(pdu, start)?.second

    /** Uintvar-value: up to five 7-bit groups, continuation flagged by the high bit. */
    private fun readUintvar(pdu: ByteArray, start: Int): Pair<Int, Int>? {
        var value = 0
        var count = 0
        var pos = start
        while (true) {
            val octet = u(pdu, pos) ?: return null
            pos++
            count++
            if (count > MAX_UINTVAR_OCTETS) return null
            value = (value shl 7) or (octet and 0x7F)
            if (octet and 0x80 == 0) break
        }
        if (value < 0) return null
        return Pair(value, pos)
    }

    /**
     * FROM: Value-length span holding an Address-present-token (0x80) or
     * Insert-address-token (0x81) followed by an Encoded-string-value. A bare
     * Encoded-string-value without the token (common simplified encoding) is
     * accepted too. Returns the address verbatim paired with the position past
     * the field. An Insert-address-token legitimately carries no address and
     * yields null without failing the walk; anything else that does not decode
     * to a non-empty printable address (truncated string, bad UTF-8) fails
     * closed.
     */
    private fun readFrom(pdu: ByteArray, start: Int): Pair<String?, Int>? {
        val span = valueLengthSpan(pdu, start) ?: return null
        var textStart = span.first
        when (u(pdu, textStart)) {
            TOKEN_INSERT_ADDRESS -> return Pair(null, span.second)
            TOKEN_ADDRESS_PRESENT -> textStart++
        }
        val decoded = decodeEncodedString(pdu, textStart, span.second) ?: return null
        val address = decoded.takeIf { it.isNotEmpty() && isPrintableAscii(it) } ?: return null
        return Pair(address, span.second)
    }

    /**
     * Long-integer: length octet (1..8) then big-endian bytes. Returns the
     * value paired with the position past the field.
     */
    private fun readLongInteger(pdu: ByteArray, start: Int): Pair<Long, Int>? {
        val length = u(pdu, start) ?: return null
        if (length !in 1..MAX_LONG_INTEGER_OCTETS) return null
        var value = 0L
        for (offset in 1..length) {
            val octet = u(pdu, start + offset) ?: return null
            value = (value shl 8) or octet.toLong()
        }
        return Pair(value, start + 1 + length)
    }

    private fun skipLongInteger(pdu: ByteArray, start: Int): Int? =
        readLongInteger(pdu, start)?.second

    /**
     * MESSAGE-CLASS: Class-identifier (single octet 128..131) or Token-text
     * (extension class name up to the null terminator).
     */
    private fun skipMessageClass(pdu: ByteArray, start: Int): Int? {
        val lead = u(pdu, start) ?: return null
        return if (lead and 0x80 != 0) start + 1 else skipNullTerminated(pdu, start)
    }

    /**
     * CONTENT-TYPE. Constrained-media (well-known Short-integer, one octet),
     * Content-general-form (Value-length span — parameters stay inside the
     * span so they cannot desynchronise the header walk), or extension-media
     * (null-terminated printable US-ASCII). An empty extension-media string
     * yields null contentType without failing the walk.
     */
    private fun readContentType(pdu: ByteArray, start: Int): Pair<String?, Int>? {
        val lead = u(pdu, start) ?: return null
        if (lead and 0x80 != 0) {
            return Pair(wellKnownMedia(lead and 0x7F), start + 1)
        }
        if (lead <= MAX_SHORT_LENGTH || lead == LENGTH_QUOTE) {
            val span = valueLengthSpan(pdu, start) ?: return null
            return Pair(mediaTypeInSpan(pdu, span.first, span.second), span.second)
        }
        val terminator = indexOfNul(pdu, start) ?: return null
        val decoded = decodeStrictUtf8(pdu, start, terminator) ?: return null
        val mediaType = decoded.takeIf { it.isNotEmpty() && isPrintableAscii(it) }
            ?: return if (decoded.isEmpty()) Pair(null, terminator + 1) else null
        return Pair(mediaType, terminator + 1)
    }

    /** Well-known media codes as AOSP/MMS libraries number them (short-integer low 7 bits). */
    private fun wellKnownMedia(code: Int): String? = when (code) {
        0x03 -> "text/plain"
        0x1C -> "image/gif"
        0x1D -> "image/jpeg"
        0x1E -> "image/tiff"
        0x1F -> "image/png"
        0x20 -> "image/vnd.wap.wbmp"
        0x32, 0x33 -> "application/vnd.wap.multipart.related"
        0x3E -> "application/vnd.wap.mms-message"
        else -> null
    }

    private fun mediaTypeInSpan(pdu: ByteArray, start: Int, end: Int): String? {
        if (start >= end) return null
        val lead = u(pdu, start) ?: return null
        if (lead and 0x80 != 0) return wellKnownMedia(lead and 0x7F)
        val terminator = indexOfNul(pdu, start)?.takeIf { it <= end } ?: (end - 1)
        val decoded = decodeStrictUtf8(pdu, start, terminator) ?: return null
        return decoded.takeIf { it.isNotEmpty() && isPrintableAscii(it) }
    }

    /**
     * Skip a header value whose field name is not in the known subset.
     * WSP values are a Value-length span, a single high-bit octet, or a
     * null-terminated string — matching those shapes keeps From / Content-Location
     * reachable when a carrier adds MMS 1.3 or vendor fields.
     */
    private fun skipUnknownValue(pdu: ByteArray, start: Int): Int? {
        val lead = u(pdu, start) ?: return null
        return when {
            lead == LENGTH_QUOTE || lead <= MAX_SHORT_LENGTH -> skipValueLengthSpan(pdu, start)
            lead >= 0x80 -> start + 1
            else -> skipNullTerminated(pdu, start)
        }
    }
}
