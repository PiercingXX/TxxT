package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the read-aloud wiring: ThreadActivity creates an on-device
 * [TextToSpeech] engine and routes every message tap through
 * [MessageReadAloud.speakable] as the only source of the spoken text.
 *
 * Following the established manifest/source-reading pattern
 * (DictationWiringTest, ThreadWiringTest), the on-device `TextToSpeech` hop —
 * producing audible speech — is not JVM-testable without a speaker (see the
 * plan's deferred verification). What the box locks here is the deterministic
 * route: a message tap must reach a handler that calls
 * `MessageReadAloud.speakable` and feeds the result to `TextToSpeech.speak`,
 * so the test FAILS if the wire-in is missing. The read-aloud text mapping
 * itself is proven behaviourally by the seam's own test.
 */
class TtsWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }

    @Test
    fun `ThreadActivity creates an on-device TextToSpeech engine`() {
        assertTrue(
            "ThreadActivity must create a TextToSpeech engine for read-aloud",
            threadActivity.contains("TextToSpeech("),
        )
    }

    @Test
    fun `ThreadActivity routes message taps through MessageReadAloud`() {
        assertTrue(
            "ThreadActivity must give a message tap a read-aloud handler",
            threadActivity.contains("readMessageAloud"),
        )
        assertTrue(
            "ThreadActivity must source the spoken text from MessageReadAloud.speakable",
            threadActivity.contains("MessageReadAloud.speakable"),
        )
    }

    @Test
    fun `ThreadActivity feeds the seam result to TextToSpeech speak`() {
        assertTrue(
            "ThreadActivity must call TextToSpeech.speak with the seam's text",
            threadActivity.contains("tts?.speak"),
        )
    }

    // ---- Behavioral proof of the read-aloud seam (the deterministic half of
    // WS13 read-aloud: what text is fed to TextToSpeech.speak). The seam is
    // pure Kotlin with zero android.* imports, so the mapping is JVM-testable
    // without a device; the on-device audible hop is deferred (see the plan).

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
    fun `speakable prefixes an incoming message with its sender`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.INCOMING, "hello", senderAddress = "+15550001111"),
        )
        assertEquals("from +15550001111: hello", text)
    }

    @Test
    fun `speakable reads an outgoing message as the body alone`() {
        val text = MessageReadAloud.speakable(
            message(MessageDirection.OUTGOING, "hello"),
        )
        assertEquals("hello", text)
    }

    @Test
    fun `speakable yields an empty string for a blank body`() {
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.OUTGOING, "")))
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.OUTGOING, "   ")))
        assertEquals("", MessageReadAloud.speakable(message(MessageDirection.INCOMING, " \t ")))
    }
}