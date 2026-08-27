package com.piercingxx.txxt.core

/**
 * The conversation the operator is looking at right now.
 *
 * Process-wide so inbound delivery can skip a shade notification for a thread
 * that is already on screen — opening a conversation must clear that sender's
 * notification, and a new SMS into the open thread must not put it back.
 *
 * [close] is keyed by conversation id so Activity A stopping after Activity B
 * has started cannot wipe B's viewed set.
 */
object ViewedThread {

    @Volatile
    var conversationId: Long = 0L
        private set

    @Volatile
    var senders: Set<String> = emptySet()
        private set

    @Synchronized
    fun open(conversationId: Long, senders: Collection<String>) {
        this.conversationId = conversationId
        this.senders = senders.filter { it.isNotBlank() }.toSet()
    }

    @Synchronized
    fun close(conversationId: Long) {
        if (this.conversationId == conversationId) {
            this.conversationId = 0L
            this.senders = emptySet()
        }
    }

    /** True when [sender] belongs to the thread currently on screen. */
    fun isOpenFor(sender: String): Boolean {
        if (sender.isBlank() || conversationId == 0L) return false
        return senders.any { it == sender || PhoneNumbers.matches(it, sender) }
    }
}
