package com.piercingxx.txxt.core

/**
 * Direction of a message relative to the local device.
 */
enum class MessageDirection {
    /** Received from a remote sender. */
    INCOMING,

    /** Sent by the local device. */
    OUTGOING,
}

/**
 * Transport a message travels over.
 */
enum class MessageTransport {
    SMS,
    MMS,
}

/**
 * A single SMS/MMS message in a conversation.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the model is
 * JVM-testable without a device. Timestamps are epoch milliseconds.
 */
data class Message(
    val id: Long,
    val conversationId: Long,
    val direction: MessageDirection,
    val transport: MessageTransport,
    val body: String,
    val timestampMillis: Long,
    /** Address of the remote participant; null for an outgoing message. */
    val senderAddress: String? = null,
    /** Whether the local user has read this message. */
    val isRead: Boolean = false,
    /**
     * Whether an outgoing message has been transmitted. `false` marks a pending
     * send — an outgoing message queued but not yet sent (e.g. interrupted by a
     * reboot) that the T2 reboot reconcile re-drives through the send pipeline.
     * Always `true` for incoming messages.
     */
    val isSent: Boolean = true,
    /**
     * MMSC Content-Location for an inbound MMS that has not been retrieved yet.
     * Null for SMS and for MMS whose body has already been fetched.
     */
    val contentLocation: String? = null,
    /** Absolute path of a retrieved or outgoing photo on disk; null when none. */
    val mediaPath: String? = null,
) {
    /** True when this is an incoming message the user has not yet read. */
    val isUnread: Boolean
        get() = direction == MessageDirection.INCOMING && !isRead
}