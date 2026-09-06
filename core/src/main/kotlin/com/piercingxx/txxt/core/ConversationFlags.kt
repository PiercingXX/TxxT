package com.piercingxx.txxt.core

/**
 * How a conversation list orders its rows.
 */
enum class ConversationSortOrder {
    /** Pinned conversations first, then everything else by recency. */
    PINNED_FIRST,

    /** Newest activity first, ignoring pin state. */
    NEWEST_FIRST,

    /** Oldest activity first, ignoring pin state. */
    OLDEST_FIRST,

    /** Conversations with unread messages first, then by recency. */
    UNREAD_FIRST,
}

/**
 * The pinning / sorting / archiving flags of a conversation.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the model is
 * JVM-testable without a device. Instances are immutable; each transition
 * returns a new instance rather than mutating in place.
 */
data class ConversationFlags(
    /** Whether the conversation is pinned to the top of the list. */
    val isPinned: Boolean = false,
    /** Whether the conversation is archived (hidden from the main list). */
    val isArchived: Boolean = false,
    /** Whether notifications for this thread are suppressed (starred still notifies). */
    val isMuted: Boolean = false,
    /**
     * Whether this thread is held in quarantine (hidden from the main list,
     * like archive). Unknown-sender holds land here until the operator
     * delivers, blocks, or deletes them.
     */
    val isQuarantined: Boolean = false,
    /**
     * Epoch millis until which notifications stay off. `0` means no
     * time-based mute; forever-mute is [isMuted] instead.
     */
    val mutedUntilMillis: Long = 0L,
    /** The sort order applied to the conversation list. */
    val sortOrder: ConversationSortOrder = ConversationSortOrder.PINNED_FIRST,
) {
    /** Returns a copy with the conversation pinned. */
    fun pin(): ConversationFlags = copy(isPinned = true)

    /** Returns a copy with the conversation unpinned. */
    fun unpin(): ConversationFlags = copy(isPinned = false)

    /** Returns a copy with the conversation archived. */
    fun archive(): ConversationFlags = copy(isArchived = true)

    /** Returns a copy with the conversation unarchived. */
    fun unarchive(): ConversationFlags = copy(isArchived = false)

    fun mute(): ConversationFlags = copy(isMuted = true, mutedUntilMillis = 0L)

    fun unmute(): ConversationFlags = copy(isMuted = false, mutedUntilMillis = 0L)

    /** Time-based mute: notifications off until [untilMillis], not forever. */
    fun muteUntil(untilMillis: Long): ConversationFlags =
        copy(isMuted = false, mutedUntilMillis = untilMillis)

    fun quarantine(): ConversationFlags = copy(isQuarantined = true)

    fun releaseFromQuarantine(): ConversationFlags = copy(isQuarantined = false)

    /** Returns a copy with the given sort order applied. */
    fun withSortOrder(order: ConversationSortOrder): ConversationFlags =
        copy(sortOrder = order)
}