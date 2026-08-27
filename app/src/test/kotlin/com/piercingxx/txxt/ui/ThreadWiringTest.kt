package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the thread screen wiring (T3): the manifest registers ThreadActivity,
 * MainActivity launches it, ThreadActivity drives the
 * ThreadAdapter and routes the compose send through SendPipeline.sendSms, and
 * the adapter's real `onBindViewHolder` path reaches ThreadMessagePresenter.
 *
 * Following the established manifest/source-reading pattern (ReceiverManifestTest,
 * ThreadLayoutTest), the Android intent/activity dispatch itself is not
 * JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), so the manifest-declared name must resolve via
 * [Class.forName] and the launch/wiring is locked by reading the source. The
 * adapter's presenter seam is driven behaviourally: a real ThreadAdapter with an
 * injected binding is fed a Message and its `onBindViewHolder` is invoked, so the
 * test FAILS if the adapter never calls ThreadMessagePresenter.present.
 */
class ThreadWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }
    private val threadAdapterSource: String by lazy { sourceText("ui/ThreadAdapter.kt") }

    private fun message(
        id: Long,
        direction: MessageDirection,
        body: String = "hello",
    ) = Message(
        id = id,
        conversationId = 1L,
        direction = direction,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = id * 1000L,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
    )

    // ---- Manifest registration ----

    @Test
    fun `the manifest declares the thread activity`() {
        assertTrue(
            "AndroidManifest.xml must declare the .ui.ThreadActivity component",
            manifestText.contains(".ui.ThreadActivity"),
        )
    }

    @Test
    fun `the declared thread activity name resolves to a class`() {
        Class.forName("com.piercingxx.txxt.ui.ThreadActivity")
    }

    // ---- Launch wiring ----

    @Test
    fun `MainActivity launches ThreadActivity`() {
        // The launcher opens threads through ThreadActivity.launchIntent — the
        // factory that carries EXTRA_CONVERSATION_ID (locked behaviourally in
        // `launchIntent carries the conversation id` below where applicable),
        // so a tapped row and the NEW affordance both land on a real thread.
        assertTrue(
            "MainActivity must open threads via ThreadActivity.launchIntent",
            mainActivity.contains("ThreadActivity.launchIntent("),
        )
        assertFalse(
            "FLAG_SECURE blacks screenshots; the operator asked to capture the app",
            mainActivity.contains("FLAG_SECURE"),
        )
        assertTrue(
            "ThreadActivity.launchIntent must pass the conversation id extra",
            sourceText("ui/ThreadActivity.kt").contains("EXTRA_CONVERSATION_ID"),
        )
    }

    // ---- ThreadActivity wiring ----

    @Test
    fun `ThreadActivity drives the ThreadAdapter and routes sends through SendPipeline`() {
        assertTrue(
            "ThreadActivity must instantiate a ThreadAdapter",
            threadActivity.contains("ThreadAdapter("),
        )
        assertTrue(
            "ThreadActivity must wire the adapter's message tap to read-aloud",
            threadActivity.contains("onMessageTap"),
        )
        assertTrue(
            "ThreadActivity must submit loaded messages to the adapter",
            threadActivity.contains("adapter.submit"),
        )
        assertTrue(
            "ThreadActivity must route the compose send through SendPipeline.sendSms",
            threadActivity.contains("SendPipeline.sendSms"),
        )
        assertFalse(
            "FLAG_SECURE blacks screenshots; the operator asked to capture the app",
            threadActivity.contains("FLAG_SECURE"),
        )
        assertFalse(
            "the compose field must not bind SentenceCapitalizer — that watcher " +
                "forced a capital after a symbol or number mid-sentence",
            threadActivity.contains("SentenceCapitalizer"),
        )
        assertTrue(
            "the message list must stack from the end so new lines sit above the keyboard",
            threadActivity.contains("stackFromEnd = true"),
        )
        assertTrue(
            "incoming messages must pin the viewport to the latest row",
            threadActivity.contains("pinToLatest()"),
        )
        assertTrue(
            "opening a thread must dismiss that sender's shade notification",
            threadActivity.contains("dismissShadeNotification()"),
        )
        assertTrue(
            "the open thread must register as viewed so a follow-up SMS stays silent",
            threadActivity.contains("ViewedThread.open("),
        )
        assertTrue(
            "leaving the thread must clear the viewed-thread marker",
            threadActivity.contains("ViewedThread.close("),
        )
    }

    // ---- Adapter seam (behavioural: drives the real onBindViewHolder path) ----

    @Test
    fun `the adapter's onBindViewHolder reaches ThreadMessagePresenter`() {
        var bound: ThreadRow? = null
        val adapter = ThreadAdapter(bindRow = { _, row -> bound = row })

        // Relaxed mock View — no inflation needed in a JVM test.
        val holder = ThreadAdapter.RowHolder(mockk(relaxed = true))

        adapter.submit(listOf(message(1L, MessageDirection.OUTGOING, body = "hi")))
        assertEquals(1, adapter.itemCount)

        adapter.onBindViewHolder(holder, 0)

        // If onBindViewHolder never called ThreadMessagePresenter.present, bound
        // stays null and this fails — the wire-in is what is under test.
        assertEquals("hi", bound?.body)
        assertEquals(ThreadAlignment.RIGHT, bound?.alignment)
        assertEquals(ThreadEmphasis.SENT, bound?.emphasis)
    }

    @Test
    fun `the adapter maps inbound rows through the presenter too`() {
        var bound: ThreadRow? = null
        val adapter = ThreadAdapter(bindRow = { _, row -> bound = row })
        val holder = ThreadAdapter.RowHolder(mockk(relaxed = true))

        adapter.submit(listOf(message(2L, MessageDirection.INCOMING)))
        adapter.onBindViewHolder(holder, 0)

        assertEquals(ThreadAlignment.LEFT, bound?.alignment)
        assertEquals(ThreadEmphasis.RECEIVED, bound?.emphasis)
    }
}