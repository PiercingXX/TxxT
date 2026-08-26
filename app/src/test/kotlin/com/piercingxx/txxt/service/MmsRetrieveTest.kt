package com.piercingxx.txxt.service

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.data.MessageDao
import com.piercingxx.txxt.data.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrieveTest {

    private class FakeMessageDao : MessageDao {
        val rows = LinkedHashMap<Long, MessageEntity>()
        override suspend fun upsert(message: MessageEntity) { rows[message.id] = message }
        override suspend fun upsertAll(messages: List<MessageEntity>) = messages.forEach { upsert(it) }
        override suspend fun update(message: MessageEntity) { rows[message.id] = message }
        override suspend fun delete(message: MessageEntity) { rows.remove(message.id) }
        override suspend fun getById(id: Long): MessageEntity? = rows[id]
        override suspend fun getForConversation(conversationId: Long): List<MessageEntity> =
            rows.values.filter { it.conversationId == conversationId }
        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(rows.values.filter { it.conversationId == conversationId })
        override suspend fun getAll(): List<MessageEntity> = rows.values.toList()
        override fun observeAll(): Flow<List<MessageEntity>> = flowOf(rows.values.toList())
        override suspend fun markConversationRead(conversationId: Long) {}
        override suspend fun deleteById(id: Long) { rows.remove(id) }
        override suspend fun markSent(id: Long) {}
        override suspend fun deleteForConversation(conversationId: Long) {}
    }

    private fun pendingMms(id: Long = 1L) = Message(
        id = id,
        conversationId = 1L,
        direction = MessageDirection.INCOMING,
        transport = MessageTransport.MMS,
        body = "",
        timestampMillis = 1L,
        senderAddress = "+15551234567",
        contentLocation = "http://mmsc.example/id",
    )

    @Test
    fun `needsRetrieve is true only for inbound MMS with a location and empty body`() {
        assertTrue(MmsRetrieve.needsRetrieve(pendingMms()))
        assertFalse(MmsRetrieve.needsRetrieve(pendingMms().copy(contentLocation = null)))
        assertFalse(MmsRetrieve.needsRetrieve(pendingMms().copy(body = "hello")))
        assertFalse(
            MmsRetrieve.needsRetrieve(
                pendingMms().copy(transport = MessageTransport.SMS, contentLocation = null),
            ),
        )
    }

    @Test
    fun `applyPdu writes the retrieved body and clears the location`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 1L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = "",
                timestampMillis = 1L,
                senderAddress = "+15551234567",
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "text/plain\u0000retrieved caption\u0000".toByteArray()
        assertTrue(MmsRetrieve.applyPdu(dao, 1L, pdu))
        assertEquals("retrieved caption", dao.rows.getValue(1L).body)
        assertNull(dao.rows.getValue(1L).contentLocation)
    }

    @Test
    fun `applyPdu deletes an audio-only retrieve`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 2L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = "",
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "audio/amr\u0000".toByteArray()
        assertTrue(MmsRetrieve.applyPdu(dao, 2L, pdu))
        assertTrue(dao.rows.isEmpty())
    }
}
