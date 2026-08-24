package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
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
 * key is [PhoneNumbers.normalize] of the address (digits-only for phone
 * numbers, trimmed-lowercase for email-gateway addresses), so one
 * correspondent lands on one thread no matter how the carrier or the SENDTO
 * hand-off formatted the number ("+15551234567" and "15551234567" are the same
 * participant); the MESSAGE rows keep the raw delivered address as their
 * `senderAddress` (display fidelity). The normalized key is also a valid SMS
 * destination: bare digits are accepted by `SmsManager`, which is what makes
 * `ThreadActivity.destinationAddress()` (it reads
 * `participantAddresses` to send) safe over normalized keys. Multi-participant
 * MMS threads are deferred until the data model grows a real
 * participant-resolution story.
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
     * [PhoneNumbers.normalize] of [address] — one participant, no delimiter —
     * matching how a 1:1 SMS thread is stored
     * (`ConversationEntity.participantAddresses` holds a delimiter-joined
     * string for multi-participant threads, which this store never writes).
     * On a miss the conversation is upserted with a wall-clock id (the same
     * id scheme every other conversation row uses) and its id returned.
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
        val key = PhoneNumbers.normalize(address)
        dao.getByParticipants(key)?.let { return it.id }
        val conversation = ConversationEntity(
            id = freshConversationId(dao),
            participantAddresses = key,
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
    suspend fun persistInboundSms(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        body: String,
        dateMillis: Long,
    ): Long = MUTEX.withLock {
        val conversationId = findOrCreateConversationLocked(conversations, address)
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
     * Persists inbound MMS **metadata only** (transport MMS, empty body) for
     * the sender's (find-or-created) conversation and returns the message id.
     *
     * The body is never populated here: MMS auto-download stays off
     * (docs/PRIVACY.md §8.1) — remote content is fetched only on explicit tap
     * later, never by the delivery path. The row is what makes the thread list
     * show that something arrived before any content exists on the device.
     */
    suspend fun persistInboundMmsMetadata(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        dateMillis: Long,
    ): Long = MUTEX.withLock {
        val conversationId = findOrCreateConversationLocked(conversations, address)
        val message = MessageEntity(
            id = freshMessageId(messages),
            conversationId = conversationId,
            direction = MessageDirection.INCOMING.name,
            transport = MessageTransport.MMS.name,
            body = "",
            timestampMillis = dateMillis,
            senderAddress = address,
            isRead = false,
            sent = true,
        )
        messages.upsert(message)
        message.id
    }
}
