package com.piercingxx.txxt.ui

import com.piercingxx.txxt.data.ConversationDao
import com.piercingxx.txxt.data.ConversationEntity
import com.piercingxx.txxt.data.MessageDao
import com.piercingxx.txxt.data.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour-verifies [ConversationListLoader] over hand-rolled fake DAOs (the
 * [InboundStoreTest] precedent — the DAOs are interfaces, no Robolectric, no
 * Room): the observe-everything → group → map → sort pipeline that feeds the
 * launcher's live conversation list. Proves messages land in their own
 * conversation in chronological order, archived conversations are dropped
 * (hidden, not deleted), pinned conversations lead, and unpinned conversations
 * follow newest-activity-first.
 */
class ConversationListLoaderTest {

    private class FakeConversationDao(
        val all: MutableStateFlow<List<ConversationEntity>>,
    ) : ConversationDao {
        override suspend fun upsert(conversation: ConversationEntity) = Unit
        override suspend fun upsertAll(conversations: List<ConversationEntity>) = Unit
        override suspend fun update(conversation: ConversationEntity) = Unit
        override suspend fun delete(conversation: ConversationEntity) = Unit
        override suspend fun getById(id: Long): ConversationEntity? = null
        override suspend fun getByParticipants(joined: String): ConversationEntity? = null
        override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(null)
        override fun observeAll(): Flow<List<ConversationEntity>> = all
        override suspend fun getAll(): List<ConversationEntity> = all.value
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
    }

    private class FakeMessageDao(
        val all: MutableStateFlow<List<MessageEntity>>,
    ) : MessageDao {
        override suspend fun upsert(message: MessageEntity) = Unit
        override suspend fun upsertAll(messages: List<MessageEntity>) = Unit
        override suspend fun update(message: MessageEntity) = Unit
        override suspend fun delete(message: MessageEntity) = Unit
        override suspend fun getById(id: Long): MessageEntity? = null
        override suspend fun getForConversation(conversationId: Long): List<MessageEntity> =
            emptyList()
        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(emptyList())
        override suspend fun getAll(): List<MessageEntity> = all.value
        override fun observeAll(): Flow<List<MessageEntity>> = all
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun markSent(id: Long) = Unit
        override suspend fun markConversationRead(conversationId: Long) = Unit
        override suspend fun deleteForConversation(conversationId: Long) = Unit
    }

    private fun conversation(
        id: Long,
        address: String = "+1555000$id",
        isArchived: Boolean = false,
        isPinned: Boolean = false,
    ): ConversationEntity = ConversationEntity(
        id = id,
        participantAddresses = address,
        isArchived = isArchived,
        isPinned = isPinned,
    )

    private fun message(
        id: Long,
        conversationId: Long,
        timestampMillis: Long,
        body: String = "body-$id",
    ): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        direction = "INCOMING",
        transport = "SMS",
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = "+15550001111",
        isRead = false,
        sent = true,
    )

    private fun loaderOver(
        conversations: List<ConversationEntity>,
        messages: List<MessageEntity>,
    ): ConversationListLoader = ConversationListLoader(
        FakeConversationDao(MutableStateFlow(conversations)),
        FakeMessageDao(MutableStateFlow(messages)),
    )

    @Test
    fun `messages land in their own conversation in chronological order`() = runBlocking {
        val loaded = loaderOver(
            conversations = listOf(conversation(1L), conversation(2L)),
            messages = listOf(
                message(10L, conversationId = 1L, timestampMillis = 3_000L),
                message(11L, conversationId = 1L, timestampMillis = 1_000L),
                message(12L, conversationId = 2L, timestampMillis = 2_000L),
            ),
        ).conversations().first()

        val one = loaded.first { it.id == 1L }
        assertEquals(listOf(11L, 10L), one.messages.map { it.id })
        assertEquals(listOf(12L), loaded.first { it.id == 2L }.messages.map { it.id })
    }

    @Test
    fun `archived conversations are dropped from the list`() = runBlocking {
        val loaded = loaderOver(
            conversations = listOf(conversation(1L), conversation(2L, isArchived = true)),
            messages = emptyList(),
        ).conversations().first()

        assertEquals(listOf(1L), loaded.map { it.id })
    }

    @Test
    fun `pinned conversations lead, the rest follow newest-first`() = runBlocking {
        val loaded = loaderOver(
            conversations = listOf(
                conversation(1L),
                conversation(2L, isPinned = true),
                conversation(3L),
            ),
            messages = listOf(
                message(10L, conversationId = 1L, timestampMillis = 1_000L),
                message(11L, conversationId = 2L, timestampMillis = 2_000L),
                message(12L, conversationId = 3L, timestampMillis = 9_000L),
            ),
        ).conversations().first()

        assertEquals(listOf(2L, 3L, 1L), loaded.map { it.id })
    }

    @Test
    fun `inbound MMS stubs are not attached as conversation messages`() = runBlocking {
        val loaded = loaderOver(
            conversations = listOf(conversation(1L)),
            messages = listOf(
                message(10L, conversationId = 1L, timestampMillis = 1_000L, body = "hello"),
                MessageEntity(
                    id = 11L,
                    conversationId = 1L,
                    direction = "INCOMING",
                    transport = "MMS",
                    body = "",
                    timestampMillis = 2_000L,
                    senderAddress = "+15550001111",
                    isRead = false,
                    sent = true,
                ),
                MessageEntity(
                    id = 12L,
                    conversationId = 1L,
                    direction = "INCOMING",
                    transport = "MMS",
                    body = "[MMS]",
                    timestampMillis = 3_000L,
                    senderAddress = "+15550001111",
                    isRead = false,
                    sent = true,
                ),
            ),
        ).conversations().first()

        assertEquals(listOf(10L), loaded.single().messages.map { it.id })
        assertEquals("hello", loaded.single().latestMessage?.body)
        assertEquals(1, loaded.single().unreadCount)
    }

    @Test
    fun `inbound Photo rows stay on the conversation`() = runBlocking {
        val loaded = loaderOver(
            conversations = listOf(conversation(1L)),
            messages = listOf(
                MessageEntity(
                    id = 20L,
                    conversationId = 1L,
                    direction = "INCOMING",
                    transport = "MMS",
                    body = "[Photo]",
                    timestampMillis = 4_000L,
                    senderAddress = "+15550001111",
                    isRead = false,
                    sent = true,
                    mediaPath = "/tmp/20.jpg",
                ),
            ),
        ).conversations().first()

        assertEquals(listOf(20L), loaded.single().messages.map { it.id })
        assertEquals("[Photo]", loaded.single().latestMessage?.body)
    }
}
