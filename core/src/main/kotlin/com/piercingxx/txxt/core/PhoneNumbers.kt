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
     */
    fun normalize(address: String): String {
        val trimmed = address.trim().lowercase()
        return if ('@' in trimmed) trimmed else trimmed.filter { it.isDigit() }
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
     * - An address that reduces to nothing (no digits, no `@`) has no identity
     *   and never matches anything.
     */
    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false

        val aIsEmail = '@' in na
        val bIsEmail = '@' in nb
        if (aIsEmail || bIsEmail) {
            // Emails: exact normalized equality, never cross-matched with numbers.
            return aIsEmail && bIsEmail && na == nb
        }

        if (na == nb) return true

        val (shorter, longer) = if (na.length <= nb.length) na to nb else nb to na
        if (shorter.length < MIN_SUFFIX_DIGITS) return false
        return longer.endsWith(shorter)
    }
}
