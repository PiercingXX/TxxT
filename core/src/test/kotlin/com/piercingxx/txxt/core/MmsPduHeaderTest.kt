package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Byte-fixture tests for [MmsPduHeader]. Every PDU below is hand-assembled per
 * OMA-MMS-ENC v1.2 (section 7.2 value ABNF, section 7.3 Table 21 field-name
 * assignments encoded as Short-integer, i.e. assigned number | 0x80).
 */
class MmsPduHeaderTest {

    // ---- encoding helpers -------------------------------------------------

    private fun bytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    private fun text(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII) + bytes(0x00)

    /** Encoded-string-value: optional charset octet (MIBenum | 0x80) + null-terminated text. */
    private fun encString(s: String, charsetMib: Int? = null): ByteArray {
        val head = if (charsetMib != null) bytes(charsetMib or 0x80) else ByteArray(0)
        return head + text(s)
    }

    /** Value-length (Short-length form; payload must fit in 30 octets) prepended to payload. */
    private fun vl(payload: ByteArray): ByteArray {
        assertTrue(payload.size <= 30)
        return bytes(payload.size) + payload
    }

    /** Value-length in Length-quote + Uintvar form. */
    private fun vlQuoted(payload: ByteArray): ByteArray =
        bytes(0x1D) + uintvar(payload.size) + payload

    private fun uintvar(value: Int): ByteArray {
        assertTrue(value >= 0)
        if (value == 0) return bytes(0x00)
        val groups = ArrayList<Int>()
        var v = value
        while (v != 0) {
            groups.add(v and 0x7F)
            v = v ushr 7
        }
        // groups are least-significant first; wire order is most-significant
        // first, every octet except the last carries the continuation bit.
        val out = ByteArray(groups.size)
        for (i in groups.indices) {
            val flag = if (i == groups.size - 1) 0 else 0x80
            out[i] = (groups[groups.size - 1 - i] or flag).toByte()
        }
        return out
    }

    /** Long-integer: length octet then big-endian payload of exactly [size] octets. */
    private fun longInt(value: Long, size: Int = (64 - java.lang.Long.numberOfLeadingZeros(value) / 8).coerceIn(1, 8)): ByteArray {
        val out = ByteArray(1 + size)
        out[0] = size.toByte()
        for (i in 0 until size) {
            out[1 + i] = (value ushr (8 * (size - 1 - i))).toByte()
        }
        return out
    }

    private val FROM_ADDR = "+15551234567/TYPE=PLMN"
    private val CT_MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    private val DATE_SECS = 1735689600L // 2025-01-01T00:00:00Z

    private fun fromField(charsetMib: Int? = 106 /* utf-8 */, address: String = FROM_ADDR): ByteArray =
        bytes(0x89) + vl(encString(address, charsetMib))

    private fun dateField(): ByteArray = bytes(0x85) + longInt(DATE_SECS, 4)

    private fun contentTypeExtensionMedia(): ByteArray =
        bytes(0x84) + text(CT_MULTIPART_RELATED)

    /** Task-canonical retrieve-conf: message-type value octet first, end-of-header last. */
    private fun retrieveConf(vararg middleFields: ByteArray): ByteArray {
        var pdu = bytes(0x84)
        pdu += fromField()
        pdu += dateField()
        for (f in middleFields) pdu += f
        pdu += contentTypeExtensionMedia()
        pdu += bytes(0x00)
        return pdu
    }

    // ---- positive extractions ----------------------------------------------

    @Test
    fun `retrieve conf extracts all four fields`() {
        val info = MmsPduHeader.parse(retrieveConf())
        assertNotNull(info)
        assertEquals(0x84, info!!.messageType)
        assertEquals(FROM_ADDR, info.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
        assertEquals(CT_MULTIPART_RELATED, info.contentType)
    }

    @Test
    fun `notification ind minimal fixture parses transaction id and from`() {
        val pdu = bytes(0x82) +
            bytes(0x98) + text("Txn000123") + // X-Mms-Transaction-Id: Text-string
            fromField() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals(0x82, info!!.messageType)
        assertEquals(FROM_ADDR, info.from)
        assertEquals("Txn000123", info.transactionId)
        assertNull(info.dateMillis)
        assertNull(info.contentType)
    }

    @Test
    fun `notification ind extracts content location`() {
        val pdu = bytes(0x82) +
            bytes(0x98) + text("Txn1") +
            fromField() +
            bytes(0x83) + text("http://mmsc.example/m/1") +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals("http://mmsc.example/m/1", info!!.contentLocation)
        assertEquals("Txn1", info.transactionId)
        assertEquals("http://mmsc.example/m/1", MmsPduHeader.retrieveUrl(info))
    }

    @Test
    fun `verizon content location appends the transaction id after message-id=`() {
        val pdu = bytes(0x82) +
            bytes(0x98) + text("A1B2C3D4") +
            fromField() +
            bytes(0x83) + text("http://63.59.140.82/servlets/mms?message-id=") +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals("http://63.59.140.82/servlets/mms?message-id=", info!!.contentLocation)
        assertEquals("A1B2C3D4", info.transactionId)
        assertEquals(
            "http://63.59.140.82/servlets/mms?message-id=A1B2C3D4",
            MmsPduHeader.retrieveUrl(info),
        )
    }

    @Test
    fun `verizon content location falls back to message-id when transaction id is absent`() {
        val pdu = bytes(0x82) +
            fromField() +
            bytes(0x83) + text("http://63.59.140.82/servlets/mms?message-id=") +
            bytes(0x8B) + text("MSGTOKEN") +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals("MSGTOKEN", info!!.messageId)
        assertEquals(
            "http://63.59.140.82/servlets/mms?message-id=MSGTOKEN",
            MmsPduHeader.retrieveUrl(info),
        )
    }

    @Test
    fun `charset octet absent variant still decodes address`() {
        val pdu = bytes(0x84) +
            bytes(0x89) + vl(encString(FROM_ADDR)) +
            dateField() +
            contentTypeExtensionMedia() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals(FROM_ADDR, info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
        assertEquals(CT_MULTIPART_RELATED, info.contentType)
    }

    @Test
    fun `full wsp framing with leading message-type field name is accepted`() {
        val pdu = bytes(0x8C, 0x82) + // X-Mms-Message-Type field name + m-notification-ind value
            bytes(0x98) + text("Txn000123") +
            fromField() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals(0x82, info!!.messageType)
        assertEquals(FROM_ADDR, info.from)
    }

    @Test
    fun `address present token inside from span is consumed`() {
        val pdu = bytes(0x84) +
            bytes(0x89) + vl(bytes(0x80) + encString(FROM_ADDR, 106)) +
            dateField() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals(FROM_ADDR, info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
    }

    @Test
    fun `insert address token yields null from but keeps parsing`() {
        val pdu = bytes(0x84) +
            bytes(0x89) + vl(bytes(0x81)) + // Insert-address-token only
            dateField() +
            contentTypeExtensionMedia() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertNull(info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
        assertEquals(CT_MULTIPART_RELATED, info.contentType)
    }

    @Test
    fun `value length in length quote uintvar form is supported`() {
        val pdu = bytes(0x84) +
            bytes(0x89) + vlQuoted(encString(FROM_ADDR, 106)) +
            dateField() +
            bytes(0x00)
        val info = MmsPduHeader.parse(pdu)
        assertNotNull(info)
        assertEquals(FROM_ADDR, info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
    }

    // ---- skip-over of known bounded fields ----------------------------------

    @Test
    fun `token text message class between from and date is skipped`() {
        val info = MmsPduHeader.parse(retrieveConf(bytes(0x8A) + text("personal")))
        assertNotNull(info)
        assertEquals(FROM_ADDR, info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
        assertEquals(CT_MULTIPART_RELATED, info.contentType)
    }

    @Test
    fun `class identifier message class is skipped as one octet`() {
        val info = MmsPduHeader.parse(retrieveConf(bytes(0x8A, 0x81))) // Advertisement = <Octet 129>
        assertNotNull(info)
        assertEquals(FROM_ADDR, info!!.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
    }

    @Test
    fun `common single octet and string fields are skipped safely`() {
        val extras = arrayOf(
            bytes(0x8B) + text("msg-id-42"), // Message-ID: Text-string
            bytes(0x96) + encString("hi there", 106), // Subject: bare Encoded-string-value
            bytes(0x97) + encString("+15559998888/TYPE=PLMN"), // To: bare Encoded-string-value
            bytes(0x8D, 0x12), // MMS-Version 1.2 (single octet)
            bytes(0x8F, 0x81), // Priority (single octet)
            bytes(0x90, 0x81), // Read-Report (single octet)
            bytes(0x8E) + longInt(30_000L), // Message-Size: Long-integer
            bytes(0x88) + vl(bytes(0x80) + longInt(DATE_SECS, 4)) // Expiry absolute
        )
        val info = MmsPduHeader.parse(retrieveConf(*extras))
        assertNotNull(info)
        assertEquals(0x84, info!!.messageType)
        assertEquals(FROM_ADDR, info.from)
        assertEquals(DATE_SECS * 1000L, info.dateMillis)
        assertEquals(CT_MULTIPART_RELATED, info.contentType)
    }

    // ---- fail closed --------------------------------------------------------

    @Test
    fun `empty array fails closed`() {
        assertNull(MmsPduHeader.parse(ByteArray(0)))
    }

    @Test
    fun `first octet below 0x80 fails closed`() {
        assertNull(MmsPduHeader.parse(bytes(0x01)))
        assertNull(MmsPduHeader.parse(bytes(0x79, 0x89, 0x02, 0xEA, 0x41, 0x00)))
    }

    @Test
    fun `truncated from string without terminator fails closed`() {
        // Span claims 6 octets: charset + "AB" + ... but no NUL ever arrives.
        val pdu = bytes(0x82, 0x89, 0x06, 0xEA, 0x41, 0x42, 0x43)
        assertNull(MmsPduHeader.parse(pdu))
    }

    @Test
    fun `date long integer length zero or nine or overrunning fails closed`() {
        val base = bytes(0x84, 0x89, 0x03, 0xEA, 0x41, 0x00)
        assertNull(MmsPduHeader.parse(base + bytes(0x85, 0x00) + bytes(0x00))) // len 0
        assertNull(MmsPduHeader.parse(base + bytes(0x85, 0x09) + ByteArray(9) + bytes(0x00))) // len 9
        assertNull(MmsPduHeader.parse(base + bytes(0x85, 0x04, 0x11))) // declared 4, truncated
    }

    @Test
    fun `value length overrunning array fails closed`() {
        assertNull(MmsPduHeader.parse(bytes(0x82, 0x89, 0x1E, 0xEA, 0x41, 0x42))) // claims 30, has 2
        assertNull(
            MmsPduHeader.parse(bytes(0x82, 0x89, 0x1D, 0xFF, 0x01)) // quote + huge uintvar
        )
    }

    @Test
    fun `truncated trailing field id fails closed`() {
        assertNull(MmsPduHeader.parse(retrieveConf().dropLast(2).toByteArray())) // cut end-of-header + ct NUL
        assertNull(MmsPduHeader.parse(bytes(0x82, 0x8D))) // version field with no value octet
    }

    @Test
    fun `unknown field name fails closed`() {
        val unknown = bytes(0xA7, 0x01) // X-Mms-Stored wire id — not in this parser's subset
        assertNull(MmsPduHeader.parse(retrieveConf(unknown)))
    }

    @Test
    fun `well known media content type code form fails closed`() {
        // Content-Type given as well-known-media Short-integer (multipart.related code),
        // optionally followed by unbounded WSP parameters — cannot be skipped minimally.
        val pdu = bytes(0x84) + fromField() + dateField() + bytes(0x84, 0xB3) + bytes(0x00)
        assertNull(MmsPduHeader.parse(pdu))
    }

    @Test
    fun `unterminated content type extension media fails closed`() {
        val pdu = bytes(0x84) + fromField() + dateField() +
            bytes(0x84) + CT_MULTIPART_RELATED.toByteArray(Charsets.US_ASCII) // no NUL
        assertNull(MmsPduHeader.parse(pdu))
    }

    @Test
    fun `non utf8 from text fails closed`() {
        // Invalid UTF-8 continuation byte where the address should be.
        val pdu = bytes(0x82) + bytes(0x98) + text("Txn") +
            bytes(0x89, 0x04, 0xEA, 0xC3, 0x28, 0x00) +
            bytes(0x00)
        assertNull(MmsPduHeader.parse(pdu))
    }

    // ---- fuzz-ish robustness -------------------------------------------------

    @Test
    fun `pseudo random arrays never throw and only yield null or valid info`() {
        val rnd = Random(20260823L)
        repeat(500) { iteration ->
            val size = rnd.nextInt(65)
            val pdu = ByteArray(size)
            when (iteration % 4) {
                0 -> for (i in 0 until size) pdu[i] = rnd.nextInt(256).toByte()
                1 -> for (i in 0 until size) pdu[i] = (0x80 or rnd.nextInt(128)).toByte() // high-bit bias
                2 -> { // plausible prefix then garbage tail
                    val prefix = retrieveConf()
                    val keep = minOf(size, prefix.size)
                    System.arraycopy(prefix, 0, pdu, 0, keep)
                    for (i in keep until size) pdu[i] = rnd.nextInt(256).toByte()
                }
                else -> rnd.nextBytes(pdu)
            }
            val info = MmsPduHeader.parse(pdu) // must not throw
            if (info != null) {
                val messageType = info.messageType
                val dateMillis = info.dateMillis
                val contentType = info.contentType
                val from = info.from
                assertTrue(messageType == null || messageType >= 0x80)
                assertTrue(dateMillis == null || dateMillis % 1000L == 0L)
                assertTrue(contentType == null || contentType.all { it.code in 0x20..0x7E })
                assertTrue(from == null || from.all { it.code in 0x20..0x7E })
            }
        }
    }

    @Test
    fun `corrupted copy of a valid pdu never throws and stays shape-valid`() {
        val valid = retrieveConf()
        val rnd = Random(42L)
        repeat(300) {
            val corrupted = valid.copyOf()
            repeat(rnd.nextInt(4) + 1) {
                corrupted[rnd.nextInt(corrupted.size)] = rnd.nextInt(256).toByte()
            }
            val info = MmsPduHeader.parse(corrupted) // must not throw
            if (info != null) {
                val messageType = info.messageType
                val dateMillis = info.dateMillis
                assertTrue(messageType != null && messageType >= 0x80)
                assertTrue(dateMillis == null || dateMillis % 1000L == 0L)
            }
        }
    }
}
