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
        val parts = multipartParts(pdu)
        val types = mediaTypesIn(pdu) + parts.mapNotNull { it.mime }
        val text = parts.firstNotNullOfOrNull { part ->
            if (part.mime?.startsWith("text/") == true) usableCaption(part.data.decodeToString())
            else null
        } ?: extractTextPlain(pdu)
        val audioOnly = types.any { it.startsWith("audio/") } &&
            types.none { it.startsWith("image/") || it.startsWith("text/") }
        if (audioOnly) {
            return MmsRetrievedContent(body = "", dropUnstored = true)
        }
        val image = parts.firstNotNullOfOrNull { imageFromPart(it) }
            ?: extractImage(pdu)
            ?: extractHeif(pdu)
            ?: parts.firstNotNullOfOrNull { videoFromPart(it) }
        val body = when {
            !text.isNullOrBlank() -> text.trim()
            image != null ||
                types.any { it.startsWith("image/") || it.startsWith("video/") } ->
                MmsRetrievedContent.PHOTO_PLACEHOLDER
            else -> ""
        }
        return MmsRetrievedContent(
            body = body,
            dropUnstored = false,
            imageBytes = image?.second,
            imageMime = image?.first,
        )
    }

    /**
     * WSP multipart parts after the MM headers. Android MMS stores the JPEG
     * here with a well-known Content-Type octet — no ASCII `image/jpeg`.
     */
    internal fun multipartParts(pdu: ByteArray): List<MmsPart> {
        val start = MmsPduHeader.parse(pdu)?.headerEnd ?: 0
        if (start !in 0 until pdu.size) return emptyList()
        val count = MmsPduHeader.uintvarAt(pdu, start) ?: return emptyList()
        if (count.first !in 1..40) return emptyList()
        var pos = count.second
        val parts = ArrayList<MmsPart>(count.first)
        repeat(count.first) {
            val headersLen = MmsPduHeader.uintvarAt(pdu, pos) ?: return parts
            pos = headersLen.second
            val dataLen = MmsPduHeader.uintvarAt(pdu, pos) ?: return parts
            pos = dataLen.second
            if (headersLen.first < 0 || dataLen.first < 0) return parts
            if (pos + headersLen.first + dataLen.first > pdu.size) return parts
            val headers = pdu.copyOfRange(pos, pos + headersLen.first)
            pos += headersLen.first
            val data = pdu.copyOfRange(pos, pos + dataLen.first)
            pos += dataLen.first
            parts += MmsPart(contentTypeFromPartHeaders(headers), data)
        }
        return parts
    }

    private fun contentTypeFromPartHeaders(headers: ByteArray): String? {
        if (headers.isEmpty()) return null
        val lead = headers[0].toInt() and 0xFF
        if (lead and 0x80 != 0) return MmsPduHeader.wellKnownMediaType(lead and 0x7F)
        val nul = headers.indexOf(0)
        val end = if (nul < 0) headers.size else nul
        val text = headers.copyOfRange(0, end).toString(Charsets.US_ASCII).trim()
        return text.lowercase().takeIf { it.contains('/') }
    }

    private fun imageFromPart(part: MmsPart): Pair<String, ByteArray>? {
        val mime = part.mime
        if (mime != null && mime.startsWith("image/")) {
            extractImage(part.data)?.let { return it }
            extractHeif(part.data)?.let { return it }
            if (part.data.size >= 16) return mime to part.data
        }
        return extractImage(part.data) ?: extractHeif(part.data)
    }

    private fun videoFromPart(part: MmsPart): Pair<String, ByteArray>? {
        val mime = part.mime ?: return null
        if (!mime.startsWith("video/") || part.data.size < 16) return null
        return mime to part.data
    }

    internal data class MmsPart(val mime: String?, val data: ByteArray)

    /** JPEG / PNG / GIF / WebP / BMP payload, if present in the PDU. */
    internal fun extractImage(pdu: ByteArray): Pair<String, ByteArray>? {
        val jpeg = indexOfJpegSoi(pdu)
        if (jpeg >= 0) {
            val eoi = lastIndexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            val end = if (eoi > jpeg) eoi + 2 else pdu.size
            if (end > jpeg + 2) {
                return "image/jpeg" to pdu.copyOfRange(jpeg, end)
            }
        }
        if (pdu.size >= 2 && pdu[0] == 'B'.code.toByte() && pdu[1] == 'M'.code.toByte()) {
            return "image/bmp" to pdu
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

    /**
     * iPhone photos often arrive as HEIC/HEIF (ISO BMFF `ftyp` + brand),
     * which has no JPEG SOI. Carriers do not always transcode.
     */
    internal fun extractHeif(pdu: ByteArray): Pair<String, ByteArray>? {
        val ftyp = indexOf(pdu, "ftyp".toByteArray(Charsets.ISO_8859_1))
        if (ftyp < 4) return null
        val brandAt = ftyp + 4
        if (brandStart(brandAt, pdu) == null) return null
        val boxStart = ftyp - 4
        if (boxStart < 0) return null
        return "image/heic" to pdu.copyOfRange(boxStart, pdu.size)
    }

    private fun brandStart(brandAt: Int, pdu: ByteArray): String? {
        if (brandAt + 4 > pdu.size) return null
        val brand = pdu.copyOfRange(brandAt, brandAt + 4).toString(Charsets.ISO_8859_1).lowercase()
        return brand.takeIf { it in HEIF_BRANDS }
    }

    private val HEIF_BRANDS = setOf("heic", "heif", "heix", "mif1", "msf1")

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
        val printable = slice.takeWhile { it.code in 0x09..0x7E }.trim()
        return usableCaption(printable)
    }

    /**
     * A caption the operator should see. WSP parameters (`charset=utf-8`)
     * and SMIL must not replace the photo.
     */
    internal fun usableCaption(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        if ('<' in t || '=' in t || '/' in t) return null
        return t.takeIf { it.any { ch -> ch.isLetterOrDigit() } }
    }

    /** Prefer `FF D8 FF` (a real JPEG marker); fall back to a bare SOI. */
    private fun indexOfJpegSoi(pdu: ByteArray): Int {
        val marked = indexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        if (marked >= 0) return marked
        return indexOf(pdu, byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
    }
}
