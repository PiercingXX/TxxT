package com.piercingxx.txxt.core

/**
 * What a retrieved MMS (m-retrieve-conf) contains, for an SMS-only inbox that
 * still has to honour the default-handler retrieve contract.
 *
 * Inbound photos are fetched on arrival. The thread shows `[Photo]` until the
 * operator taps the row; then the image itself is shown. Audio-only retrieve
 * is dropped (PRIVACY.md §5). Anything that cannot prove text or an image is
 * dropped unstored — never a fake `[MMS]` line.
 */
data class MmsRetrievedContent(
    val body: String,
    val dropUnstored: Boolean,
    val imageBytes: ByteArray? = null,
    val imageMime: String? = null,
) {
    companion object {
        /** Revealed photo-only marker after a tap (inbound and outgoing). */
        const val PHOTO_PLACEHOLDER = "[photo]"

        /** Fetched photo still showing the marker; a tap reveals the image. */
        const val COLLAPSED_PHOTO_PLACEHOLDER = "[Photo]"

        const val MMS_PLACEHOLDER = "[MMS]"
    }
}

/**
 * Best-effort inspect of a retrieved MM PDU. Unknown / empty content is
 * dropped unstored rather than invented as `[MMS]`.
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
            else -> ""
        }
        val dropUnstored = body.isEmpty()
        return MmsRetrievedContent(
            body = body,
            dropUnstored = dropUnstored,
            imageBytes = if (dropUnstored) null else image?.second,
            imageMime = if (dropUnstored) null else image?.first,
        )
    }

    /** JPEG / PNG / GIF / WebP payload, if present in the PDU. */
    internal fun extractImage(pdu: ByteArray): Pair<String, ByteArray>? {
        val jpeg = indexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (jpeg >= 0) {
            val eoi = lastIndexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            val end = if (eoi > jpeg) eoi + 2 else pdu.size
            if (end > jpeg + 2) {
                return "image/jpeg" to pdu.copyOfRange(jpeg, end)
            }
        }
        val pngSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val png = indexOf(pdu, pngSig)
        if (png >= 0) {
            val iend = lastIndexOf(pdu, "IEND".toByteArray(Charsets.ISO_8859_1))
            val end = if (iend > png) (iend + 8).coerceAtMost(pdu.size) else pdu.size
            if (end > png) {
                return "image/png" to pdu.copyOfRange(png, end)
            }
        }
        val gif89 = indexOf(pdu, "GIF89a".toByteArray(Charsets.ISO_8859_1))
        val gif87 = indexOf(pdu, "GIF87a".toByteArray(Charsets.ISO_8859_1))
        val gif = when {
            gif89 >= 0 && gif87 >= 0 -> minOf(gif89, gif87)
            gif89 >= 0 -> gif89
            else -> gif87
        }
        if (gif >= 0) {
            return "image/gif" to pdu.copyOfRange(gif, pdu.size)
        }
        val riff = indexOf(pdu, "RIFF".toByteArray(Charsets.ISO_8859_1))
        if (riff >= 0 && riff + 12 <= pdu.size) {
            val tag = pdu.copyOfRange(riff + 8, riff + 12)
            if (tag.contentEquals("WEBP".toByteArray(Charsets.ISO_8859_1))) {
                return "image/webp" to pdu.copyOfRange(riff, pdu.size)
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
