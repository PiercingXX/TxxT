package com.piercingxx.txxt.core

/**
 * The single, unconditional entry point for stripping privacy metadata from
 * media before it leaves the device.
 *
 * This object exists to make the "no send-with-metadata toggle" guarantee
 * ([docs/PRIVACY.md:82]) structural rather than a matter of convention: the
 * only public surface here is [scrub], which always strips the metadata
 * containers from the given bytes. There is deliberately no `keepMetadata`,
 * no `preserveMetadata`, and no boolean or overload that skips the scrub —
 * a caller cannot ask for a metadata-preserving path because none exists.
 *
 * [scrub] inspects the leading bytes to dispatch to the PNG scrubber
 * ([PngMetadataScrubber]), the image scrubber ([ImageMetadataScrubber]) for
 * JPEG input, or the video scrubber ([VideoMetadataScrubber]) for MP4/MOV
 * input. It returns `null` when the format is recognized but its structure is
 * malformed, **and** when the format is a still image we cannot strip
 * (HEIF/HEIC, WebP, GIF): fail-closed, the caller must NOT send — there is no
 * guarantee the metadata is gone. Like the scrubbers it delegates to, this is
 * pure Kotlin with zero `android.*` imports so it is JVM-testable without a
 * device (docs/PRIVACY.md §4).
 */
object MetadataScrubber {

    /**
     * Returns a copy of [media] with privacy metadata containers stripped, or
     * `null` when [media] is a recognized format whose structure cannot be
     * parsed.
     *
     * JPEG input has its EXIF/XMP/IPTC/comment segments removed; PNG input has
     * its EXIF/text chunks removed; MP4/MOV input has its GPS/device/creation-
     * time/encoder atoms removed. The media bytes themselves are never
     * re-encoded — only the metadata containers are dropped (see
     * [ImageMetadataScrubber], [PngMetadataScrubber] and
     * [VideoMetadataScrubber]). A still-image format we cannot strip (HEIF,
     * WebP, GIF) returns `null` so it never leaves with metadata intact.
     *
     * This is the only entry point and it is unconditional: there is no way to
     * ask for the metadata to be preserved.
     */
    fun scrub(media: ByteArray): ByteArray? = when {
        isPng(media) -> PngMetadataScrubber.scrub(media)
        isJpeg(media) -> ImageMetadataScrubber.scrub(media)
        isHeif(media) || isWebp(media) || isGif(media) -> null
        isMp4(media) -> VideoMetadataScrubber.scrub(media)
        else -> null
    }

    /** A PNG begins with the 8-byte signature 0x89 'P' 'N' 'G' CR LF SUB LF. */
    private fun isPng(media: ByteArray): Boolean =
        media.size >= 8 &&
            (media[0].toInt() and 0xFF) == 0x89 &&
            media[1] == 'P'.code.toByte() &&
            media[2] == 'N'.code.toByte() &&
            media[3] == 'G'.code.toByte() &&
            media[4] == 0x0D.toByte() &&
            media[5] == 0x0A.toByte() &&
            media[6] == 0x1A.toByte() &&
            media[7] == 0x0A.toByte()

    /** A JPEG begins with the SOI marker 0xFF 0xD8. */
    private fun isJpeg(media: ByteArray): Boolean =
        media.size >= 2 &&
            (media[0].toInt() and 0xFF) == 0xFF &&
            (media[1].toInt() and 0xFF) == 0xD8

    /**
     * An MP4/MOV begins with an `ftyp` atom. Anything else with a printable
     * 4-byte type (a JPEG APP segment, a random GIF, …) is not video.
     */
    private fun isMp4(media: ByteArray): Boolean {
        if (media.size < 8) return false
        return String(media, 4, 4, Charsets.ISO_8859_1) == "ftyp" && !isHeif(media)
    }

    /** HEIF/HEIC/AVIF: ISO-BMFF with a HEIF-family `ftyp` brand. */
    private fun isHeif(media: ByteArray): Boolean {
        if (media.size < 12) return false
        if (String(media, 4, 4, Charsets.ISO_8859_1) != "ftyp") return false
        val brand = String(media, 8, 4, Charsets.ISO_8859_1).lowercase()
        return brand in HEIF_BRANDS
    }

    /** WebP: `RIFF....WEBP`. */
    private fun isWebp(media: ByteArray): Boolean =
        media.size >= 12 &&
            media[0] == 'R'.code.toByte() &&
            media[1] == 'I'.code.toByte() &&
            media[2] == 'F'.code.toByte() &&
            media[3] == 'F'.code.toByte() &&
            media[8] == 'W'.code.toByte() &&
            media[9] == 'E'.code.toByte() &&
            media[10] == 'B'.code.toByte() &&
            media[11] == 'P'.code.toByte()

    /** GIF87a / GIF89a. */
    private fun isGif(media: ByteArray): Boolean =
        media.size >= 6 &&
            media[0] == 'G'.code.toByte() &&
            media[1] == 'I'.code.toByte() &&
            media[2] == 'F'.code.toByte() &&
            media[3] == '8'.code.toByte() &&
            (media[4] == '7'.code.toByte() || media[4] == '9'.code.toByte()) &&
            media[5] == 'a'.code.toByte()

    private val HEIF_BRANDS = setOf(
        "heic", "heif", "heix", "hevc", "hevx", "mif1", "msf1", "avif",
    )
}
