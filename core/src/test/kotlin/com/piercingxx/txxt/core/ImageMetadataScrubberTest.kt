package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ImageMetadataScrubber]. Synthetic JPEG payloads are constructed in
 * Kotlin — minimal but structurally valid containers with EXIF/XMP/IPTC/COM
 * segments embedded — because no real media fixtures exist in the tree and no
 * parsing library is available offline (see .skippy/IMPLEMENTATION_PLAN.md,
 * "Deferred verification").
 *
 * Also covers the fail-closed contract: recognized-but-malformed JPEG input
 * must return `null` (never the unscrubbed original).
 */
class ImageMetadataScrubberTest {

    // --- JPEG construction helpers --------------------------------------

    private fun marker(m: Int): ByteArray = byteArrayOf(0xFF.toByte(), m.toByte())

    /** A length-prefixed APPn segment (length includes itself but not 0xFF xx). */
    private fun appSegment(appMarker: Int, payload: ByteArray): ByteArray {
        val length = 2 + payload.size
        val seg = ByteArray(2 + length)
        seg[0] = 0xFF.toByte()
        seg[1] = appMarker.toByte()
        seg[2] = ((length shr 8) and 0xFF).toByte()
        seg[3] = (length and 0xFF).toByte()
        payload.copyInto(seg, 4)
        return seg
    }

    private fun scanData(): ByteArray =
        byteArrayOf(0x11, 0x22, 0x33, 0xFF.toByte(), 0x00, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())

    private fun sosHeader(): ByteArray = byteArrayOf(0x00, 0x03, 0x01, 0x00, 0x00, 0x3F, 0x00)

    /** The full scan region: SOS header plus entropy-coded scan data. */
    private fun scanRegion(): ByteArray = sosHeader() + scanData()

    /** A minimal JPEG: SOI, optional segments, SOS header, scan, EOI. */
    private fun jpeg(vararg segments: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(marker(0xD8)) // SOI
        segments.forEach { out.write(it) }
        out.write(marker(0xDA)) // SOS
        out.write(sosHeader())
        out.write(scanData())
        out.write(marker(0xD9)) // EOI
        return out.toByteArray()
    }

    private fun exifPayload(): ByteArray =
        byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00, 0x01, 0x02, 0x03)

    private fun xmpPayload(): ByteArray =
        "http://ns.adobe.com/xap/1.0/\u0000<camera>Nikon</camera><GPS>40.7,-74.0</GPS>".toByteArray()

    private fun iptcPayload(): ByteArray =
        byteArrayOf('P'.code.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 't'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 'p'.code.toByte(), 0x00, 0x01, 0x02)

    private fun comPayload(): ByteArray =
        "<GPS>40.7128,-74.0060</GPS> Shot on ACME-100".toByteArray()

    // --- tests -----------------------------------------------------------

    @Test
    fun `strips EXIF APP1 segment`() {
        val input = jpeg(appSegment(0xE1, exifPayload()))
        val out = ImageMetadataScrubber.scrub(input)!!
        assertFalse(contains(out, exifPayload()))
        assertTrue(contains(out, scanData()))
    }

    @Test
    fun `strips XMP APP1 segment`() {
        val input = jpeg(appSegment(0xE1, xmpPayload()))
        val out = ImageMetadataScrubber.scrub(input)!!
        assertFalse(contains(out, xmpPayload()))
        assertTrue(contains(out, scanData()))
    }

    @Test
    fun `strips IPTC APP13 segment`() {
        val input = jpeg(appSegment(0xED, iptcPayload()))
        val out = ImageMetadataScrubber.scrub(input)!!
        assertFalse(contains(out, iptcPayload()))
        assertTrue(contains(out, scanData()))
    }

    @Test
    fun `strips GPS-like COM comment segment`() {
        val input = jpeg(appSegment(0xFE, comPayload()))
        val out = ImageMetadataScrubber.scrub(input)!!
        assertFalse(contains(out, comPayload()))
        assertTrue(contains(out, scanData()))
    }

    @Test
    fun `strips all metadata segments together`() {
        val input = jpeg(
            appSegment(0xE1, exifPayload()),
            appSegment(0xE1, xmpPayload()),
            appSegment(0xED, iptcPayload()),
            appSegment(0xFE, comPayload()),
        )
        val out = ImageMetadataScrubber.scrub(input)!!
        assertFalse(contains(out, exifPayload()))
        assertFalse(contains(out, xmpPayload()))
        assertFalse(contains(out, iptcPayload()))
        assertFalse(contains(out, comPayload()))
        assertTrue(contains(out, scanData()))
    }

    @Test
    fun `preserves scan data byte-identically`() {
        val input = jpeg(
            appSegment(0xE1, exifPayload()),
            appSegment(0xED, iptcPayload()),
        )
        val out = ImageMetadataScrubber.scrub(input)!!
        assertTrue(contains(out, scanData()))
        // The scan region (SOS header + entropy-coded data) is untouched: the
        // exact byte sequence survives verbatim.
        assertArrayEquals(scanRegion(), extractScan(out))
    }

    @Test
    fun `preserves non-metadata segments such as JFIF and ICC`() {
        val jfif = appSegment(0xE0, byteArrayOf('J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00, 0x01))
        val icc = appSegment(0xE2, byteArrayOf('I'.code.toByte(), 'C'.code.toByte(), 'C'.code.toByte(), 0x00, 0x01))
        val input = jpeg(jfif, appSegment(0xE1, exifPayload()), icc)
        val out = ImageMetadataScrubber.scrub(input)!!
        assertTrue(contains(out, jfif))
        assertTrue(contains(out, icc))
        assertFalse(contains(out, exifPayload()))
    }

    @Test
    fun `returns non-JPEG input unchanged`() {
        val notJpeg = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A)
        val out = ImageMetadataScrubber.scrub(notJpeg)
        assertArrayEquals(notJpeg, out)
    }

    @Test
    fun `empty input returns unchanged`() {
        val empty = ByteArray(0)
        assertArrayEquals(empty, ImageMetadataScrubber.scrub(empty))
    }

    @Test
    fun `jpeg with no metadata is unchanged`() {
        val input = jpeg()
        assertArrayEquals(input, ImageMetadataScrubber.scrub(input))
    }

    // --- fail-closed: malformed JPEG input returns null -------------------

    @Test
    fun `soi followed by a garbage non-marker byte returns null`() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42, 0x00, 0x01, 0x02)
        assertNull(ImageMetadataScrubber.scrub(corrupt))
    }

    @Test
    fun `stream truncated mid-marker returns null`() {
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertNull(ImageMetadataScrubber.scrub(truncated))
    }

    @Test
    fun `segment length overflowing past end of stream returns null`() {
        // An APP1 whose declared length (2 + 65535 bytes) far exceeds the data.
        val overlong = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE1.toByte(), 0xFF.toByte(), 0xF1.toByte(), // APP1, length claims 65521 bytes
            0x01, 0x02, 0x03, // ...but only 3 payload bytes exist
        )
        assertNull(ImageMetadataScrubber.scrub(overlong))
    }

    @Test
    fun `segment truncated before its length field returns null`() {
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00)
        assertNull(ImageMetadataScrubber.scrub(truncated))
    }

    @Test
    fun `scan without an EOI terminator returns null`() {
        val out = java.io.ByteArrayOutputStream()
        out.write(marker(0xD8)) // SOI
        out.write(marker(0xDA)) // SOS
        out.write(sosHeader())
        out.write(scanData()) // ...and nothing else: stream ends mid-scan
        assertNull(ImageMetadataScrubber.scrub(out.toByteArray()))
    }

    @Test
    fun `malformed metadata-bearing jpeg is never returned as-is`() {
        // The privacy-critical direction: even though this corrupt stream still
        // contains EXIF bytes, scrubbing must not hand back the original.
        val corrupt = jpeg(appSegment(0xE1, exifPayload())).copyOfRange(0, 20) // cut mid-segment
        assertNull(ImageMetadataScrubber.scrub(corrupt))
    }

    // --- helpers ---------------------------------------------------------

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    /** Extracts the bytes between SOS and EOI (the raw scan payload). */
    private fun extractScan(jpeg: ByteArray): ByteArray {
        val sos = indexOf(jpeg, marker(0xDA))
        val eoi = indexOf(jpeg, marker(0xD9))
        if (sos < 0 || eoi < 0 || eoi <= sos) error("malformed test JPEG")
        return jpeg.copyOfRange(sos + 2, eoi)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
