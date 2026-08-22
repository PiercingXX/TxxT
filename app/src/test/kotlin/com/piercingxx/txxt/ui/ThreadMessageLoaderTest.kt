package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.data.MessageDao
import com.piercingxx.txxt.data.MessageEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviorally verifies the T1 seam: [ThreadMessageLoader] drives the
 * conversationId → DAO query → mapper pipeline. A specific conversationId must
 * be forwarded into [MessageDao.observeForConversation] and the returned Room
 * entities must be mapped to the pure `core` [com.piercingxx.txxt.core.Message]
 * model before the caller sees them.
 *
 * The DAO is an interface and the loader is JVM-pure (no `android.*` imports),
 * so this needs no device: a mocked DAO is stubbed to return a known entity
 * flow and the loader's own `messages()` flow is collected and asserted.
 */
class ThreadMessageLoaderTest {

    private fun entity(
        id: Long,
        direction: MessageDirection,
        body: String = "hello",
    ) = MessageEntity(
        id = id,
        conversationId = 7L,
        direction = direction.name,
        transport = MessageTransport.SMS.name,
        body = body,
        timestampMillis = id * 1000L,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
    )

    @Test
    fun `the loader forwards its conversationId into the DAO query`() = runBlocking {
        val dao = mockk<MessageDao>()
        every { dao.observeForConversation(any()) } returns flowOf(emptyList())

        ThreadMessageLoader(dao, conversationId = 42L).messages().toList()

        verify { dao.observeForConversation(42L) }
    }

    @Test
    fun `the loader maps the queried entities to pure core messages`() = runBlocking {
        val dao = mockk<MessageDao>()
        val incoming = entity(id = 1L, direction = MessageDirection.INCOMING, body = "see attached")
        val outgoing = entity(id = 2L, direction = MessageDirection.OUTGOING, body = "on my way")
        every { dao.observeForConversation(any()) } returns flowOf(listOf(incoming, outgoing))

        val messages = ThreadMessageLoader(dao, conversationId = 7L).messages().toList().single()

        assertEquals(2, messages.size)
        assertEquals("see attached", messages[0].body)
        assertEquals(MessageDirection.INCOMING, messages[0].direction)
        assertEquals("+15550001111", messages[0].senderAddress)
        assertEquals("on my way", messages[1].body)
        assertEquals(MessageDirection.OUTGOING, messages[1].direction)
    }

    @Test
    fun `the loader maps every emitted entity batch`() = runBlocking {
        val dao = mockk<MessageDao>()
        every { dao.observeForConversation(any()) } returns flowOf(
            listOf(entity(id = 1L, direction = MessageDirection.OUTGOING)),
            listOf(entity(id = 2L, direction = MessageDirection.INCOMING)),
        )

        val batches = ThreadMessageLoader(dao, conversationId = 7L).messages().toList()

        assertEquals(2, batches.size)
        assertEquals(1, batches[0].size)
        assertEquals(1, batches[1].size)
        assertEquals(MessageDirection.INCOMING, batches[1].single().direction)
    }
}