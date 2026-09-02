package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [OutboundStore] over hand-rolled fake DAOs (the DAOs are
 * interfaces, so the used methods are directly implementable — no Robolectric,
 * no Room; the same fakes as [InboundStoreTest]). Proves the outgoing row
 * shape (OUTGOING / read / **sent=false** / `senderAddress = null`), the id
 * round-trip (the returned id is the persisted row's id), and that two quick
 * replies to one address share ONE conversation keyed by the normalized
 * address.
 */
class OutboundStoreTest {

    /** REPLACE-by-primary-key conversation DAO fake (Room upsert semantics). */
    private class FakeConversationDao : ConversationDao {
        val rows = LinkedHashMap<Long, ConversationEntity>()
        var upsertCount = 0

        override suspend fun upsert(conversation: ConversationEntity) {
            upsertCount += 1
            rows[conversation.id] = conversation
        }

        override suspend fun upsertAll(conversations: List<ConversationEntity>) =
            conversations.forEach { upsert(it) }

        override suspend fun update(conversation: ConversationEntity) {
            rows[conversation.id] = conversation
        }

        override suspend fun delete(conversation: ConversationEntity) {
            rows.remove(conversation.id)
        }

        override suspend fun getById(id: Long): ConversationEntity? = rows[id]

        override suspend fun getByParticipants(joined: String): ConversationEntity? =
            rows.values.firstOrNull { it.participantAddresses == joined }

        override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(rows[id])

        override fun observeAll(): Flow<List<ConversationEntity>> = flowOf(rows.values.toList())

        override suspend fun getAll(): List<ConversationEntity> = rows.values.toList()

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override suspend fun unarchive(id: Long) {
            rows[id]?.let { rows[id] = it.copy(isArchived = false) }
        }
    }

    /** REPLACE-by-primary-key message DAO fake (Room upsert semantics). */
    private class FakeMessageDao : MessageDao {
        val rows = LinkedHashMap<Long, MessageEntity>()

        override suspend fun upsert(message: MessageEntity) {
            rows[message.id] = message
        }

        override suspend fun upsertAll(messages: List<MessageEntity>) =
            messages.forEach { upsert(it) }

        override suspend fun update(message: MessageEntity) {
            rows[message.id] = message
        }

        override suspend fun delete(message: MessageEntity) {
            rows.remove(message.id)
        }

        override suspend fun getById(id: Long): MessageEntity? = rows[id]

        override suspend fun getForConversation(conversationId: Long): List<MessageEntity> =
            rows.values.filter { it.conversationId == conversationId }
                .sortedBy { it.timestampMillis }

        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(
                rows.values.filter { it.conversationId == conversationId }
                    .sortedBy { it.timestampMillis },
            )

        override suspend fun getAll(): List<MessageEntity> = rows.values.toList()

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override suspend fun markSent(id: Long) {
            rows[id]?.let { rows[id] = it.copy(sent = true) }
        }

        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<MessageEntity>> =
            kotlinx.coroutines.flow.flowOf(rows.values.toList())

        override suspend fun markConversationRead(conversationId: Long) {
            rows.values.filter { it.conversationId == conversationId && it.direction == "INCOMING" }
                .forEach { rows[it.id] = it.copy(isRead = true) }
        }

        override suspend fun deleteForConversation(conversationId: Long) {
            rows.values.filter { it.conversationId == conversationId }.forEach { rows.remove(it.id) }
        }
    }

    // ---- persistOutgoingSms ----

    @Test
    fun `persistOutgoingSms inserts an outgoing read unsent row with a null sender`() =
        runBlocking {
            val conversations = FakeConversationDao()
            val messages = FakeMessageDao()
            val before = System.currentTimeMillis()

            val messageId = OutboundStore.persistOutgoingSms(
                conversations, messages, "+15551234567", "quick reply",
            )
            val after = System.currentTimeMillis()

            val stored = messages.rows.getValue(messageId)
            assertEquals(
                "the returned id is the persisted row's id",
                messageId,
                stored.id,
            )
            assertEquals(
                conversations.rows.keys.single(),
                stored.conversationId,
            )
            assertEquals(MessageDirection.OUTGOING.name, stored.direction)
            assertEquals(MessageTransport.SMS.name, stored.transport)
            assertEquals("quick reply", stored.body)
            assertTrue("wall-clock timestamp", stored.timestampMillis in before..after)
            // Model contract: null for an outgoing message — the destination
            // lives on the conversation's participants.
            assertNull(stored.senderAddress)
            assertEquals(true, stored.isRead)
            // THE contract: sent=false marks the pending send that
            // RebootReconcile re-drives after an interrupted dispatch.
            assertEquals(false, stored.sent)
        }

    @Test
    fun `two quick replies to one address share one normalized conversation`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()

        val first = OutboundStore.persistOutgoingSms(
            conversations, messages, "+15551234567", "first",
        )
        val second = OutboundStore.persistOutgoingSms(
            conversations, messages, "15551234567", "second",
        )

        // Different formats of one recipient → one thread, normalized key.
        assertEquals(1, conversations.rows.size)
        assertEquals("15551234567", conversations.rows.values.single().participantAddresses)
        assertEquals(2, messages.rows.size)
        // Distinct ids — the bump-past scheme never collides two rows.
        assertTrue(first != second)
        assertEquals(
            messages.rows.getValue(first).conversationId,
            messages.rows.getValue(second).conversationId,
        )
        assertEquals("first", messages.rows.getValue(first).body)
        assertEquals("second", messages.rows.getValue(second).body)
    }

    @Test
    fun `a wall-clock message-id collision is bumped past instead of overwriting`() = runBlocking {
        val now = System.currentTimeMillis()
        val conversations = FakeConversationDao().apply {
            rows[now] = ConversationEntity(id = now, participantAddresses = "15551234567")
        }
        val messages = FakeMessageDao().apply {
            rows[now] = MessageEntity(
                id = now,
                conversationId = now,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.SMS.name,
                body = "pre-existing",
                timestampMillis = now,
                senderAddress = "+15551234567",
            )
        }

        val messageId = OutboundStore.persistOutgoingSms(
            conversations, messages, "+15551234567", "reply",
        )

        assertTrue("created id must not collide with the pre-seeded row", messageId != now)
        assertEquals(2, messages.rows.size)
        assertEquals("pre-existing", messages.rows.getValue(now).body)
        assertEquals("reply", messages.rows.getValue(messageId).body)
    }
    @Test
    fun `sending into an archived conversation unarchives it`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        val conversationId = InboundStore.findOrCreateConversation(conversations, "+15551234567")
        conversations.rows[conversationId] =
            conversations.rows.getValue(conversationId).copy(isArchived = true)

        OutboundStore.persistOutgoingSms(conversations, messages, "+15551234567", "hi")

        // The send is new activity: the thread must resurface in the list.
        assertEquals(false, conversations.rows.getValue(conversationId).isArchived)
    }

    // ---- persistOutgoingMms (photo attachments) ----

    @Test
    fun `persistOutgoingMms inserts an MMS-transport pending row`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()

        val messageId = OutboundStore.persistOutgoingMms(conversations, messages, "+15551234567")

        val stored = messages.rows.getValue(messageId)
        // The transport is the load-bearing field: RebootReconcile refuses to
        // re-drive an MMS row. The body starts as [Photo] so sent photos
        // toggle the same way inbound ones do.
        assertEquals(MessageTransport.MMS.name, stored.transport)
        assertEquals(MessageDirection.OUTGOING.name, stored.direction)
        assertEquals("[Photo]", stored.body)
        assertNull(stored.senderAddress)
        assertEquals(true, stored.isRead)
        assertEquals("photo sends are pending until dispatched", false, stored.sent)
    }

    @Test
    fun `a photo and a text send share one conversation and never collide`() = runBlocking {
        // A caption travels as its own SMS row alongside the photo's MMS row
        // (PhotoAttachment.plan), so both must land in the same thread with
        // distinct ids.
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()

        val textId = OutboundStore.persistOutgoingSms(
            conversations, messages, "+15551234567", "look at this",
        )
        val photoId = OutboundStore.persistOutgoingMms(conversations, messages, "+15551234567")

        assertEquals(1, conversations.rows.size)
        assertTrue("the two rows must not share an id", textId != photoId)
        assertEquals(
            messages.rows.getValue(textId).conversationId,
            messages.rows.getValue(photoId).conversationId,
        )
        assertEquals(MessageTransport.SMS.name, messages.rows.getValue(textId).transport)
        assertEquals(MessageTransport.MMS.name, messages.rows.getValue(photoId).transport)
    }

    @Test
    fun `sending a photo into an archived conversation unarchives it too`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        val conversationId = InboundStore.findOrCreateConversation(conversations, "+15551234567")
        conversations.rows[conversationId] =
            conversations.rows.getValue(conversationId).copy(isArchived = true)

        OutboundStore.persistOutgoingMms(conversations, messages, "+15551234567")

        assertEquals(false, conversations.rows.getValue(conversationId).isArchived)
    }
}
