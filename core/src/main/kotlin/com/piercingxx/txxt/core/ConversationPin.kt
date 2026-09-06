package com.piercingxx.txxt.core

/**
 * Pin-cap policy for the conversation list.
 *
 * Pinned threads stay above the rest ([ConversationSortOrder.PINNED_FIRST]).
 * The cap is small on purpose: a handful of daily-driver threads, not a
 * second inbox. Unpin is always allowed; pin is refused once the cap is
 * already full. Pure Kotlin, zero `android.*` imports.
 */
object ConversationPin {

    /** Maximum number of pinned conversations. */
    const val MAX_PINNED = 5

    /**
     * Whether [pinnedCount] still has room for a new pin. An already-pinned
     * thread can stay pinned even at the cap (re-pin is a no-op, not a
     * refusal).
     */
    fun canPin(pinnedCount: Int, alreadyPinned: Boolean): Boolean =
        alreadyPinned || pinnedCount < MAX_PINNED
}
