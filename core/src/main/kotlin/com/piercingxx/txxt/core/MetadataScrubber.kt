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
 * malformed (fail-closed: the caller must NOT send — corrupt input never
 * leaves the device with metadata intact). Input in an unrecognized format is
 * returned unchanged: there is no known container to strip from it. Like the
 * scrubbers it delegates to, this is pure Kotlin with zero `android.*` imports
 * so it is JVM-testable without a device (docs/PRIVACY.md §4).
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
     * [VideoMetadataScrubber]). Input that is not a recognized format is
     * returned unchanged.
     *
     * This is the only entry point and it is unconditional: there is no way to
     * ask for the metadata to be preserved.
     */
    fun scrub(media: ByteArray): ByteArray? = when {
        isPng(media) -> PngMetadataScrubber.scrub(media)
        isJpeg(media) -> ImageMetadataScrubber.scrub(media)
        isMp4(media) -> VideoMetadataScrubber.scrub(media)
        else -> media
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
     * An MP4/MOV is a sequence of atoms; the first atom's 4-byte type must be a
     * printable code (e.g. `ftyp`). We accept any leading atom with a printable
     * type, matching the video scrubber's own recognition rule.
     */
    private fun isMp4(media: ByteArray): Boolean {
        if (media.size < 8) return false
        val type = String(media, 4, 4, Charsets.ISO_8859_1)
        return type.all { it.code in 0x20..0x7E }
    }
}
