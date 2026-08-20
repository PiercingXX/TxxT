package com.piercingxx.txxt.core

/**
 * Strips metadata-bearing APPn segments from a JPEG byte stream.
 *
 * A JPEG is a sequence of markers (0xFF xx) and their payloads. Most metadata
 * lives in APP1 (EXIF and XMP) and APP13 (IPTC/Photoshop) segments. This
 * scrubber walks the marker stream, drops those segments, and copies everything
 * else byte-for-byte — including the scan data (SOF..EOI), so no re-encode
 * happens. Non-JPEG input is returned unchanged.
 *
 * Pure-Kotlin with zero `android.*` imports so the scrub logic is JVM-testable
 * without a device (docs/PRIVACY.md §4).
 */
object ImageMetadataScrubber {

    /**
     * APPn markers that carry the metadata the goal names.
     * - APP1 (0xE1): EXIF and XMP.
     * - APP13 (0xED): IPTC / Photoshop.
     * APP0 (0xE0, JFIF) and APP2 (0xE2, ICC profile) are display/colour data,
     * not privacy metadata, and are preserved.
     */
    private val METADATA_APP_MARKERS = setOf(0xE1.toByte(), 0xED.toByte())

    private const val MARKER = 0xFF
    private const val SOS = 0xDA // start of scan: payload has no length field
    private const val EOI = 0xD9 // end of image

    /**
     * Returns a copy of [jpeg] with EXIF/XMP/IPTC segments removed. The media
     * scan data is byte-identical to the input. Input that is not a JPEG (does
     * not start with a JPEG SOI marker) is returned unchanged.
     */
    fun scrub(jpeg: ByteArray): ByteArray {
        if (!startsWithSof(jpeg)) return jpeg

        val out = java.io.ByteArrayOutputStream(jpeg.size)
        var i = 0
        var inScan = false
        while (i < jpeg.size) {
            if (!inScan) {
                // Outside the scan every byte is a marker (0xFF xx). If we see
                // anything else the stream is malformed and we bail out with the
                // input unchanged rather than risk corrupting the image.
                if (jpeg[i].toInt() and 0xFF != MARKER) return jpeg
            }

            // Every marker is two bytes; a truncated stream (no room for the
            // second byte) is left unchanged.
            if (i + 1 >= jpeg.size) return jpeg

            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == SOS) {
                // Copy the SOS marker and enter the raw scan. The scan payload
                // is entropy-coded data (with 0xFF00 byte-stuffing) terminated
                // only by the EOI marker, so copy it verbatim.
                out.write(jpeg, i, 2)
                i += 2
                inScan = true
                continue
            }
            if (marker == EOI) {
                out.write(jpeg, i, jpeg.size - i)
                break
            }

            if (inScan) {
                // Inside the scan, 0xFF bytes are byte-stuffed (0xFF00) or start
                // a restart marker (FFD0..FFD7); the only real terminator is EOI,
                // handled above. Copy the scan byte-by-byte so a lone 0xFF that
                // begins EOI is never consumed as part of a data pair.
                out.write(jpeg[i].toInt())
                i += 1
                continue
            }

            // Standalone markers (RSTn, SOI) carry no payload and no length.
            if (isStandalone(marker)) {
                out.write(jpeg, i, 2)
                i += 2
                continue
            }

            // All other markers are length-prefixed: two length bytes that
            // include themselves but not the marker bytes.
            if (i + 4 > jpeg.size) return jpeg
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val segmentEnd = i + 2 + length
            if (segmentEnd > jpeg.size) return jpeg

            if (marker.toByte() in METADATA_APP_MARKERS) {
                // Drop the whole metadata segment.
                i = segmentEnd
                continue
            }

            out.write(jpeg, i, segmentEnd - i)
            i = segmentEnd
        }
        return out.toByteArray()
    }

    private fun startsWithSof(jpeg: ByteArray): Boolean =
        jpeg.size >= 2 && (jpeg[0].toInt() and 0xFF) == MARKER && (jpeg[1].toInt() and 0xFF) == 0xD8

    private fun isStandalone(marker: Int): Boolean = when (marker) {
        // Standalone markers with no length field.
        0x01, // TEM
        in 0xD0..0xD7, // RST0..RST7
        in 0xD8..0xD9, // SOI, EOI
        -> true
        else -> false
    }
}