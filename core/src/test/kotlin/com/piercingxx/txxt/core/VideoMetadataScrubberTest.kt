package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [VideoMetadataScrubber]. Synthetic MP4/MOV payloads are constructed
 * in Kotlin — minimal but structurally valid containers with GPS/device/
 * creation-time/encoder atoms embedded — because no real media fixtures exist
 * in the tree and no parsing library is available offline (see
 * .skippy/IMPLEMENTATION_PLAN.md, "Deferred verification").
 */
class VideoMetadataScrubberTest {

    // --- MP4 atom construction helpers -----------------------------------

    private fun int32(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

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

    /** A minimal MP4: ftyp, optional moov, mdat preserved byte-for-byte. */
    private fun mp4(moov: ByteArray): ByteArray {
        val ftyp = atom("ftyp", "isom\u0000\u0000\u0000\u0000isom".toByteArray(Charsets.ISO_8859_1))
        val mdat = atom("mdat", byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        return ftyp + moov + mdat
    }

    private fun gpsPayload(): ByteArray =
        "40.7128,-74.0060".toByteArray(Charsets.ISO_8859_1)

    private fun encoderPayload(): ByteArray =
        "Lavf58.29.100".toByteArray(Charsets.ISO_8859_1)

    private fun datePayload(): ByteArray =
        "2024-01-15T10:30:00Z".toByteArray(Charsets.ISO_8859_1)

    private fun makePayload(): ByteArray =
        "Apple".toByteArray(Charsets.ISO_8859_1)

    private fun modelPayload(): ByteArray =
        "iPhone 15 Pro".toByteArray(Charsets.ISO_8859_1)

    /** A moov with a udta > meta > ilst holding the given metadata items. */
    private fun moovWithMetadata(vararg items: ByteArray): ByteArray {
        val ilst = atom("ilst", items.fold(ByteArray(0)) { acc, it -> acc + it })
        val meta = metaAtom(ilst)
        val udta = atom("udta", meta)
        return atom("moov", udta)
    }

    // --- tests -----------------------------------------------------------

    @Test
    fun `strips GPS atom`() {
        val input = mp4(moovWithMetadata(ilstItem("\u00A9xyz", gpsPayload())))
        val out = VideoMetadataScrubber.scrub(input)
        assertFalse(contains(out, gpsPayload()))
        assertTrue(contains(out, mdatPayload()))
    }

    @Test
    fun `strips encoder atom`() {
        val input = mp4(moovWithMetadata(ilstItem("\u00A9too", encoderPayload())))
        val out = VideoMetadataScrubber.scrub(input)
        assertFalse(contains(out, encoderPayload()))
        assertTrue(contains(out, mdatPayload()))
    }

    @Test
    fun `strips creation-time atom`() {
        val input = mp4(moovWithMetadata(ilstItem("\u00A9day", datePayload())))
        val out = VideoMetadataScrubber.scrub(input)
        assertFalse(contains(out, datePayload()))
        assertTrue(contains(out, mdatPayload()))
    }

    @Test
    fun `strips device make and model atoms`() {
        val input = mp4(
            moovWithMetadata(
                ilstItem("\u00A9mak", makePayload()),
                ilstItem("\u00A9mod", modelPayload()),
            ),
        )
        val out = VideoMetadataScrubber.scrub(input)
        assertFalse(contains(out, makePayload()))
        assertFalse(contains(out, modelPayload()))
        assertTrue(contains(out, mdatPayload()))
    }

    @Test
    fun `strips all metadata atoms together`() {
        val input = mp4(
            moovWithMetadata(
                ilstItem("\u00A9xyz", gpsPayload()),
                ilstItem("\u00A9too", encoderPayload()),
                ilstItem("\u00A9day", datePayload()),
                ilstItem("\u00A9mak", makePayload()),
                ilstItem("\u00A9mod", modelPayload()),
            ),
        )
        val out = VideoMetadataScrubber.scrub(input)
        assertFalse(contains(out, gpsPayload()))
        assertFalse(contains(out, encoderPayload()))
        assertFalse(contains(out, datePayload()))
        assertFalse(contains(out, makePayload()))
        assertFalse(contains(out, modelPayload()))
        assertTrue(contains(out, mdatPayload()))
    }

    @Test
    fun `preserves mdat media payload byte-identically`() {
        val input = mp4(
            moovWithMetadata(
                ilstItem("\u00A9xyz", gpsPayload()),
                ilstItem("\u00A9too", encoderPayload()),
            ),
        )
        val out = VideoMetadataScrubber.scrub(input)
        assertArrayEquals(mdatPayload(), extractMdat(out))
    }

    @Test
    fun `preserves non-metadata ilst items`() {
        val title = ilstItem("\u00A9nam", "My Vacation".toByteArray(Charsets.ISO_8859_1))
        val input = mp4(moovWithMetadata(title, ilstItem("\u00A9xyz", gpsPayload())))
        val out = VideoMetadataScrubber.scrub(input)
        assertTrue(contains(out, "My Vacation".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(contains(out, gpsPayload()))
    }

    @Test
    fun `returns non-MP4 input unchanged`() {
        val notMp4 = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00) // RIFF...
        val out = VideoMetadataScrubber.scrub(notMp4)
        assertArrayEquals(notMp4, out)
    }

    @Test
    fun `empty input returns unchanged`() {
        val empty = ByteArray(0)
        assertArrayEquals(empty, VideoMetadataScrubber.scrub(empty))
    }

    @Test
    fun `mp4 with no metadata is unchanged`() {
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

    private fun readInt32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or
            ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or
            (b[i + 3].toInt() and 0xFF)
}