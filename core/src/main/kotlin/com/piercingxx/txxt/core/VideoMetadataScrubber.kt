package com.piercingxx.txxt.core

/**
 * Strips metadata-bearing atoms from an MP4/MOV byte stream.
 *
 * An MP4/MOV file is a sequence of atoms (boxes). Each atom is a 4-byte
 * big-endian size (including the header) followed by a 4-byte type. Privacy
 * metadata lives inside the `moov` movie atom in two shapes:
 *
 *  - nested: `udta` > `meta` > `ilst` (the item list), where each item atom's
 *    type is the metadata key (`©xyz` GPS, `©too` encoder, `©day` creation
 *    date, `©mak`/`©mod` device make/model);
 *  - flat: QuickTime `.mov` files and Android `MediaRecorder` MP4s place
 *    ©-prefixed keys (`©xyz`, `©mak`, `©mod`, `©day`, …) as **direct children
 *    of `udta`**, with no `meta`/`ilst` nesting at all.
 *
 * This scrubber walks the atom tree, recurses into the container atoms, and
 * drops the known metadata item atoms inside `ilst` plus every ©-prefixed
 * atom under the `udta` subtree — then copies everything else byte-for-byte,
 * including the `mdat` media payload, so no re-encode happens.
 *
 * Fail-closed: MP4/MOV input whose structure cannot be parsed (a truncated or
 * overlong atom header, trailing bytes that are not an atom) returns `null` —
 * never partially-scrubbed bytes. Corrupt input must not leave the device with
 * metadata intact. Input that is not an MP4/MOV (no recognizable leading atom)
 * is returned unchanged.
 *
 * Pure-Kotlin with zero `android.*` imports so the scrub logic is JVM-testable
 * without a device (docs/PRIVACY.md §4).
 */
object VideoMetadataScrubber {

    /** Container atoms whose children we recurse into. */
    private val CONTAINER_TYPES = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "udta", "ilst", "meta",
    )

    /**
     * Metadata-bearing item atoms inside `ilst` — the privacy data the goal
     * names (GPS, device model, creation time, encoder).
     */
    private val METADATA_TYPES = setOf(
        "\u00A9xyz", // GPS coordinates
        "\u00A9too", // encoder
        "\u00A9day", // creation date
        "\u00A9mak", // device make
        "\u00A9mod", // device model
    )

    /**
     * Returns a copy of [mp4] with metadata atoms removed. The `mdat` media
     * payload is byte-identical to the input. Input that is not an MP4/MOV
     * (no recognizable leading atom) is returned unchanged; input whose atom
     * structure cannot be parsed returns `null` — see the fail-closed note in
     * the class KDoc.
     */
    fun scrub(mp4: ByteArray): ByteArray? {
        if (mp4.size < 8) return mp4
        if (atomTypeAt(mp4, 0, mp4.size) == null) return mp4
        return scrubRange(mp4, 0, mp4.size, inIlst = false, inUdta = false)
    }

    /**
     * Parses the atoms in [from, to) and returns the scrubbed bytes, or `null`
     * when the structure is unparseable. Two context flags drive dropping:
     *  - [inIlst] — we are inside an `ilst` item list; its known metadata item
     *    atoms ([METADATA_TYPES]) are dropped;
     *  - [inUdta] — we are looking at a **direct child of `udta`**: QuickTime
     *    `.mov` files and Android `MediaRecorder` MP4s place their ©-prefixed
     *    keys (`©xyz`, `©mak`, `©mod`, `©day`, …) right there, with no
     *    `meta`/`ilst` nesting at all, and every such © atom is metadata.
     */
    private fun scrubRange(
        mp4: ByteArray,
        from: Int,
        to: Int,
        inIlst: Boolean,
        inUdta: Boolean,
    ): ByteArray? {
        val out = java.io.ByteArrayOutputStream(to - from)
        var i = from
        while (i < to) {
            // An unparseable atom header means the structure is broken: fail
            // closed rather than silently dropping trailing bytes.
            val header = atomHeader(mp4, i, to) ?: return null
            val (size, type, headerLen) = header
            val payloadStart = i + headerLen
            val atomEnd = payloadStart + (size - headerLen)

            if ((inIlst && type in METADATA_TYPES) || (inUdta && type.startsWith('\u00A9'))) {
                // Drop the metadata item atom (and its payload).
                i = atomEnd
                continue
            }

            if (type in CONTAINER_TYPES) {
                // `meta` is a fullbox: a 4-byte version/flags field precedes its
                // child atoms. Recurse into the children and rebuild the atom
                // with a recomputed size. The udta flag marks direct children
                // only: inside deeper containers the ilst rule governs instead.
                val versionFlags = if (type == "meta") readInt32(mp4, payloadStart) else null
                val childStart = if (type == "meta") payloadStart + 4 else payloadStart
                val scrubbed = scrubRange(
                    mp4,
                    childStart,
                    atomEnd,
                    inIlst = type == "ilst",
                    inUdta = type == "udta",
                ) ?: return null
                out.write(containerHeader(type, versionFlags, scrubbed))
            } else {
                out.write(mp4, i, atomEnd - i)
            }
            i = atomEnd
        }
        return out.toByteArray()
    }

    /** (size, type, headerLen) of the atom at [i]; null if it is not parseable. */
    private fun atomHeader(mp4: ByteArray, i: Int, to: Int): Triple<Int, String, Int>? {
        if (i + 8 > to) return null
        var size = readInt32(mp4, i)
        val type = String(mp4, i + 4, 4, Charsets.ISO_8859_1)
        var headerLen = 8
        if (size == 1) {
            // 64-bit extended size follows the type.
            if (i + 16 > to) return null
            size = readInt64(mp4, i + 8)
            headerLen = 16
        } else if (size == 0) {
            // Size 0 means the atom extends to the end of the range.
            size = to - i
        }
        // Overflow-safe bound check: an oversized size must fail closed here,
        // before callers compute atomEnd from it.
        if (size < headerLen || size > to - i) return null
        return Triple(size, type, headerLen)
    }

    /** The type of the atom at [i], or null if it is not a printable 4-byte code. */
    private fun atomTypeAt(mp4: ByteArray, i: Int, to: Int): String? {
        if (i + 8 > to) return null
        val type = String(mp4, i + 4, 4, Charsets.ISO_8859_1)
        return if (type.all { it.code in 0x20..0x7E }) type else null
    }

    /** A container atom header with a recomputed size. [versionFlags] is the
     *  `meta` fullbox field, written when present. */
    private fun containerHeader(type: String, versionFlags: Int?, childBytes: ByteArray): ByteArray {
        val headerLen = 8 + if (versionFlags != null) 4 else 0
        val total = headerLen + childBytes.size
        val out = java.io.ByteArrayOutputStream(headerLen)
        out.write(int32(total))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        if (versionFlags != null) out.write(int32(versionFlags))
        out.write(childBytes)
        return out.toByteArray()
    }

    private fun readInt32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or
            ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or
            (b[i + 3].toInt() and 0xFF)

    private fun readInt64(b: ByteArray, i: Int): Int {
        val hi = readInt32(b, i)
        val lo = readInt32(b, i + 4)
        if (hi != 0) return Int.MAX_VALUE // sizes beyond 2 GiB fail closed on the range check
        return lo
    }

    private fun int32(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )
}
