package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.BackupMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the T5 idempotent backup restore (RestoreService.kt).
 *
 * `RestoreService.restore` is the seam a restore consumes: it maps a parsed
 * [BackupData] deterministically onto conversation/message entities and persists
 * them through REPLACE-upsert seams. The tests drive the pure [RestoreService]
 * directly over an in-memory store (whose upsert replaces by primary key, the
 * same semantics as the Room DAO `OnConflictStrategy.REPLACE`), asserting the
 * idempotency contract: restoring the same payload twice — or re-running after a
 * partial failure — leaves the store in exactly the same state, never duplicating
 * a conversation or message. The `RoomRestoreService` wire-in is locked by a
 * source-reading assertion (the established `RebootReconcileTest` pattern).
 */
class RestoreIdempotencyTest {

    /** In-memory store whose upsert replaces by primary key, mirroring the DAOs' REPLACE. */
    private class InMemoryStore {
        val conversations = LinkedHashMap<Long, ConversationEntity>()
        val messages = LinkedHashMap<Long, MessageEntity>()

        fun upsertConversation(c: ConversationEntity) { conversations[c.id] = c }
        fun upsertMessage(m: MessageEntity) { messages[m.id] = m }
    }

    private fun backupMessage(id: Long, threadId: Long, address: String = "+15551234567", body: String = "hello") =
        BackupMessage(id = id, threadId = threadId, address = address, body = body, date = 1700000000000L + id)

    private fun sampleData() = BackupData(
        version = BACKUP_VERSION,
        messages = listOf(
            backupMessage(1L, 10L),
            backupMessage(2L, 10L, body = "world"),
            backupMessage(3L, 20L, address = "+15559998888", body = "other thread"),
        ),
        settings = mapOf("quietHoursStart" to "22:00"),
        blocklist = listOf("spam"),
        starred = listOf("+15551234567"),
    )

    private fun restore(data: BackupData, store: InMemoryStore): RestoreService.RestorePlan {
        val service = RestoreService(
            upsertConversation = { store.upsertConversation(it) },
            upsertMessage = { store.upsertMessage(it) },
        )
        return runBlocking { service.restore(data) }
    }

    // ---- plan is deterministic ----

    @Test
    fun `plan is a pure function of the payload`() {
        val service = RestoreService(
            upsertConversation = { },
            upsertMessage = { },
        )
        val data = sampleData()
        assertEquals(service.plan(data), service.plan(data))
    }

    @Test
    fun `plan maps each thread to one conversation and each message to one entity`() {
        val service = RestoreService(
            upsertConversation = { },
            upsertMessage = { },
        )
        val plan = service.plan(sampleData())
        assertEquals(setOf(10L, 20L), plan.conversations.map { it.id }.toSet())
        assertEquals(3, plan.messages.size)
    }

    // ---- restore is idempotent ----

    @Test
    fun `restoring the same payload twice yields the same final state`() {
        val store = InMemoryStore()
        restore(sampleData(), store)
        val firstConversations = store.conversations.toMap()
        val firstMessages = store.messages.toMap()

        restore(sampleData(), store)

        // No duplicates: the same primary keys are overwritten, not appended.
        assertEquals(firstConversations, store.conversations)
        assertEquals(firstMessages, store.messages)
        assertEquals(2, store.conversations.size)
        assertEquals(3, store.messages.size)
    }

    @Test
    fun `restoring an empty payload is a no-op and stays a no-op`() {
        val store = InMemoryStore()
        val empty = BackupData(
            version = BACKUP_VERSION,
            messages = emptyList(),
            settings = emptyMap(),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        restore(empty, store)
        restore(empty, store)
        assertTrue(store.conversations.isEmpty())
        assertTrue(store.messages.isEmpty())
    }

    @Test
    fun `re-running after a partial restore converges to the same state`() {
        // Simulate a first attempt that only persisted the conversations before
        // being interrupted: the store already holds the conversations.
        val store = InMemoryStore()
        val data = sampleData()
        val service = RestoreService(
            upsertConversation = { store.upsertConversation(it) },
            upsertMessage = { store.upsertMessage(it) },
        )
        runBlocking { service.restore(data) }
        store.messages.clear() // "interrupted" before any message landed

        // A fresh restore must converge to the full state, not duplicate.
        runBlocking { service.restore(data) }

        assertEquals(2, store.conversations.size)
        assertEquals(3, store.messages.size)
        assertEquals(setOf(1L, 2L, 3L), store.messages.keys)
    }

    @Test
    fun `restore preserves the backup message ids and thread ids`() {
        val store = InMemoryStore()
        restore(sampleData(), store)
        assertEquals(setOf(1L, 2L, 3L), store.messages.keys)
        assertEquals(10L, store.messages[1L]!!.conversationId)
        assertEquals(20L, store.messages[3L]!!.conversationId)
    }

    // ---- wire-in: the running restore path reaches RestoreService ----

    @Test
    fun `RoomRestoreService source supplies the real DAO upserts`() {
        val source = sourceText("data/RestoreService.kt")
        assertTrue(
            "RoomRestoreService must construct a RestoreService",
            source.contains("RestoreService("),
        )
        assertTrue(
            "RoomRestoreService must wire the conversation DAO upsert",
            source.contains("database.conversationDao().upsert"),
        )
        assertTrue(
            "RoomRestoreService must wire the message DAO upsert",
            source.contains("database.messageDao().upsert"),
        )
        assertTrue(
            "RoomRestoreService must expose a restore entry point",
            source.contains("suspend fun restore"),
        )
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}