package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.ui.ThreadMessagePresenter.present
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadMessagePresenterTest {

    private fun message(
        id: Long,
        direction: MessageDirection,
        body: String = "hello",
        timestampMillis: Long = id * 1000L,
    ) = Message(
        id = id,
        conversationId = 1L,
        direction = direction,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
    )

    @Test
    fun `outgoing message maps to right alignment and sent emphasis`() {
        val row = present(message(id = 1L, direction = MessageDirection.OUTGOING))
        assertEquals(ThreadAlignment.RIGHT, row.alignment)
        assertEquals(ThreadEmphasis.SENT, row.emphasis)
    }

    @Test
    fun `incoming message maps to left alignment and received emphasis`() {
        val row = present(message(id = 2L, direction = MessageDirection.INCOMING))
        assertEquals(ThreadAlignment.LEFT, row.alignment)
        assertEquals(ThreadEmphasis.RECEIVED, row.emphasis)
    }

    @Test
    fun `body and timestamp pass through unchanged`() {
        val row = present(
            message(id = 3L, direction = MessageDirection.INCOMING, body = "see attached", timestampMillis = 123456L),
        )
        assertEquals("see attached", row.body)
        assertEquals(123456L, row.timestampMillis)
    }

    @Test
    fun `both directions produce a text-first row with no bubble state`() {
        // The row model carries only alignment + emphasis + content — no bubble
        // or card field exists, so the no-bubble constraint is structural.
        val outgoing = present(message(id = 4L, direction = MessageDirection.OUTGOING))
        val incoming = present(message(id = 5L, direction = MessageDirection.INCOMING))
        assertEquals(setOf("alignment", "emphasis", "body", "timestampMillis"), outgoing::class.java.declaredFields.map { it.name }.toSet())
        assertEquals(setOf("alignment", "emphasis", "body", "timestampMillis"), incoming::class.java.declaredFields.map { it.name }.toSet())
    }
}