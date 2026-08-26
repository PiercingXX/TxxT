package com.piercingxx.txxt.core

/**
 * What a retrieved MMS (m-retrieve-conf) contains, for an SMS-only inbox that
 * still has to honour the default-handler retrieve contract.
 *
 * TxxT does not send photos and does not render a gallery. It **does** fetch
 * the PDU when the operator taps an inbound MMS, so holding `ROLE_SMS` does
 * not swallow carrier mail. The summary is text-first: a text part if one
 * exists, `[photo]` for image-only, `[MMS]` otherwise. Audio-only retrieve
 * is dropped (PRIVACY.md §5).
 */
data class MmsRetrievedContent(
    val body: String,
    val dropUnstored: Boolean,
) {
    companion object {
        const val PHOTO_PLACEHOLDER = "[photo]"
        const val MMS_PLACEHOLDER = "[MMS]"
    }
}

/**
 * Best-effort inspect of a retrieved MM PDU. Fail-open to [MmsRetrievedContent.MMS_PLACEHOLDER]
 * when the multipart cannot be walked — the bytes were fetched, so the
 * operator still sees that something arrived rather than a blank row.
 *
 * Zero `android.*` imports; JVM-testable.
 */
object MmsRetrievedContentParser {

    fun parse(pdu: ByteArray): MmsRetrievedContent {
        val types = mediaTypesIn(pdu)
        val text = extractTextPlain(pdu)
        val audioOnly = types.any { it.startsWith("audio/") } &&
            types.none { it.startsWith("image/") || it.startsWith("text/") }
        if (audioOnly) {
            return MmsRetrievedContent(body = "", dropUnstored = true)
        }
        val body = when {
            !text.isNullOrBlank() -> text.trim()
            types.any { it.startsWith("image/") } -> MmsRetrievedContent.PHOTO_PLACEHOLDER
            else -> MmsRetrievedContent.MMS_PLACEHOLDER
        }
        return MmsRetrievedContent(body = body, dropUnstored = false)
    }

    /**
     * Collects ASCII media-type tokens (`type/subtype`) visible in the PDU.
     * Notification-ind and retrieve-conf both carry these as Text-string or
     * extension-media values.
     */
    internal fun mediaTypesIn(pdu: ByteArray): List<String> {
        val ascii = pdu.toString(Charsets.ISO_8859_1)
        val found = mutableListOf<String>()
        val regex = Regex("""(text|image|audio|video|application)/[A-Za-z0-9.+\-]+""")
        regex.findAll(ascii).forEach { found += it.value.lowercase() }
        return found.distinct()
    }

    /**
     * Pulls a `text/plain` payload when the part is stored as a NUL-terminated
     * or length-bounded US-ASCII/UTF-8 run after the type token. Returns null
     * when no usable text part is found.
     */
    internal fun extractTextPlain(pdu: ByteArray): String? {
        val marker = "text/plain"
        val ascii = pdu.toString(Charsets.ISO_8859_1)
        val index = ascii.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        var pos = index + marker.length
        // Skip WSP parameters / NULs that sit between the type and the body.
        while (pos < ascii.length && (ascii[pos] == '\u0000' || ascii[pos] == ' ' || ascii[pos] == ';')) {
            if (ascii[pos] == ';') {
                val nul = ascii.indexOf('\u0000', pos)
                pos = if (nul < 0) pos + 1 else nul + 1
            } else {
                pos++
            }
        }
        if (pos >= ascii.length) return null
        val endNul = ascii.indexOf('\u0000', pos)
        val end = if (endNul < 0) ascii.length else endNul
        val slice = ascii.substring(pos, end)
        val printable = slice.takeWhile { it.code in 0x09..0x7E }
            .trim()
            .takeIf { it.isNotEmpty() && it.any { ch -> ch.isLetterOrDigit() } }
        return printable
    }
}
