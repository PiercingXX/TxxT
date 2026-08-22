package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport

/**
 * Restores a backup payload into the app's storage idempotently (T5).
 *
 * The backup model ([BackupData]) carries messages keyed by `threadId`; the Room
 * schema stores messages under a `conversationId` foreign key. [plan] maps the
 * backup deterministically onto the storage entities:
 *   - a backup `threadId` becomes the [ConversationEntity.id];
 *   - a backup message `id` becomes the [MessageEntity.id];
 *   - every backup message is stored as an incoming SMS with `isRead = true`
 *     and `sent = true` (the backup format carries no direction/read/sent
 *     fields, so there is no information to preserve for those flags).
 *
 * Restore is **idempotent** because [plan] is a pure function of the payload and
 * [restore] persists through REPLACE-upsert seams: re-running it with the same
 * payload (or re-running after a partial failure) overwrites the exact same
 * primary keys and yields exactly the same final state — never duplicates. The
 * component is pure over its two seams ([upsertConversation], [upsertMessage]),
 * so the idempotency logic is JVM-testable without a device; the running call
 * site ([RoomRestoreService]) supplies the real Room DAO upserts, which use
 * `OnConflictStrategy.REPLACE`.
 */
class RestoreService(
    /** Persists a conversation, replacing any row with the same primary key. */
    private val upsertConversation: suspend (ConversationEntity) -> Unit,
    /** Persists a message, replacing any row with the same primary key. */
    private val upsertMessage: suspend (MessageEntity) -> Unit,
) {

    /** The deterministic set of storage entities a [BackupData] restores to. */
    data class RestorePlan(
        val conversations: List<ConversationEntity>,
        val messages: List<MessageEntity>,
    )

    /**
     * Maps [data] onto the storage entities. Pure and deterministic: the same
     * payload always yields the same plan, so applying it twice is a no-op on
     * the final state (the second pass overwrites identical primary keys).
     */
    fun plan(data: BackupData): RestorePlan {
        // Group backup messages by their thread so each thread becomes exactly
        // one conversation. Order is preserved from the payload, keeping the
        // mapping deterministic.
        val byThread = data.messages.groupBy { it.threadId }

        val conversations = byThread.map { (threadId, messages) ->
            // The conversation's participant address is the address of its first
            // message; the backup groups a thread's messages under one address.
            ConversationEntity(
                id = threadId,
                participantAddresses = messages.first().address,
            )
        }

        val stored = data.messages.map { backup ->
            MessageEntity(
                id = backup.id,
                conversationId = backup.threadId,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.SMS.name,
                body = backup.body,
                timestampMillis = backup.date,
                senderAddress = backup.address,
                isRead = true,
                sent = true,
            )
        }

        return RestorePlan(conversations = conversations, messages = stored)
    }

    /**
     * Restores [data] into the store. Idempotent: [plan] is a pure function of
     * the payload and every upsert replaces the same primary keys, so calling
     * [restore] twice (or re-running after a partial failure) leaves the store
     * in exactly the same state. Returns the plan that was applied.
     */
    suspend fun restore(data: BackupData): RestorePlan {
        val plan = plan(data)
        plan.conversations.forEach { upsertConversation(it) }
        plan.messages.forEach { upsertMessage(it) }
        return plan
    }
}

/**
 * The T5 wire-in: builds the Room database and supplies the real DAO upserts so
 * [RestoreService] persists a parsed backup into the app's storage. The DAO
 * upserts use `OnConflictStrategy.REPLACE`, which is what makes a re-run of a
 * restore overwrite existing rows instead of duplicating them.
 *
 * A restore is triggered by calling [restore] on [service] with a [BackupData]
 * parsed from the backup JSON (e.g. via [BackupJson.deserialize]).
 */
class RoomRestoreService(private val database: TxxTDatabase) {

    /** The idempotent restore service backed by this database's DAOs. */
    val service: RestoreService by lazy {
        RestoreService(
            upsertConversation = { database.conversationDao().upsert(it) },
            upsertMessage = { database.messageDao().upsert(it) },
        )
    }

    /** Restores [data] into this database. See [RestoreService.restore]. */
    suspend fun restore(data: BackupData): RestoreService.RestorePlan = service.restore(data)
}