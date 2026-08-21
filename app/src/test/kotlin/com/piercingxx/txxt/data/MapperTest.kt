package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.ConversationFlags
import com.piercingxx.txxt.core.ConversationSortOrder
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.data.Mappers.toConversation
import com.piercingxx.txxt.data.Mappers.toEntity
import com.piercingxx.txxt.data.Mappers.toEntityPart
import com.piercingxx.txxt.data.Mappers.toFlags
import com.piercingxx.txxt.data.Mappers.toMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {

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

    // ---- Message round-trips ----

    @Test
    fun `message maps to entity and back losslessly`() {
        val m = message(
            id = 7L,
            conversationId = 3L,
            direction = MessageDirection.OUTGOING,
            transport = MessageTransport.MMS,
            body = "see attached",
            timestampMillis = 1234L,
            senderAddress = null,
            isRead = true,
        )
        assertEquals(m, m.toEntity().toMessage())
    }

    @Test
    fun `message entity stores enums as names`() {
        val m = message(id = 1L, direction = MessageDirection.OUTGOING, transport = MessageTransport.MMS)
        val entity = m.toEntity()
        assertEquals("OUTGOING", entity.direction)
        assertEquals("MMS", entity.transport)
    }

    @Test
    fun `message entity default sender and read state map through`() {
        val entity = MessageEntity(
            id = 1L,
            conversationId = 1L,
            direction = "INCOMING",
            transport = "SMS",
            body = "hi",
            timestampMillis = 1000L,
        )
        val m = entity.toMessage()
        assertEquals(MessageDirection.INCOMING, m.direction)
        assertEquals(MessageTransport.SMS, m.transport)
        assertEquals(null, m.senderAddress)
        assertFalse(m.isRead)
    }

    // ---- Conversation round-trips ----

    @Test
    fun `conversation maps to entity and back losslessly`() {
        val conv = Conversation(
            id = 5L,
            participantAddresses = setOf("+15550001111", "+15550002222"),
        )
        val entity = conv.toEntity()
        assertEquals(conv, entity.toConversation())
    }

    @Test
    fun `conversation entity joins and splits participant addresses`() {
        val conv = Conversation(id = 1L, participantAddresses = setOf("+15550001111", "+15550002222", "+15550003333"))
        val entity = conv.toEntity()
        val decoded = entity.toConversation()
        assertEquals(conv.participantAddresses, decoded.participantAddresses)
    }

    @Test
    fun `self-only conversation stores empty address string`() {
        val conv = Conversation(id = 1L, participantAddresses = emptySet())
        val entity = conv.toEntity()
        assertEquals("", entity.participantAddresses)
        assertTrue(entity.toConversation().participantAddresses.isEmpty())
    }

    @Test
    fun `conversation with messages maps to entity plus loaded messages`() {
        val conv = Conversation(
            id = 1L,
            participantAddresses = setOf("+15550001111"),
            messages = listOf(message(id = 1L), message(id = 2L)),
        )
        val entity = conv.toEntity()
        val restored = entity.toConversation(conv.messages)
        assertEquals(conv, restored)
    }

    // ---- ConversationFlags ----

    @Test
    fun `flags map to entity and back losslessly`() {
        val flags = ConversationFlags(
            isPinned = true,
            isArchived = false,
            sortOrder = ConversationSortOrder.UNREAD_FIRST,
        )
        val entity = Conversation(id = 1L, participantAddresses = setOf("+15550001111")).toEntity(flags = flags)
        assertEquals(flags, entity.toFlags())
    }

    @Test
    fun `default flags map to default entity fields`() {
        val entity = Conversation(id = 1L, participantAddresses = setOf("+15550001111")).toEntity()
        assertEquals(false, entity.isPinned)
        assertEquals(false, entity.isArchived)
        assertEquals("PINNED_FIRST", entity.sortOrder)
        assertEquals(ConversationFlags(), entity.toFlags())
    }

    @Test
    fun `flags apply onto an existing entity`() {
        val entity = Conversation(id = 1L, participantAddresses = setOf("+15550001111")).toEntity()
        val flags = ConversationFlags(isPinned = true, isArchived = true, sortOrder = ConversationSortOrder.NEWEST_FIRST)
        val updated = flags.toEntityPart(entity)
        assertEquals(true, updated.isPinned)
        assertEquals(true, updated.isArchived)
        assertEquals("NEWEST_FIRST", updated.sortOrder)
    }

    @Test
    fun `starred flag persists through entity`() {
        val entity = Conversation(id = 1L, participantAddresses = setOf("+15550001111")).toEntity(isStarred = true)
        assertTrue(entity.isStarred)
        assertFalse(Conversation(id = 1L, participantAddresses = setOf("+15550001111")).toEntity().isStarred)
    }
}