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
 * Matching is case-insensitive and trims surrounding whitespace so that
 * `"  +1 555 1234 "` and `"+1 555 1234"` refer to the same sender, mirroring
 * [UnknownSenderRule].
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class StarredBypass(
    private val starredContacts: Set<String> = emptySet(),
) {

    /** True when [sender] is a starred contact (case-insensitive, trimmed). */
    fun isStarred(sender: String): Boolean = normalize(sender) in starredNormalized()

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

    private fun starredNormalized(): Set<String> = starredContacts.mapTo(mutableSetOf()) { normalize(it) }

    private fun normalize(address: String): String = address.trim().lowercase()
}