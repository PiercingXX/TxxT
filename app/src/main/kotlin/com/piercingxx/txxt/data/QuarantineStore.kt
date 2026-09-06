package com.piercingxx.txxt.data

/**
 * Persistence and disposition for quarantined inbound SMS/MMS.
 *
 * [InboundFilter] returns [com.piercingxx.txxt.block.MessageDisposition.QUARANTINE]
 * for unknown senders when the operator opts in. The deliver receivers used to
 * drop those messages — silent data loss. This store is the hold: persist
 * unread, hide the thread from the main list (the archive idea), and let the
 * review surface deliver / block / delete per sender.
 *
 * Pure-ish over its DAO parameters: zero `android.*` imports, JVM-testable
 * with the same fake DAOs as [InboundStore]. All writes ride
 * [InboundStore.withPersistenceLock] so a hold cannot race a live delivery.
 */
object QuarantineStore {

    /**
     * Persists an inbound SMS as unread on a quarantined thread and returns
     * the message id. The row shape matches a normal inbound SMS; only the
     * conversation flag differs.
     */
    suspend fun persistInboundSms(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        body: String,
        dateMillis: Long,
    ): Long = InboundStore.persistInboundSms(
        conversations, messages, address, body, dateMillis, quarantined = true,
    )

    /**
     * Persists an inbound MMS metadata row on a quarantined thread and
     * returns the message id. Same shape as a normal inbound MMS hold.
     */
    suspend fun persistInboundMmsMetadata(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        dateMillis: Long,
        contentLocation: String? = null,
    ): Long = InboundStore.persistInboundMmsMetadata(
        conversations, messages, address, dateMillis, contentLocation, quarantined = true,
    )

    /**
     * Releases [conversationId] into the inbox: the thread is no longer
     * quarantined (and no longer archived). Messages stay unread so the
     * badge lights up. Future inbound from this sender still depends on
     * the live filter (the review surface adds a block-override when the
     * operator taps Deliver).
     */
    suspend fun deliver(
        conversations: ConversationDao,
        conversationId: Long,
    ) = InboundStore.withPersistenceLock {
        InboundStore.surfaceConversation(conversations, conversationId)
    }

    /**
     * Deletes the quarantined thread and every message in it. Does not
     * touch the block list — Block is a separate review action.
     */
    suspend fun delete(
        conversations: ConversationDao,
        messages: MessageDao,
        conversationId: Long,
    ) = InboundStore.withPersistenceLock {
        messages.deleteForConversation(conversationId)
        conversations.deleteById(conversationId)
    }

    /** First participant address on [entity], or null when the thread is empty. */
    fun senderAddress(entity: ConversationEntity): String? =
        entity.participantAddresses.split('\u0001').firstOrNull { it.isNotBlank() }
}
