package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadCountTest {

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

    private fun conversation(id: Long, vararg messages: Message) =
        Conversation(id = id, participantAddresses = setOf("+15550001111"), messages = messages.toList())

    @Test
    fun `total counts unread across all conversations`() {
        val convA = conversation(
            1L,
            message(id = 1L, conversationId = 1L, isRead = true),
            message(id = 2L, conversationId = 1L, isRead = false),
            message(id = 3L, conversationId = 1L, direction = MessageDirection.OUTGOING, isRead = false),
        )
        val convB = conversation(
            2L,
            message(id = 4L, conversationId = 2L, isRead = false),
            message(id = 5L, conversationId = 2L, isRead = false),
        )
        assertEquals(3, UnreadCount.total(listOf(convA, convB)))
    }

    @Test
    fun `total is zero when nothing is unread`() {
        val conv = conversation(
            1L,
            message(id = 1L, conversationId = 1L, isRead = true),
            message(id = 2L, conversationId = 1L, direction = MessageDirection.OUTGOING, isRead = false),
        )
        assertEquals(0, UnreadCount.total(listOf(conv)))
    }

    @Test
    fun `total is zero for an empty conversation set`() {
        assertEquals(0, UnreadCount.total(emptyList()))
    }

    @Test
    fun `per conversation maps each id to its own unread count`() {
        val convA = conversation(
            1L,
            message(id = 1L, conversationId = 1L, isRead = true),
            message(id = 2L, conversationId = 1L, isRead = false),
        )
        val convB = conversation(2L)
        val byConv = UnreadCount.perConversation(listOf(convA, convB))
        assertEquals(1, byConv[1L])
        assertEquals(0, byConv[2L])
    }

    @Test
    fun `any unread is true only when at least one conversation has unread`() {
        val clean = conversation(
            1L,
            message(id = 1L, conversationId = 1L, isRead = true),
        )
        val dirty = conversation(
            2L,
            message(id = 2L, conversationId = 2L, isRead = false),
        )
        assertFalse(UnreadCount.anyUnread(listOf(clean)))
        assertTrue(UnreadCount.anyUnread(listOf(clean, dirty)))
        assertFalse(UnreadCount.anyUnread(emptyList()))
    }
}