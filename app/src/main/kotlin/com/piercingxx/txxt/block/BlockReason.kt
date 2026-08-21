package com.piercingxx.txxt.block

/**
 * Describes why an inbound message was blocked or quarantined.
 *
 * When [canOverride] is true the UI should surface an affordance (e.g. a
 * "allow" button) so the user can unblock the sender.
 */
data class BlockReason(
    val type: Type,
    val message: String,
    val canOverride: Boolean = false,
) {
    enum class Type {
        UNKNOWN_SENDER,
        BLOCKED_LIST,
        STARRED_CONTACT_RULE,
    }

    companion object {
        val UnknownSender = BlockReason(
            type = Type.UNKNOWN_SENDER,
            message = "Sender is not in your contacts",
        )

        val BlockedList = BlockReason(
            type = Type.BLOCKED_LIST,
            message = "Sender is on your block list",
        )

        fun StarredContactRule(ruleName: String) = BlockReason(
            type = Type.STARRED_CONTACT_RULE,
            message = "Rule \"$ruleName\" matched a starred contact",
            canOverride = true,
        )
    }
}
