package com.piercingxx.txxt.core

/**
 * Strips metadata chunks from a PNG byte stream.
 *
 * A PNG is an 8-byte signature followed by a sequence of chunks: a 4-byte
 * big-endian data length, a 4-byte type code, the data, and a 4-byte CRC. PNG
 * carries privacy metadata in dedicated chunk types — `eXIf` (EXIF), and the
 * text chunks `tEXt`, `zTXt`, `iTXt` (comments, XMP, capture-software tags).
 * This scrubber drops those chunks entirely and copies every other chunk
 * verbatim, including `IDAT` image data and `IEND` — no re-encode happens,
 * and since chunk bytes are unchanged their CRCs stay valid untouched.
 *
 * Fail-closed: PNG input whose chunk stream cannot be parsed (a truncated
 * chunk header, or a length that overruns the remaining bytes) returns `null`
 * — never partially-scrubbed bytes. Corrupt input must not leave the device
 * with metadata intact. Input that is not a PNG (signature mismatch) is
 * returned unchanged: this scrubber recognizes no container in it to strip. A
 * missing trailing `IEND` is tolerated: every chunk was still walked and every
 * metadata carrier dropped.
 *
 * Pure-Kotlin with zero `android.*` imports so the scrub logic is JVM-testable
 * without a device (docs/PRIVACY.md §4).
 */
object PngMetadataScrubber {

    private val SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Chunk types that carry privacy metadata. Text chunks are dropped
     * wholesale: they are free-form metadata carriers (`tEXt`/`zTXt` comments,
     * `iTXt` XMP packets).
     */
    private val METADATA_CHUNK_TYPES = setOf("eXIf", "tEXt", "zTXt", "iTXt")

    /**
     * Returns a copy of [png] with EXIF/text metadata chunks removed. The image
     * chunks (`IHDR`, `IDAT`, `IEND`, …) are byte-identical to the input.
     * Input that is not a PNG is returned unchanged; PNG input with an
     * unparseable chunk structure returns `null` — see the fail-closed note in
     * the class KDoc.
     */
    fun scrub(png: ByteArray): ByteArray? {
        if (!hasSignature(png)) return png

        val out = java.io.ByteArrayOutputStream(png.size)
        out.write(SIGNATURE, 0, SIGNATURE.size)
        var i = SIGNATURE.size
        while (i < png.size) {
            // Each chunk: 4-byte BE data length, 4-byte type, data, 4-byte CRC.
            if (i + 8 > png.size) return null // truncated chunk header
            val length = ((png[i].toInt() and 0xFF) shl 24) or
                ((png[i + 1].toInt() and 0xFF) shl 16) or
                ((png[i + 2].toInt() and 0xFF) shl 8) or
                (png[i + 3].toInt() and 0xFF)
            if (length < 0) return null // data longer than Int.MAX_VALUE
            val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
            // Overflow-safe bound check: header + data + CRC must fit.
            if (length > png.size - (i + 8) - 4) return null // overruns the data
            val chunkEnd = i + 8 + length + 4
            if (type !in METADATA_CHUNK_TYPES) out.write(png, i, chunkEnd - i)
            i = chunkEnd
        }
        return out.toByteArray()
    }

    private fun hasSignature(b: ByteArray): Boolean =
        b.size >= SIGNATURE.size && b.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)
}
