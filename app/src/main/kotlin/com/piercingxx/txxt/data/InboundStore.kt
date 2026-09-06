package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.core.PhoneNumbers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistence entry point for inbound delivery — the single store both the
 * default-handler delivery receivers (`SmsDeliverReceiver` / `MmsDeliverReceiver`)
 * and the compose hand-off (`ComposeActivity`) resolve conversations and
 * messages through.
 *
 * Pure-ish over its DAO parameters: zero `android.*` imports, the DAOs are
 * injected, so the find-or-create and persist logic is JVM-testable with
 * hand-rolled fakes (the established seam style of `RestoreService`).
 *
 * **Concurrency:** both deliver receivers persist from independent `goAsync`
 * coroutines, so every public function is serialized through a process-wide
 * [MUTEX]. The find-then-insert sequence is check-then-act: without the lock,
 * two deliveries landing in the same millisecond could both observe an id as
 * free and insert it twice — and because the DAOs upsert with
 * `OnConflictStrategy.REPLACE` (DELETE+INSERT under the message table's
 * CASCADE foreign key), a collided conversation id would silently destroy
 * every message already in that thread. The mutex makes that unreachable.
 *
 * Scope: **single-participant threads only** — the SMS 1:1 model. The lookup
 * key is [PhoneNumbers.conversationKey] of the address (digits-only for phone
 * numbers, trimmed-lowercase for email-gateway and alphanumeric senders), so
 * one correspondent lands on one thread no matter how the carrier or the
 * SENDTO hand-off formatted the number. A 10-digit national form and the
 * `+1` E.164 form of the same person share a thread via
 * [PhoneNumbers.matches], not exact-key equality. MESSAGE rows keep the raw
 * delivered address as their `senderAddress` (display fidelity).
 * Multi-participant MMS threads are deferred.
 */
object InboundStore {

    /** Serializes all check-then-act persistence (see the concurrency KDoc). */
    private val MUTEX = Mutex()

    /**
     * Runs [block] under the store-wide persistence [MUTEX]. Internal so
     * sibling stores ([OutboundStore]) ride the SAME serialization as inbound
     * delivery instead of introducing a second lock that would leave the two
     * writers racing each other. Never nest around a public entry point (the
     * Mutex is not reentrant).
     */
    internal suspend fun <T> withPersistenceLock(block: suspend () -> T): T =
        MUTEX.withLock { block() }

    /**
     * Finds the conversation whose participants are exactly the normalized
     * form of [address], or creates it when absent.
     *
     * Single-participant threads only: the lookup key is
     * [PhoneNumbers.conversationKey] of [address] — one participant, no
     * delimiter. On an exact-key miss, existing rows are scanned with
     * [PhoneNumbers.matches] so `5551234567` and `+15551234567` share a
     * thread. On a total miss the conversation is upserted with a wall-clock
     * id and its id returned.
     */
    suspend fun findOrCreateConversation(dao: ConversationDao, address: String): Long =
        MUTEX.withLock { findOrCreateConversationLocked(dao, address) }

    /**
     * The unlocked find-or-create body — callers must already hold [MUTEX]
     * (the Mutex is not reentrant, so public entry points never nest it).
     *
     * The conversation KEY is the normalized address; callers persisting
     * message rows keep the raw delivered address on the row itself.
     */
    internal suspend fun findOrCreateConversationLocked(
        dao: ConversationDao,
        address: String,
    ): Long {
        val key = PhoneNumbers.conversationKey(address)
        dao.getByParticipants(key)?.let { return it.id }
        dao.getAll().firstOrNull { existing ->
            existing.participantAddresses.split('\u0001')
                .filter { it.isNotBlank() }
                .any { PhoneNumbers.matches(it, address) }
        }?.let { return it.id }
        val storedKey = key.ifEmpty { address.trim() }
        val conversation = ConversationEntity(
            id = freshConversationId(dao),
            participantAddresses = storedKey,
        )
        dao.upsert(conversation)
        return conversation.id
    }

    /**
     * Wall-clock id, bumped past any existing row so two deliveries landing in
     * the same millisecond can never collide. Callers must hold [MUTEX] (all
     * public entry points do): the bump is check-then-act and is only safe
     * when serialized.
     */
    private suspend fun freshConversationId(dao: ConversationDao): Long {
        var id = System.currentTimeMillis()
        while (dao.getById(id) != null) id += 1
        return id
    }

    /**
     * [freshConversationId]'s counterpart over the messages table. Same locking contract.
     * Internal so [OutboundStore] allocates outgoing ids under exactly the same
     * bump-past scheme (and the same mutex, via [withPersistenceLock]).
     */
    internal suspend fun freshMessageId(messages: MessageDao): Long {
        var id = System.currentTimeMillis()
        while (messages.getById(id) != null) id += 1
        return id
    }

    /**
     * Persists an inbound SMS as an unread incoming row in the sender's
     * (find-or-created) conversation and returns the message id.
     *
     * The row's `senderAddress` keeps [address] exactly as delivered — only
     * the conversation KEY is normalized (see the class KDoc), never the
     * per-message display address.
     *
     * `isRead = false` is deliberate: the unread-count derivation reads the
     * persisted `isRead` flags (`Message.isUnread`), so an inbound delivery
     * lights up the unread badge without any separate counter write. `sent`
     * is always `true` for incoming rows — the pending-send flag exists only
     * for outgoing messages (`RebootReconcile` re-drives those).
     */
    /**
     * Surfaces [conversationId] in the launcher's inbox: clears archive and
     * quarantine. New delivered activity (or an outgoing send) must never stay
     * silently hidden. Quarantine holds skip this on purpose.
     */
    internal suspend fun surfaceConversation(dao: ConversationDao, conversationId: Long) {
        dao.getById(conversationId)?.let { entity ->
            if (entity.isArchived || entity.isQuarantined) {
                dao.update(entity.copy(isArchived = false, isQuarantined = false))
            }
        }
    }

    internal suspend fun markQuarantined(dao: ConversationDao, conversationId: Long) {
        dao.getById(conversationId)?.let { entity ->
            if (!entity.isQuarantined) {
                dao.update(entity.copy(isQuarantined = true))
            }
        }
    }

    suspend fun persistInboundSms(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        body: String,
        dateMillis: Long,
        quarantined: Boolean = false,
    ): Long = MUTEX.withLock {
        val conversationId = findOrCreateConversationLocked(conversations, address)
        if (quarantined) {
            markQuarantined(conversations, conversationId)
        } else {
            // New activity unarchives / releases a hold: an archived or
            // quarantined thread receiving a delivered message must surface
            // in the launcher's list again, never stay silently hidden.
            surfaceConversation(conversations, conversationId)
        }
        val message = MessageEntity(
            id = freshMessageId(messages),
            conversationId = conversationId,
            direction = MessageDirection.INCOMING.name,
            transport = MessageTransport.SMS.name,
            body = body,
            timestampMillis = dateMillis,
            senderAddress = address,
            isRead = false,
            sent = true,
        )
        messages.upsert(message)
        message.id
    }

    /**
     * Persists an inbound MMS row for the sender's conversation and returns
     * the message id. The body starts as `[Photo]`; the deliver receiver then
     * fetches the PDU. A photo stays collapsed until the operator taps it.
     */
    suspend fun persistInboundMmsMetadata(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        dateMillis: Long,
        contentLocation: String? = null,
        quarantined: Boolean = false,
    ): Long = MUTEX.withLock {
        val conversationId = findOrCreateConversationLocked(conversations, address)
        if (quarantined) {
            markQuarantined(conversations, conversationId)
        } else {
            surfaceConversation(conversations, conversationId)
        }
        val message = MessageEntity(
            id = freshMessageId(messages),
            conversationId = conversationId,
            direction = MessageDirection.INCOMING.name,
            transport = MessageTransport.MMS.name,
            body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
            timestampMillis = dateMillis,
            senderAddress = address,
            isRead = false,
            sent = true,
            contentLocation = contentLocation,
        )
        messages.upsert(message)
        message.id
    }
}
