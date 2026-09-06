package com.piercingxx.txxt.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [QuarantineStore]: persist unread, deliver releases to
 * the inbox, delete removes the thread. Fake DAOs — no Room.
 */
class QuarantineStoreTest {

    private class FakeConversationDao : ConversationDao {
        val rows = LinkedHashMap<Long, ConversationEntity>()
        override suspend fun upsert(conversation: ConversationEntity) {
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
        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(rows.values.filter { it.conversationId == conversationId })
        override suspend fun getAll(): List<MessageEntity> = rows.values.toList()
        override fun observeAll(): Flow<List<MessageEntity>> = flowOf(rows.values.toList())
        override suspend fun markConversationRead(conversationId: Long) = Unit
        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
        override suspend fun markSent(id: Long) = Unit
        override suspend fun deleteForConversation(conversationId: Long) {
            rows.values.filter { it.conversationId == conversationId }.forEach { rows.remove(it.id) }
        }
    }

    @Test
    fun `persistInboundSms holds the sender unread and flagged`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        QuarantineStore.persistInboundSms(
            conversations, messages, "+15551112222", "spam?", 1_700_000_000_000L,
        )
        val conversation = conversations.rows.values.single()
        assertTrue(conversation.isQuarantined)
        assertEquals(false, messages.rows.values.single().isRead)
        assertEquals("15551112222", QuarantineStore.senderAddress(conversation))
    }

    @Test
    fun `deliver releases the thread into the inbox`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        QuarantineStore.persistInboundSms(
            conversations, messages, "+15551112222", "spam?", 1_700_000_000_000L,
        )
        val id = conversations.rows.keys.single()
        QuarantineStore.deliver(conversations, id)
        assertFalse(conversations.rows.getValue(id).isQuarantined)
        assertEquals(1, messages.rows.size)
    }

    @Test
    fun `delete removes the thread and its messages`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        QuarantineStore.persistInboundSms(
            conversations, messages, "+15551112222", "spam?", 1_700_000_000_000L,
        )
        val id = conversations.rows.keys.single()
        QuarantineStore.delete(conversations, messages, id)
        assertTrue(conversations.rows.isEmpty())
        assertTrue(messages.rows.isEmpty())
    }
}
