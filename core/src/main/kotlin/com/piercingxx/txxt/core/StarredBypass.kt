package com.piercingxx.txxt.core

/**
 * Pure-Kotlin rule that lets a **starred** contact through every suppression.
 *
 * Starred contacts are first-class (`docs/PRIVACY.md:105`): their messages
 * bypass blocking filters and keyword/unknown-sender blocking rather than being
 * routed to quarantine. A block rule that would match a starred contact is
 * surfaced with a [reason] instead of being applied silently
 * (`docs/PRIVACY.md:116`).
 *
 * Matching goes through [PhoneNumbers.matches]: case-insensitive, whitespace-
 * trimmed, digit-normalised (so `"  +1 555 1234 "`, `"+15551234"`, and
 * `"(+1) 555-1234"` all refer to the same sender), with country-code suffix
 * tolerance for phone numbers and exact equality for email-gateway addresses,
 * mirroring [UnknownSenderRule].
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class StarredBypass(
    private val starredContacts: Set<String> = emptySet(),
) {

    /** True when [sender] is a starred contact (format-tolerant; see [PhoneNumbers.matches]). */
    fun isStarred(sender: String): Boolean = starredContacts.any { PhoneNumbers.matches(sender, it) }

    /**
     * True when [sender]'s messages bypass every suppression. Equivalent to
     * [isStarred]: only starred contacts get through.
     */
    fun bypasses(sender: String): Boolean = isStarred(sender)

    /**
     * The reason a starred contact's message bypasses the suppressions, or null
     * when [sender] is not starred. Returns a deterministic message so a
     * surfaced reason is stable.
     */
    fun reason(sender: String): String? =
        if (isStarred(sender)) "Starred contacts bypass every suppression" else null
}