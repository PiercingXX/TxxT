package com.piercingxx.txxt.block

import com.piercingxx.txxt.core.BlockDecision
import com.piercingxx.txxt.core.BlockingFilter
import com.piercingxx.txxt.core.PhoneNumbers
import com.piercingxx.txxt.core.StarredBypass
import com.piercingxx.txxt.core.UnknownSenderRule

/**
 * App-layer inbound message filter.
 *
 * Wraps the core decision classes ([UnknownSenderRule], [BlockingFilter],
 * [StarredBypass], [BlockDecision]) and maps their result to a
 * [MessageDisposition] and an optional [BlockReason].
 *
 * Decision order:
 * 1. Sender on the blocked-address list → [MessageDisposition.BLOCK].
 * 2. Content filter (keyword/phrase) matches → [MessageDisposition.BLOCK].
 * 3. Unknown sender (not in contacts) AND [quarantineUnknownSenders] is true
 *    → [MessageDisposition.QUARANTINE]; when it is false (the default) the
 *    unknown sender **delivers**.
 * 4. Otherwise → [MessageDisposition.DELIVER].
 *
 * Quarantine is explicit opt-in. `docs/PRIVACY.md` §8 lists "block unknown
 * senders by default" among *proposed* improvements (§8.7) that have not been
 * adopted, and there is no quarantine store to persist held messages to — a
 * quarantined message is dropped by the receivers without ever being written
 * anywhere, i.e. silent data loss. So the factory default is fail-open:
 * unknown non-blocked senders are delivered until both a quarantine store and
 * an explicit user setting exist.
 *
 * Address matching goes through `PhoneNumbers.matches` (core): case-
 * insensitive, whitespace-trimmed, digit-normalised with country-code suffix
 * tolerance for phone numbers, exact equality for email-gateway addresses.
 *
 * Starred contacts bypass every suppression: when a rule would match a
 * starred sender the disposition is [MessageDisposition.DELIVER] and the
 * [BlockReason] has [BlockReason.canOverride] set to true.
 *
 * A sender with a persisted [BlockOverrideStore] override bypasses every
 * suppression too: the override is the user's explicit "allow this sender"
 * decision, so it wins over the block list, content filter, and unknown-sender
 * rule. When [blockOverrideStore] is null (the default) no overrides apply.
 */
class InboundFilter(
    knownContacts: Set<String> = emptySet(),
    blockedAddresses: Set<String> = emptySet(),
    starredContacts: Set<String> = emptySet(),
    contentKeywords: Set<String> = emptySet(),
    contentPhrases: Set<String> = emptySet(),
    private val blockOverrideStore: BlockOverrideStore? = null,
    /**
     * Whether the unknown-sender rule routes unknown senders to QUARANTINE.
     * Off by default: §8.7 of docs/PRIVACY.md is proposed, not adopted, and
     * quarantine has no persistence behind it yet, so the factory posture is
     * fail-open delivery.
     */
    val quarantineUnknownSenders: Boolean = false,
) {

    private val unknownSenderRule = UnknownSenderRule(knownContacts = knownContacts)
    private val blockingFilter = BlockingFilter(keywords = contentKeywords, phrases = contentPhrases)
    private val starredBypass = StarredBypass(starredContacts = starredContacts)
    private val blockDecision = BlockDecision(
        filter = blockingFilter,
        unknownSenderRule = unknownSenderRule,
        starredBypass = starredBypass,
    )
    // Originals are kept; membership compares with PhoneNumbers.matches so
    // formatting variance ("+1 555 8888" vs "55588888888"-style variants)
    // cannot evade the list.
    private val blockedAddresses = blockedAddresses.toSet()

    /**
     * Evaluate an inbound message from [sender] with body [body].
     *
     * Returns a pair of the [MessageDisposition] and an optional [BlockReason]
     * (present when a rule matched, even if the message was delivered because
     * the sender is starred).
     */
    fun evaluate(sender: String, body: String): Pair<MessageDisposition, BlockReason?> {
        // A user override allows the sender through every suppression.
        if (blockOverrideStore?.hasOverride(sender) == true) {
            return MessageDisposition.DELIVER to null
        }

        // Check blocked address list first
        if (isBlockedAddress(sender)) {
            return if (starredBypass.isStarred(sender)) {
                MessageDisposition.DELIVER to BlockReason.StarredContactRule("blocked-address-list")
            } else {
                MessageDisposition.BLOCK to BlockReason.BlockedList
            }
        }

        // Check content filter
        val matchedTerm = blockingFilter.matchedTerm(body)
        if (matchedTerm != null) {
            return if (starredBypass.isStarred(sender)) {
                MessageDisposition.DELIVER to BlockReason.StarredContactRule("content-filter")
            } else {
                MessageDisposition.BLOCK to BlockReason(
                    type = BlockReason.Type.BLOCKED_LIST,
                    message = "Message matched content filter: $matchedTerm",
                )
            }
        }

        // Check unknown sender
        if (unknownSenderRule.isUnknown(sender)) {
            return if (starredBypass.isStarred(sender)) {
                MessageDisposition.DELIVER to BlockReason.StarredContactRule("unknown-sender")
            } else if (quarantineUnknownSenders) {
                // Explicit opt-in: docs/PRIVACY.md §8.7 is proposed, not adopted.
                MessageDisposition.QUARANTINE to BlockReason.UnknownSender
            } else {
                // Fail-open factory default: no adopted quarantine setting and
                // no quarantine persistence — delivering loses nothing.
                MessageDisposition.DELIVER to null
            }
        }

        return MessageDisposition.DELIVER to null
    }

    private fun isBlockedAddress(sender: String): Boolean =
        blockedAddresses.any { PhoneNumbers.matches(sender, it) }
}
