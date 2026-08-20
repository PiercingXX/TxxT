package com.piercingxx.txxt.core

/**
 * Derivation of unread counts across a set of conversations.
 *
 * Pure-Kotlin helpers with zero `android.*` imports so the derivation is
 * JVM-testable without a device. Per-conversation counts come from
 * [Conversation.unreadCount]; these helpers aggregate them for the launcher's
 * badge and per-thread list.
 */
object UnreadCount {

    /**
     * Total number of unread incoming messages across all [conversations].
     * This is the launcher-badge number: the sum of each conversation's unread
     * count.
     */
    fun total(conversations: Iterable<Conversation>): Int =
        conversations.sumOf { it.unreadCount }

    /**
     * Maps each conversation id to its unread count, so a thread list can badge
     * each conversation independently. Conversations with no unread messages
     * are included with a count of zero.
     */
    fun perConversation(conversations: Iterable<Conversation>): Map<Long, Int> =
        conversations.associate { it.id to it.unreadCount }

    /**
     * True when at least one conversation holds an unread incoming message.
     */
    fun anyUnread(conversations: Iterable<Conversation>): Boolean =
        conversations.any { it.hasUnread }
}