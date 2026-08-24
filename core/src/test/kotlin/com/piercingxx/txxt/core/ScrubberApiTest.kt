package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the no "send with metadata" toggle guarantee
 * ([docs/PRIVACY.md:82]): the scrubber API exposes **only** the scrubbing path
 * and no mode, flag, or overload that keeps metadata. The entry point
 * [MetadataScrubber.scrub] is a single, unconditional strip — there is no
 * `keepMetadata`, no `preserveMetadata`, and no boolean that skips the scrub.
 *
 * Also locks the fail-closed contract at the dispatch level: a recognized but
 * malformed container returns `null` (the caller must not send), while an
 * unrecognized format is returned unchanged.
 *
 * Synthetic JPEG, PNG and MP4/MOV payloads are constructed in Kotlin — minimal
 * but structurally valid containers with metadata embedded — because no real
 * media fixtures exist in the tree and no parsing library is available offline
 * (see .skippy/IMPLEMENTATION_PLAN.md, "Deferred verification"). This task owns
 * the API-surface / no-toggle guarantee only; the strip behaviour itself is
 * T1's and T2's.
 */
class ScrubberApiTest {

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

    /** A moov with a udta > meta > ilst holding the given metadata items. */
    private fun moovWithMetadata(vararg items: ByteArray): ByteArray {
        val ilst = atom("ilst", items.fold(ByteArray(0)) { acc, it -> acc + it })
        val meta = metaAtom(ilst)
        val udta = atom("udta", meta)
        return atom("moov", udta)
    }

    // --- no metadata-preserving path -------------------------------------

    @Test
    fun `public API surface has no metadata-preserving switch`() {
        val methods = MetadataScrubber::class.java.methods.map { it.name }.toSet()

        // The only public method is the unconditional scrub.
        assertTrue("scrub entry point must exist", methods.contains("scrub"))

        // No method name suggests a metadata-preserving mode.
        assertFalse("no keepMetadata switch", methods.any { it.contains("keepMetadata", ignoreCase = true) })
        assertFalse("no preserveMetadata switch", methods.any { it.contains("preserveMetadata", ignoreCase = true) })
        assertFalse("no toggle switch", methods.any { it.contains("toggle", ignoreCase = true) })
        assertFalse("no keep switch", methods.any { it.contains("keep", ignoreCase = true) })
        assertFalse("no preserve switch", methods.any { it.contains("preserve", ignoreCase = true) })
        assertFalse("no optional switch", methods.any { it.contains("optional", ignoreCase = true) })
    }

    @Test
    fun `scrub takes exactly one byte array parameter`() {
        val scrub = MetadataScrubber::class.java.methods.single { it.name == "scrub" }
        val params = scrub.parameterTypes.map { it.name }
        assertTrue(
            "scrub must take exactly one byte-array argument and no switch parameter: got $params",
            params.size == 1 && params[0] == "[B",
        )
    }

    // --- the single entry point always strips ----------------------------

    @Test
    fun `single entry point strips JPEG metadata unconditionally`() {
        val input = jpeg(appSegment(0xE1, exifPayload()))
        val out = MetadataScrubber.scrub(input)
        assertNotNull(out)
        assertFalse("JPEG EXIF must be stripped", contains(out!!, exifPayload()))
        assertTrue("JPEG scan data must survive", contains(out, scanData()))
    }

    @Test
    fun `single entry point strips MP4 metadata unconditionally`() {
        val input = mp4(moovWithMetadata(ilstItem("\u00A9xyz", gpsPayload())))
        val out = MetadataScrubber.scrub(input)
        assertNotNull(out)
        assertFalse("MP4 GPS must be stripped", contains(out!!, gpsPayload()))
        assertTrue("MP4 mdat must survive", contains(out, mdatPayload()))
    }

    // --- fail-closed dispatch ----------------------------------------------

    @Test
    fun `corrupt JPEG fails closed`() {
        // SOI followed by garbage that is not a marker byte.
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42, 0x13, 0x37)
        assertNull(MetadataScrubber.scrub(corrupt))
    }

    @Test
    fun `oversized MP4 atom fails closed`() {
        val ftyp = atom("ftyp", "isom\u0000\u0000\u0000\u0000isom".toByteArray(Charsets.ISO_8859_1))
        val overlong = int32(Int.MAX_VALUE) + "mdat".toByteArray(Charsets.ISO_8859_1)
        assertNull(MetadataScrubber.scrub(ftyp + overlong))
    }

    @Test
    fun `truncated PNG chunk fails closed`() {
        val headerOnly = pngSignature +
            int32(64) + "IDAT".toByteArray(Charsets.ISO_8859_1) + // claims 64 data bytes...
            byteArrayOf(0x01, 0x02) // ...and carries none of them
        assertNull(MetadataScrubber.scrub(headerOnly))
    }

    @Test
    fun `unrecognized format passes through non-null and unchanged`() {
        val gif = "GIF89a" + "\u00A9nothing-to-strip-here".repeat(2)
        val bytes = gif.toByteArray(Charsets.ISO_8859_1)
        assertArrayEquals(bytes, MetadataScrubber.scrub(bytes))
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
}
