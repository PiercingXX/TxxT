package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationListTest {

    private fun message(
        id: Long,
        conversationId: Long,
        direction: MessageDirection = MessageDirection.INCOMING,
        isRead: Boolean = false,
        timestampMillis: Long = id * 1000L,
    ) = Message(
        id = id,
        conversationId = conversationId,
        direction = direction,
        transport = MessageTransport.SMS,
        body = "hello",
        timestampMillis = timestampMillis,
        senderAddress = "+15550001111",
        isRead = isRead,
    )

    private fun conversation(
        id: Long,
        vararg messages: Message,
    ) = Conversation(
        id = id,
        participantAddresses = setOf("+15550001111"),
        messages = messages.toList(),
    )

    private fun ids(conversations: List<Conversation>): List<Long> =
        conversations.map { it.id }

    @Test
    fun `unread first surfaces conversations with unread messages before read ones`() {
        val read = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 1000L, isRead = true),
        )
        val unread = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 2000L, isRead = false),
        )
        val ordered = ConversationList.sorted(listOf(read, unread), ConversationSortOrder.UNREAD_FIRST)
        assertEquals(listOf(2L, 1L), ids(ordered))
    }

    @Test
    fun `unread first orders by unread count then recency within a group`() {
        val oneUnread = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 3000L, isRead = false),
        )
        val twoUnread = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 1000L, isRead = false),
            message(id = 3L, conversationId = 2L, timestampMillis = 2000L, isRead = false),
        )
        val ordered = ConversationList.sorted(listOf(oneUnread, twoUnread), ConversationSortOrder.UNREAD_FIRST)
        assertEquals(listOf(2L, 1L), ids(ordered))
    }

    @Test
    fun `newest first ignores unread state and orders by recency`() {
        val older = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 1000L, isRead = false),
        )
        val newer = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 2000L, isRead = false),
        )
        val ordered = ConversationList.sorted(listOf(older, newer), ConversationSortOrder.NEWEST_FIRST)
        assertEquals(listOf(2L, 1L), ids(ordered))
    }

    @Test
    fun `oldest first orders by recency ascending`() {
        val older = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 1000L, isRead = false),
        )
        val newer = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 2000L, isRead = false),
        )
        val ordered = ConversationList.sorted(listOf(older, newer), ConversationSortOrder.OLDEST_FIRST)
        assertEquals(listOf(1L, 2L), ids(ordered))
    }

    @Test
    fun `pinned first defaults to unread then recency`() {
        val read = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 3000L, isRead = true),
        )
        val unread = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 1000L, isRead = false),
        )
        val ordered = ConversationList.sorted(listOf(read, unread))
        assertEquals(listOf(2L, 1L), ids(ordered))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<Long>(), ids(ConversationList.sorted(emptyList())))
    }
}