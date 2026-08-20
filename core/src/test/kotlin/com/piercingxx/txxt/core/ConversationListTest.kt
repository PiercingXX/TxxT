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
    fun `unread first orders by the UnreadCount perConversation map`() {
        // The unread-first order must be driven by the counts UnreadCount derives
        // per conversation, not by a hardcoded expectation: compute the expected
        // order from UnreadCount.perConversation and assert ConversationList.sorted
        // reproduces it. This exercises the wiring of the unread subsystem end to end.
        val read = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 3000L, isRead = true),
        )
        val twoUnread = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 1000L, isRead = false),
            message(id = 3L, conversationId = 2L, timestampMillis = 2000L, isRead = false),
        )
        val oneUnread = conversation(
            3L,
            message(id = 4L, conversationId = 3L, timestampMillis = 500L, isRead = false),
        )
        val conversations = listOf(read, twoUnread, oneUnread)
        val byUnread = UnreadCount.perConversation(conversations)
        val ordered = ConversationList.sorted(conversations, ConversationSortOrder.UNREAD_FIRST)
        val expected = conversations.sortedByDescending { byUnread.getValue(it.id) }
        assertEquals(expected.map { it.id }, ids(ordered))
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