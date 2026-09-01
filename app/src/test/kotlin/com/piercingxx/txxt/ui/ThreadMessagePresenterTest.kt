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
    fun `an outgoing photo with no caption renders as a text-first photo line`() {
        // Sending a photo with an empty caption persists a blank-bodied MMS
        // row. Without the substitution the thread shows a row of nothing at
        // all and the send looks like it vanished.
        val row = present(
            message(id = 6L, direction = MessageDirection.OUTGOING, body = "")
                .copy(transport = MessageTransport.MMS),
        )
        assertEquals(PhotoAttachment.PHOTO_ROW_PLACEHOLDER, row.body)
    }

    @Test
    fun `an outgoing MMS that carries a body keeps its own text`() {
        val row = present(
            message(id = 7L, direction = MessageDirection.OUTGOING, body = "real text")
                .copy(transport = MessageTransport.MMS),
        )
        assertEquals("real text", row.body)
    }

    @Test
    fun `an inbound Photo marker is shown as Photo until tap`() {
        val row = present(
            message(id = 10L, direction = MessageDirection.INCOMING, body = "[Photo]")
                .copy(transport = MessageTransport.MMS, mediaPath = "/tmp/p.jpg"),
        )
        assertEquals("[Photo]", row.body)
        assertEquals("/tmp/p.jpg", row.mediaPath)
    }

    @Test
    fun `an inbound MMS with an empty body is not invented as a placeholder`() {
        val row = present(
            message(id = 8L, direction = MessageDirection.INCOMING, body = "")
                .copy(transport = MessageTransport.MMS),
        )
        assertEquals("", row.body)
    }

    @Test
    fun `a blank-bodied SMS is left blank`() {
        val row = present(message(id = 9L, direction = MessageDirection.OUTGOING, body = ""))
        assertEquals("", row.body)
    }

    @Test
    fun `both directions produce a text-first row with no bubble state`() {
        // The row model carries only alignment + emphasis + content — no bubble
        // or card field exists, so the no-bubble constraint is structural.
        val outgoing = present(message(id = 4L, direction = MessageDirection.OUTGOING))
        val incoming = present(message(id = 5L, direction = MessageDirection.INCOMING))
        assertEquals(
            setOf("alignment", "emphasis", "body", "timestampMillis", "mediaPath"),
            outgoing::class.java.declaredFields.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("alignment", "emphasis", "body", "timestampMillis", "mediaPath"),
            incoming::class.java.declaredFields.map { it.name }.toSet(),
        )
    }
}