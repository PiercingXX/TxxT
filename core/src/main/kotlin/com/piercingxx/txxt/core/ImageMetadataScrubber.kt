package com.piercingxx.txxt.core

/**
 * Strips metadata-bearing segments from a JPEG byte stream.
 *
 * A JPEG is a sequence of markers (0xFF xx) and their payloads. Most metadata
 * lives in APP1 (EXIF and XMP), APP13 (IPTC/Photoshop) and COM comment
 * segments. This scrubber walks the marker stream, drops those segments, and
 * copies everything else byte-for-byte — including the scan data (SOS..EOI),
 * so no re-encode happens.
 *
 * Fail-closed: JPEG input whose structure cannot be parsed (a non-marker byte
 * outside the scan, a stream truncated mid-marker or mid-segment, a segment
 * length that overruns the data, or a missing EOI) returns `null` — never
 * partially-scrubbed or unscrubbed bytes. Corrupt input must not leave the
 * device with metadata intact. Input that is not a JPEG (no SOI marker) is
 * returned unchanged: this scrubber recognizes no container in it to strip.
 *
 * Pure-Kotlin with zero `android.*` imports so the scrub logic is JVM-testable
 * without a device (docs/PRIVACY.md §4).
 */
object ImageMetadataScrubber {

    /**
     * Markers carrying the metadata the goal names.
     * - APP1 (0xE1): EXIF and XMP.
     * - APP13 (0xED): IPTC / Photoshop.
     * - COM (0xFE): free-form comment — a common GPS/camera-tag carrier.
     * APP0 (0xE0, JFIF) and APP2 (0xE2, ICC profile) are display/colour data,
     * not privacy metadata, and are preserved.
     */
    private val METADATA_MARKERS = setOf(0xE1.toByte(), 0xED.toByte(), 0xFE.toByte())

    private const val MARKER = 0xFF
    private const val SOS = 0xDA // start of scan: payload has no length field
    private const val EOI = 0xD9 // end of image

    /**
     * Returns a copy of [jpeg] with EXIF/XMP/IPTC/comment segments removed. The
     * image scan data is byte-identical to the input. Input that is not a JPEG
     * (does not start with a JPEG SOI marker) is returned unchanged. JPEG input
     * with an unparseable structure returns `null` — see the fail-closed note
     * in the class KDoc.
     */
    fun scrub(jpeg: ByteArray): ByteArray? {
        if (!startsWithSof(jpeg)) return jpeg

        val out = java.io.ByteArrayOutputStream(jpeg.size)
        var i = 0
        var inScan = false
        var sawEoi = false
        while (i < jpeg.size && !sawEoi) {
            if (!inScan) {
                // Outside the scan every byte is a marker (0xFF xx). If we see
                // anything else the stream is malformed and we fail closed —
                // never send possibly-unscrubbed bytes off device.
                if (jpeg[i].toInt() and 0xFF != MARKER) return null
            }

            // Every marker is two bytes; a stream truncated mid-marker fails
            // closed for the same reason.
            if (i + 1 >= jpeg.size) return null

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
                sawEoi = true
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
            // include themselves but not the marker bytes. A truncated length
            // prefix or one whose segment overruns the data fails closed.
            if (i + 4 > jpeg.size) return null
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val segmentEnd = i + 2 + length
            if (segmentEnd > jpeg.size) return null

            if (marker.toByte() in METADATA_MARKERS) {
                // Drop the whole metadata segment.
                i = segmentEnd
                continue
            }

            out.write(jpeg, i, segmentEnd - i)
            i = segmentEnd
        }
        // A scan that ends without an EOI marker is a truncated stream.
        if (!sawEoi) return null
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
