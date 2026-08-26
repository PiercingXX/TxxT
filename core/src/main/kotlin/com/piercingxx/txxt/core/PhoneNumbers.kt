package com.piercingxx.txxt.core

/**
 * Pure-Kotlin sender-address normalisation and matching shared by every
 * comparator in the blocking pipeline ([UnknownSenderRule.isKnown],
 * [StarredBypass.isStarred], the app-layer blocked-address list, and
 * [com.piercingxx.txxt.core.BlockDecision] via those rules).
 *
 * **The evasion this closes:** matching used to be `trim().lowercase()` only,
 * so the same phone number written as `" +15551234567 "`, `"555-123-4567"`,
 * `"(555) 123 4567"`, or `"+1 555 1234567"` normalised to four *different*
 * strings. A blocked sender could evade the blocklist purely by formatting
 * variance, and a starred contact saved one way lost their bypass when the
 * carrier delivered another format. Digit-normalising both sides before
 * comparison makes all four forms refer to one sender.
 *
 * Email-gateway addresses (`foo@carrier.example.com`, how carriers deliver
 * email-to-SMS) are deliberately **not** digit-stripped: an email address's
 * identity lives in its punctuation and letters, so emails compare by exact
 * normalized (trimmed, lowercased) equality only.
 *
 * Zero `android.*` imports so the logic is JVM-testable without a device.
 */
object PhoneNumbers {

    /**
     * The minimum number of digits the SHORTER side must carry for a suffix
     * match to count. A national number is ≥7 digits almost everywhere; short
     * codes (5–6 digits) and emergency/service numbers are shorter. Without
     * the threshold, a stored `"911"` or a 5-digit short code would suffix-match
     * any longer number ending in those digits — a false-positive block of
     * unrelated senders. With it, suffix matching only tolerates the presence/
     * absence of a country code (`+1`) on an otherwise full national number.
     */
    private const val MIN_SUFFIX_DIGITS = 7

    /**
     * Normalises [address] for comparison: trimmed and lowercased; if it
     * contains `@` (an email-gateway address) the trimmed-lowercase form is
     * returned verbatim, otherwise every non-digit character is stripped.
     *
     * Alphanumeric senders (`VERIFY`, `AMAZON`) strip to empty here — they have
     * no digits. Use [conversationKey] as a thread / blocklist identity; this
     * function stays digit-or-email so existing callers that want "digits only"
     * keep that contract.
     */
    fun normalize(address: String): String {
        val trimmed = address.trim().lowercase()
        return if ('@' in trimmed) trimmed else trimmed.filter { it.isDigit() }
    }

    /**
     * The stable identity used as a 1:1 conversation key and as the token
     * alphanumeric senders compare with.
     *
     * - Email: trimmed lowercase (punctuation kept).
     * - Phone number: digits only (same as [normalize]).
     * - Alphanumeric sender (at least one letter, no digits, no `@`): the
     *   trimmed-lowercase token, so `VERIFY` and `verify` are one thread.
     * - Punctuation-only garbage: empty — no identity.
     */
    fun conversationKey(address: String): String {
        val trimmed = address.trim().lowercase()
        if (trimmed.isEmpty()) return ""
        if ('@' in trimmed) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotEmpty()) return digits
        return if (trimmed.any { it.isLetter() }) trimmed else ""
    }

    /**
     * True when [a] and [b] refer to the same sender.
     *
     * - Either side an email address: exact normalized equality only (and both
     *   sides must be emails — an email never matches a bare number).
     * - Both phone numbers: digit-normalized equality, OR a suffix match where
     *   the SHORTER side carries at least [MIN_SUFFIX_DIGITS] digits and one
     *   normalized form ends with the other (tolerates a country code on one
     *   side; the threshold keeps short codes from colliding with longer
     *   numbers).
     * - Both alphanumeric (no digits, at least one letter): exact
     *   [conversationKey] equality (`VERIFY` == `verify`, never `AMAZON`).
     * - An address that reduces to nothing (no digits, no `@`, no letter) has
     *   no identity and never matches anything.
     */
    fun matches(a: String, b: String): Boolean {
        val ka = conversationKey(a)
        val kb = conversationKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false

        val aIsEmail = '@' in ka
        val bIsEmail = '@' in kb
        if (aIsEmail || bIsEmail) {
            return aIsEmail && bIsEmail && ka == kb
        }

        val da = ka.filter { it.isDigit() }
        val db = kb.filter { it.isDigit() }
        if (da.isEmpty() && db.isEmpty()) {
            return ka == kb
        }
        if (da.isEmpty() || db.isEmpty()) return false

        if (da == db) return true

        val (shorter, longer) = if (da.length <= db.length) da to db else db to da
        if (shorter.length < MIN_SUFFIX_DIGITS) return false
        return longer.endsWith(shorter)
    }
}
