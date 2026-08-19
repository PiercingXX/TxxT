package com.piercingxx.txxt.core

/**
 * A conversation (thread) grouping messages exchanged with one or more remote
 * participants.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the model is
 * JVM-testable without a device. Messages are held in chronological order.
 */
data class Conversation(
    val id: Long,
    /** Addresses of the remote participants (empty for a self-only thread). */
    val participantAddresses: Set<String>,
    val messages: List<Message> = emptyList(),
) {
    init {
        require(messages.all { it.conversationId == id }) {
            "all messages must belong to this conversation (id=$id)"
        }
    }

    /** The most recent message, or null when the conversation is empty. */
    val latestMessage: Message?
        get() = messages.maxByOrNull { it.timestampMillis }

    /** The timestamp of the most recent message, or null when empty. */
    val latestTimestampMillis: Long?
        get() = latestMessage?.timestampMillis

    /** Number of incoming messages the local user has not yet read. */
    val unreadCount: Int
        get() = messages.count { it.isUnread }

    /** True when this conversation holds at least one unread incoming message. */
    val hasUnread: Boolean
        get() = unreadCount > 0
}