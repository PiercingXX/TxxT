package com.piercingxx.txxt.block

import com.piercingxx.txxt.core.BlockDecision
import com.piercingxx.txxt.core.BlockingFilter
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
 * 3. Unknown sender (not in contacts) → [MessageDisposition.QUARANTINE].
 * 4. Otherwise → [MessageDisposition.DELIVER].
 *
 * Starred contacts bypass every suppression: when a rule would match a
 * starred sender the disposition is [MessageDisposition.DELIVER] and the
 * [BlockReason] has [BlockReason.canOverride] set to true.
 */
class InboundFilter(
    knownContacts: Set<String> = emptySet(),
    blockedAddresses: Set<String> = emptySet(),
    starredContacts: Set<String> = emptySet(),
    contentKeywords: Set<String> = emptySet(),
    contentPhrases: Set<String> = emptySet(),
) {

    private val unknownSenderRule = UnknownSenderRule(knownContacts = knownContacts)
    private val blockingFilter = BlockingFilter(keywords = contentKeywords, phrases = contentPhrases)
    private val starredBypass = StarredBypass(starredContacts = starredContacts)
    private val blockDecision = BlockDecision(
        filter = blockingFilter,
        unknownSenderRule = unknownSenderRule,
        starredBypass = starredBypass,
    )
    private val blockedAddresses = blockedAddresses.mapTo(mutableSetOf()) { it.trim().lowercase() }

    /**
     * Evaluate an inbound message from [sender] with body [body].
     *
     * Returns a pair of the [MessageDisposition] and an optional [BlockReason]
     * (present when a rule matched, even if the message was delivered because
     * the sender is starred).
     */
    fun evaluate(sender: String, body: String): Pair<MessageDisposition, BlockReason?> {
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
            } else {
                MessageDisposition.QUARANTINE to BlockReason.UnknownSender
            }
        }

        return MessageDisposition.DELIVER to null
    }

    private fun isBlockedAddress(sender: String): Boolean = sender.trim().lowercase() in blockedAddresses
}
