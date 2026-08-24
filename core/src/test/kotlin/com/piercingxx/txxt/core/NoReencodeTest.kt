package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for the no-re-encode guarantee ([docs/PRIVACY.md] §"No re-encode by
 * default"): the scrubbers remove metadata **containers only** and never
 * re-encode the media itself. For synthetic JPEG, PNG and MP4/MOV media, the
 * media payload bytes (image scan data / PNG image chunks / `mdat` content)
 * must be byte-identical in the output while the metadata containers are gone —
 * proving a strip, not a re-encode. Synthetic payloads are constructed in
 * Kotlin because no real media fixtures exist in the tree and no parsing
 * library is available offline (see .skippy/IMPLEMENTATION_PLAN.md,
 * "Deferred verification").
 */
class NoReencodeTest {

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

    // --- PNG construction helpers ----------------------------------------

    private val pngSignature =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun int32(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    /** A full chunk with a fixed placeholder CRC. */
    private fun chunk(type: String, data: ByteArray): ByteArray {
        val crc = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        return int32(data.size) + type.toByteArray(Charsets.ISO_8859_1) + data + crc
    }

    // --- MP4 atom construction helpers -----------------------------------

    private fun atom(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return int32(size) + type.toByteArray(Charsets.ISO_8859_1) + payload
    }

    /** A `meta` fullbox: 4-byte version/flags field before its children. */
    private fun metaAtom(children: ByteArray): ByteArray =
        atom("meta", int32(0) + children)

    /** An `ilst` metadata item: the key type wrapping a `data` payload. */
    private fun ilstItem(key: String, data: ByteArray): ByteArray =
        atom(key, atom("data", int32(1) + data))

    /** A minimal MP4: ftyp, moov with metadata, mdat preserved byte-for-byte. */
    private fun mp4(moov: ByteArray): ByteArray {
        val ftyp = atom("ftyp", "isom\u0000\u0000\u0000\u0000isom".toByteArray(Charsets.ISO_8859_1))
        val mdat = atom("mdat", mdatPayload())
        return ftyp + moov + mdat
    }

    private fun gpsPayload(): ByteArray =
        "40.7128,-74.0060".toByteArray(Charsets.ISO_8859_1)

    private fun encoderPayload(): ByteArray =
        "Lavf58.29.100".toByteArray(Charsets.ISO_8859_1)

    /** A moov with a udta > meta > ilst holding the given metadata items. */
    private fun moovWithMetadata(vararg items: ByteArray): ByteArray {
        val ilst = atom("ilst", items.fold(ByteArray(0)) { acc, it -> acc + it })
        val meta = metaAtom(ilst)
        val udta = atom("udta", meta)
        return atom("moov", udta)
    }

    // --- image (JPEG): no re-encode ----------------------------------------

    @Test
    fun `image scan data is byte-identical after stripping metadata`() {
        val input = jpeg(
            appSegment(0xE1, exifPayload()),
            appSegment(0xE1, xmpPayload()),
            appSegment(0xED, iptcPayload()),
        )
        val out = ImageMetadataScrubber.scrub(input)

        assertNotNull(out)
        // The scan region (SOS header + entropy-coded data) survives verbatim.
        assertArrayEquals(scanRegion(), extractScan(out!!))

        // But the metadata containers are gone.
        assertFalse(contains(out, exifPayload()))
        assertFalse(contains(out, xmpPayload()))
        assertFalse(contains(out, iptcPayload()))
    }

    @Test
    fun `image with no metadata is byte-identical (no re-encode)`() {
        val input = jpeg()
        assertArrayEquals(input, ImageMetadataScrubber.scrub(input))
    }

    // --- image (PNG): no re-encode -----------------------------------------

    @Test
    fun `png image chunks are byte-identical after stripping metadata`() {
        val ihdr = chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 0, 0, 0))
        val idat = chunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x60, 0x60, 0x00, 0x00, 0x00, 0x04, 0x00, 0x01))
        val iend = chunk("IEND", ByteArray(0))
        val exif = chunk("eXIf", exifPayload())
        val text = chunk("iTXt", "<GPS>home</GPS>".toByteArray())

        val input = pngSignature + ihdr + exif + text + idat + iend
        val out = PngMetadataScrubber.scrub(input)

        assertNotNull(out)
        // The image chunks survive verbatim, CRCs included.
        assertArrayEquals(pngSignature + ihdr + idat + iend, out!!)

        // But the metadata chunks are gone.
        assertFalse(contains(out, exifPayload()))
        assertFalse(contains(out, "<GPS>home</GPS>".toByteArray()))
    }

    @Test
    fun `png with no metadata is byte-identical (no re-encode)`() {
        val input = pngSignature +
            chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 0, 0, 0)) +
            chunk("IDAT", byteArrayOf(0x78, 0x9C.toByte())) +
            chunk("IEND", ByteArray(0))
        assertArrayEquals(input, PngMetadataScrubber.scrub(input))
    }

    // --- video: no re-encode ---------------------------------------------

    @Test
    fun `video mdat content is byte-identical after stripping metadata`() {
        val input = mp4(
            moovWithMetadata(
                ilstItem("\u00A9xyz", gpsPayload()),
                ilstItem("\u00A9too", encoderPayload()),
            ),
        )
        val out = VideoMetadataScrubber.scrub(input)

        assertNotNull(out)
        // The mdat media payload survives verbatim.
        assertArrayEquals(mdatPayload(), extractMdat(out!!))

        // But the metadata atoms are gone.
        assertFalse(contains(out, gpsPayload()))
        assertFalse(contains(out, encoderPayload()))
    }

    @Test
    fun `video with no metadata is byte-identical (no re-encode)`() {
        val input = mp4(atom("moov", atom("udta", metaAtom(atom("ilst", ByteArray(0))))))
        assertArrayEquals(input, VideoMetadataScrubber.scrub(input))
    }

    // --- helpers ---------------------------------------------------------

    private fun mdatPayload(): ByteArray =
        byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)

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

    /** Extracts the payload of the top-level `mdat` atom. */
    private fun extractMdat(mp4: ByteArray): ByteArray {
        var i = 0
        while (i + 8 <= mp4.size) {
            val size = readInt32(mp4, i)
            val type = String(mp4, i + 4, 4, Charsets.ISO_8859_1)
            if (type == "mdat") return mp4.copyOfRange(i + 8, i + size)
            i += size
        }
        error("no mdat atom in test MP4")
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

    private fun readInt32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or
            ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or
            (b[i + 3].toInt() and 0xFF)
}
