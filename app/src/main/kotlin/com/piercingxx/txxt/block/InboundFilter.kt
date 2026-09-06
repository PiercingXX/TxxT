package com.piercingxx.txxt.block

import com.piercingxx.txxt.core.BlockingFilter
import com.piercingxx.txxt.core.PhoneNumbers
import com.piercingxx.txxt.core.StarredBypass
import com.piercingxx.txxt.core.UnknownSenderRule

/**
 * App-layer inbound message filter.
 *
 * Wraps the core decision classes ([UnknownSenderRule], [BlockingFilter],
 * [StarredBypass]) and maps their result to a
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
 * Quarantine is explicit opt-in and **default off**. Held messages persist
 * through [com.piercingxx.txxt.data.QuarantineStore] (unread, hidden from the
 * main list). The factory default stays fail-open so an unknown sender is
 * never silently held until the operator turns the toggle on.
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
     * Off by default: the hold is opt-in even though the store now exists.
     */
    val quarantineUnknownSenders: Boolean = false,
    /**
     * Live lookup of known contacts. Defaults to the constructor snapshot.
     * The inbound path supplies a provider that re-reads the system contacts
     * book so a contact saved after process start is not held.
     */
    private val knownContactsProvider: () -> Set<String> = { knownContacts },
) {

    private val blockingFilter = BlockingFilter(keywords = contentKeywords, phrases = contentPhrases)
    private val starredBypass = StarredBypass(starredContacts = starredContacts)
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

        // Check unknown sender against the live contact set (not the
        // construction-time snapshot) so a contact saved after apply() is
        // not held.
        if (UnknownSenderRule(knownContactsProvider()).isUnknown(sender)) {
            return if (starredBypass.isStarred(sender)) {
                MessageDisposition.DELIVER to BlockReason.StarredContactRule("unknown-sender")
            } else if (quarantineUnknownSenders) {
                MessageDisposition.QUARANTINE to BlockReason.UnknownSender
            } else {
                // Fail-open factory default: the hold is opt-in.
                MessageDisposition.DELIVER to null
            }
        }

        return MessageDisposition.DELIVER to null
    }

    private fun isBlockedAddress(sender: String): Boolean =
        blockedAddresses.any { PhoneNumbers.matches(sender, it) }
}
