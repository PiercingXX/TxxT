package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [PngMetadataScrubber]. Synthetic PNG payloads are constructed in
 * Kotlin — signature plus structurally valid chunks with EXIF/text metadata
 * embedded — because no real media fixtures exist in the tree and no parsing
 * library is available offline (see .skippy/IMPLEMENTATION_PLAN.md,
 * "Deferred verification").
 *
 * CRC bytes are arbitrary fixed markers: the scrubber copies chunks verbatim
 * (no CRC recompute), so byte-identical output comparisons prove they survive
 * untouched.
 */
class PngMetadataScrubberTest {

    // --- PNG construction helpers ----------------------------------------

    private val signature =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun int32(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    /** A full chunk with a fixed placeholder CRC (preserved verbatim). */
    private fun chunk(type: String, data: ByteArray): ByteArray {
        val crc = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        return int32(data.size) + type.toByteArray(Charsets.ISO_8859_1) + data + crc
    }

    private fun png(vararg chunks: ByteArray): ByteArray =
        signature + chunks.fold(ByteArray(0)) { acc, c -> acc + c }

    private fun ihdr(): ByteArray =
        chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 0, 0, 0))

    private fun idat(): ByteArray =
        chunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x60, 0x60, 0x00, 0x00, 0x00, 0x04, 0x00, 0x01))

    private fun iend(): ByteArray = chunk("IEND", ByteArray(0))

    private fun exifChunk(): ByteArray =
        chunk("eXIf", "MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0001\u0001\u001A\u0000\u0005\u0000\u0000\u0000\u0001".toByteArray(Charsets.ISO_8859_1))

    private fun textChunk(): ByteArray =
        chunk("tEXt", "Comment\u0000Taken at home".toByteArray(Charsets.ISO_8859_1))

    private fun ztxtChunk(): ByteArray =
        chunk("zTXt", "Location\u0000\u000072.0,-122.0".toByteArray(Charsets.ISO_8859_1))

    private fun itxtChunk(): ByteArray =
        chunk("iTXt", "XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<GPS>40.7,-74.0</GPS>".toByteArray(Charsets.ISO_8859_1))

    // --- tests -------------------------------------------------------------

    @Test
    fun `strips EXIF and text chunks and keeps image chunks byte-identical`() {
        val input = png(ihdr(), exifChunk(), textChunk(), ztxtChunk(), itxtChunk(), idat(), iend())
        val expected = png(ihdr(), idat(), iend())

        val out = PngMetadataScrubber.scrub(input)!!

        assertArrayEquals(expected, out)
    }

    @Test
    fun `png with no metadata is unchanged`() {
        val input = png(ihdr(), idat(), iend())
        assertArrayEquals(input, PngMetadataScrubber.scrub(input))
    }

    @Test
    fun `missing IEND is tolerated`() {
        val input = png(ihdr(), exifChunk(), idat())
        val expected = png(ihdr(), idat())

        assertArrayEquals(expected, PngMetadataScrubber.scrub(input))
    }

    @Test
    fun `returns non-PNG input unchanged`() {
        val notPng = "GIF89a89a".toByteArray()
        assertArrayEquals(notPng, PngMetadataScrubber.scrub(notPng))
    }

    @Test
    fun `empty input returns unchanged`() {
        val empty = ByteArray(0)
        assertArrayEquals(empty, PngMetadataScrubber.scrub(empty))
    }

    @Test
    fun `chunk length overrunning remaining bytes fails closed`() {
        val truncatedIdat = int32(1024) + "IDAT".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0x01, 0x02, 0x03) + // claims 1024 data bytes, carries 3
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val input = png(ihdr(), truncatedIdat)
        assertNull(PngMetadataScrubber.scrub(input))
    }

    @Test
    fun `truncated chunk header fails closed`() {
        val input = png(ihdr()) + byteArrayOf(0x00, 0x00, 0x00) // 3 stray bytes
        assertNull(PngMetadataScrubber.scrub(input))
    }
}
