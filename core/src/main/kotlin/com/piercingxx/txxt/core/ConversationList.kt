package com.piercingxx.txxt.core

/**
 * Ordering of a conversation list.
 *
 * Pure-Kotlin consumer that wires [UnreadCount] into the list ordering: it
 * aggregates per-conversation unread counts via [UnreadCount.perConversation]
 * and applies the requested [ConversationSortOrder]. Zero `android.*` imports
 * so the ordering is JVM-testable without a device.
 */
object ConversationList {

    /**
     * Returns [conversations] ordered by [order].
     *
     * The [ConversationSortOrder.PINNED_FIRST] order puts pinned conversations
     * (ids in [pinnedIds]) before unpinned ones, then falls back to recency
     * (newest activity first) within each group. The unread-aware
     * [ConversationSortOrder.UNREAD_FIRST] order consults the unread counts
     * derived by [UnreadCount.perConversation] so the list surfaces
     * conversations with unread incoming messages first. All orders fall back
     * to recency (newest activity first) within each group.
     */
    fun sorted(
        conversations: Iterable<Conversation>,
        order: ConversationSortOrder = ConversationSortOrder.PINNED_FIRST,
        pinnedIds: Set<Long> = emptySet(),
    ): List<Conversation> {
        val unread = UnreadCount.perConversation(conversations)
        return conversations.sortedWith { a, b ->
            when (order) {
                ConversationSortOrder.UNREAD_FIRST -> {
                    val byUnread = unread.getValue(b.id).compareTo(unread.getValue(a.id))
                    if (byUnread != 0) byUnread else recencyDesc(a, b)
                }

                ConversationSortOrder.OLDEST_FIRST -> recencyAsc(a, b)

                ConversationSortOrder.NEWEST_FIRST -> recencyDesc(a, b)

                ConversationSortOrder.PINNED_FIRST -> {
                    val aPinned = a.id in pinnedIds
                    val bPinned = b.id in pinnedIds
                    when {
                        aPinned && !bPinned -> -1
                        !aPinned && bPinned -> 1
                        else -> recencyDesc(a, b)
                    }
                }
            }
        }
    }

    /** Newest activity first. */
    private fun recencyDesc(a: Conversation, b: Conversation): Int {
        val aTime = a.latestTimestampMillis ?: Long.MIN_VALUE
        val bTime = b.latestTimestampMillis ?: Long.MIN_VALUE
        return bTime.compareTo(aTime)
    }

    /** Oldest activity first. */
    private fun recencyAsc(a: Conversation, b: Conversation): Int {
        val aTime = a.latestTimestampMillis ?: Long.MAX_VALUE
        val bTime = b.latestTimestampMillis ?: Long.MAX_VALUE
        return aTime.compareTo(bTime)
    }
}