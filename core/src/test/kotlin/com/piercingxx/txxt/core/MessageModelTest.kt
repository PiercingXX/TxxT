package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageModelTest {

    private fun message(
        id: Long,
        conversationId: Long = 1L,
        direction: MessageDirection = MessageDirection.INCOMING,
        transport: MessageTransport = MessageTransport.SMS,
        body: String = "hello",
        timestampMillis: Long = id * 1000L,
        senderAddress: String? = "+15550001111",
        isRead: Boolean = false,
    ) = Message(
        id = id,
        conversationId = conversationId,
        direction = direction,
        transport = transport,
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = senderAddress,
        isRead = isRead,
    )

    @Test
    fun `incoming unread message reports unread`() {
        val m = message(id = 1L)
        assertTrue(m.isUnread)
    }

    @Test
    fun `incoming read message reports read`() {
        val m = message(id = 1L, isRead = true)
        assertFalse(m.isUnread)
    }

    @Test
    fun `outgoing message is never unread`() {
        val m = message(id = 1L, direction = MessageDirection.OUTGOING, isRead = false)
        assertFalse(m.isUnread)
    }

    @Test
    fun `message carries transport and sender`() {
        val m = message(id = 2L, transport = MessageTransport.MMS, senderAddress = "+15550002222")
        assertEquals(MessageTransport.MMS, m.transport)
        assertEquals("+15550002222", m.senderAddress)
    }

    @Test
    fun `outgoing message has no sender address by default`() {
        val m = message(id = 3L, direction = MessageDirection.OUTGOING, senderAddress = null)
        assertNull(m.senderAddress)
    }

    @Test
    fun `conversation latest timestamp derives from the newest message`() {
        val older = message(id = 1L, timestampMillis = 1000L)
        val newer = message(id = 2L, timestampMillis = 5000L)
        val conv = Conversation(id = 1L, participantAddresses = setOf("+15550001111"), messages = listOf(older, newer))
        assertEquals(5000L, conv.latestTimestampMillis)
    }

    @Test
    fun `conversation latest message is the newest by timestamp`() {
        val older = message(id = 1L, timestampMillis = 1000L)
        val newer = message(id = 2L, timestampMillis = 3000L)
        val conv = Conversation(id = 1L, participantAddresses = setOf("+15550001111"), messages = listOf(older, newer))
        assertEquals(newer, conv.latestMessage)
        assertEquals(3000L, conv.latestTimestampMillis)
    }

    @Test
    fun `conversation unread count counts only unread incoming`() {
        val readIn = message(id = 1L, isRead = true)
        val unreadIn = message(id = 2L, isRead = false)
        val outgoing = message(id = 3L, direction = MessageDirection.OUTGOING, isRead = false)
        val conv = Conversation(
            id = 1L,
            participantAddresses = setOf("+15550001111"),
            messages = listOf(readIn, unreadIn, outgoing),
        )
        assertEquals(1, conv.unreadCount)
        assertTrue(conv.hasUnread)
    }

    @Test
    fun `empty conversation has no unread and no latest message`() {
        val conv = Conversation(id = 1L, participantAddresses = setOf("+15550001111"))
        assertEquals(0, conv.unreadCount)
        assertFalse(conv.hasUnread)
        assertNull(conv.latestMessage)
        assertNull(conv.latestTimestampMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `conversation rejects a message from another thread`() {
        val foreign = message(id = 9L, conversationId = 99L)
        Conversation(id = 1L, participantAddresses = setOf("+15550001111"), messages = listOf(foreign))
    }

    @Test
    fun `empty inbound MMS and the MMS placeholder are unshown stubs`() {
        val empty = message(id = 1L, transport = MessageTransport.MMS, body = "")
        val placeholder = message(id = 2L, transport = MessageTransport.MMS, body = "[MMS]")
        val caption = message(id = 3L, transport = MessageTransport.MMS, body = "see attached")
        val sms = message(id = 4L, body = "")
        assertTrue(empty.isUnshownInboundMms)
        assertTrue(placeholder.isUnshownInboundMms)
        assertFalse(caption.isUnshownInboundMms)
        assertFalse(sms.isUnshownInboundMms)
    }
}