package com.piercingxx.txxt.core

/**
 * Pure-Kotlin decision that combines the blocking rules with the starred
 * bypass, deciding whether an incoming message should be blocked and surfacing
 * a reason when a rule would match a starred contact.
 *
 * A starred contact is never blocked (`docs/PRIVACY.md:116`): when a rule
 * (keyword/phrase filter or unknown-sender rule) would match a starred sender,
 * the block is surfaced with a [reason] rather than applied silently
 * (`docs/INSPIRATION.md:162`). Unstarred senders keep the full suppression
 * posture.
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class BlockDecision(
    private val filter: BlockingFilter,
    private val unknownSenderRule: UnknownSenderRule,
    private val starredBypass: StarredBypass,
) {

    /**
     * Whether the message from [sender] with [body] should be blocked. A starred
     * contact is never blocked, even when a rule matches.
     */
    fun shouldBlock(sender: String, body: String): Boolean =
        !starredBypass.isStarred(sender) && matchingRuleReason(sender, body) != null

    /**
     * The reason a block was decided, or the reason a block would have been
     * surfaced for a starred contact, or null when no rule matches.
     *
     * - No rule matches: null.
     * - A rule matches an unstarred sender: the rule's reason (the block is
     *   applied).
     * - A rule matches a starred sender: a reason explaining the block would
     *   match a starred contact and is therefore not applied.
     *
     * Returns a deterministic message so a surfaced reason is stable.
     */
    fun reason(sender: String, body: String): String? {
        val ruleReason = matchingRuleReason(sender, body) ?: return null
        return if (starredBypass.isStarred(sender)) {
            "Block would match starred contact: $ruleReason"
        } else {
            ruleReason
        }
    }

    private fun matchingRuleReason(sender: String, body: String): String? {
        val filterTerm = filter.matchedTerm(body)
        if (filterTerm != null) return "Blocked term: $filterTerm"
        return unknownSenderRule.reason(sender)
    }
}