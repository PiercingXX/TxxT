package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavioural test of the read-aloud-text seam ([MessageReadAloud.speakable]).
 *
 * The seam is pure Kotlin with zero `android.*` imports (docs/DESIGN.md
 * §"Pure-Kotlin core"), so the mapping is JVM-testable without a device. This
 * is the deterministic half of WS13 read-aloud: what text is spoken when the
 * user taps a message. The on-device `TextToSpeech` hop that produces the
 * audible speech is deferred (needs a speaker — see the plan); what this test
 * locks is the mapping ThreadActivity feeds to `TextToSpeech.speak`.
 */
class MessageReadAloudTest {

    private fun message(
        direction: MessageDirection,
        body: String,
        senderAddress: String? = null,
    ) = Message(
        id = 1L,
        conversationId = 1L,
        direction = direction,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = 1000L,
        senderAddress = senderAddress,
    )

    @Test
    fun `an incoming message is prefixed with its sender`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.INCOMING, "hello", senderAddress = "+15550001111"),
        )
        assertEquals("from +15550001111: hello", text)
    }

    @Test
    fun `an outgoing message is read as the body alone`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.OUTGOING, "hello"),
        )
        assertEquals("hello", text)
    }

    @Test
    fun `an incoming message with no sender address reads just the body`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.INCOMING, "hello", senderAddress = null),
        )
        assertEquals("from : hello", text)
    }

    @Test
    fun `the body is trimmed before being spoken`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.OUTGOING, "  hello  "),
        )
        assertEquals("hello", text)
    }

    @Test
    fun `a blank body yields an empty string so the speak call is skipped`() {
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.OUTGOING, "")))
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.OUTGOING, "   ")))
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.INCOMING, " \t ")))
    }
}