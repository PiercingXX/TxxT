package com.piercingxx.txxt.core

/**
 * What a retrieved MMS (m-retrieve-conf) contains, for an SMS-only inbox that
 * still has to honour the default-handler retrieve contract.
 *
 * TxxT does not send photos and does not render a gallery. It **does** fetch
 * the PDU when the operator taps an inbound MMS, so holding `ROLE_SMS` does
 * not swallow carrier mail. The summary is text-first: a text part if one
 * exists, `[photo]` for image-only, `[MMS]` otherwise. Audio-only retrieve
 * is dropped (PRIVACY.md §5).
 */
data class MmsRetrievedContent(
    val body: String,
    val dropUnstored: Boolean,
    val imageBytes: ByteArray? = null,
    val imageMime: String? = null,
) {
    companion object {
        const val PHOTO_PLACEHOLDER = "[photo]"
        const val MMS_PLACEHOLDER = "[MMS]"
    }
}

/**
 * Best-effort inspect of a retrieved MM PDU. Fail-open to [MmsRetrievedContent.MMS_PLACEHOLDER]
 * when the multipart cannot be walked — the bytes were fetched, so the
 * operator still sees that something arrived rather than a blank row.
 *
 * Zero `android.*` imports; JVM-testable.
 */
object MmsRetrievedContentParser {

    fun parse(pdu: ByteArray): MmsRetrievedContent {
        val types = mediaTypesIn(pdu)
        val text = extractTextPlain(pdu)
        val audioOnly = types.any { it.startsWith("audio/") } &&
            types.none { it.startsWith("image/") || it.startsWith("text/") }
        if (audioOnly) {
            return MmsRetrievedContent(body = "", dropUnstored = true)
        }
        val image = extractImage(pdu)
        val body = when {
            !text.isNullOrBlank() -> text.trim()
            image != null || types.any { it.startsWith("image/") } ->
                MmsRetrievedContent.PHOTO_PLACEHOLDER
            else -> MmsRetrievedContent.MMS_PLACEHOLDER
        }
        return MmsRetrievedContent(
            body = body,
            dropUnstored = false,
            imageBytes = image?.second,
            imageMime = image?.first,
        )
    }

    /** JPEG SOI…EOI or PNG signature…IEND, if present in the PDU. */
    internal fun extractImage(pdu: ByteArray): Pair<String, ByteArray>? {
        val jpeg = indexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (jpeg >= 0) {
            val eoi = lastIndexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            if (eoi > jpeg) {
                return "image/jpeg" to pdu.copyOfRange(jpeg, eoi + 2)
            }
        }
        val pngSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val png = indexOf(pdu, pngSig)
        if (png >= 0) {
            val iend = lastIndexOf(pdu, "IEND".toByteArray(Charsets.ISO_8859_1))
            if (iend > png) {
                return "image/png" to pdu.copyOfRange(png, (iend + 8).coerceAtMost(pdu.size))
            }
        }
        return null
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in (haystack.size - needle.size) downTo 0) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Collects ASCII media-type tokens (`type/subtype`) visible in the PDU.
     * Notification-ind and retrieve-conf both carry these as Text-string or
     * extension-media values.
     */
    internal fun mediaTypesIn(pdu: ByteArray): List<String> {
        val ascii = pdu.toString(Charsets.ISO_8859_1)
        val found = mutableListOf<String>()
        val regex = Regex("""(text|image|audio|video|application)/[A-Za-z0-9.+\-]+""")
        regex.findAll(ascii).forEach { found += it.value.lowercase() }
        return found.distinct()
    }

    /**
     * Pulls a `text/plain` payload when the part is stored as a NUL-terminated
     * or length-bounded US-ASCII/UTF-8 run after the type token. Returns null
     * when no usable text part is found.
     */
    internal fun extractTextPlain(pdu: ByteArray): String? {
        val marker = "text/plain"
        val ascii = pdu.toString(Charsets.ISO_8859_1)
        val index = ascii.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        var pos = index + marker.length
        // Skip WSP parameters / NULs that sit between the type and the body.
        while (pos < ascii.length && (ascii[pos] == '\u0000' || ascii[pos] == ' ' || ascii[pos] == ';')) {
            if (ascii[pos] == ';') {
                val nul = ascii.indexOf('\u0000', pos)
                pos = if (nul < 0) pos + 1 else nul + 1
            } else {
                pos++
            }
        }
        if (pos >= ascii.length) return null
        val endNul = ascii.indexOf('\u0000', pos)
        val end = if (endNul < 0) ascii.length else endNul
        val slice = ascii.substring(pos, end)
        val printable = slice.takeWhile { it.code in 0x09..0x7E }
            .trim()
            .takeIf { it.isNotEmpty() && it.any { ch -> ch.isLetterOrDigit() } }
        return printable
    }
}
