package com.piercingxx.txxt.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the thread-layout constraints (T2) by reading the layout XML directly.
 *
 * The screen *rendering* on-device is a visual check that is the operator's (see
 * the plan's deferred verification) and inflating the layouts is not JVM-testable
 * without Robolectric (not in the offline cache). Following the established
 * manifest-test pattern (ReceiverManifestTest, NotificationManifestTest), this
 * locks the structural contract the design depends on:
 *   - the thread has a compose bar and a message list, and NO voice-message
 *     affordance (docs/DESIGN.md §"Conversation thread", docs/PRIVACY.md §5);
 *   - the message row is a text-first line with NO bubble/card background
 *     (docs/PRIVACY.md §2 — "nothing ever bubbles").
 */
class ThreadLayoutTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory, so the layouts are src/main/res/layout/... relative to that;
    // fall back to the workspace-root-relative path for robustness.
    private fun layoutText(name: String): String {
        val file = sequenceOf(
            File("src/main/res/layout/$name"),
            File("app/src/main/res/layout/$name"),
        ).first { it.exists() }
        // Strip XML comments so the assertions check actual layout elements and
        // attributes, not the documentation prose that names the constraints.
        return file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val activityThread: String by lazy { layoutText("activity_thread.xml") }
    private val itemMessage: String by lazy { layoutText("item_message.xml") }

    @Test
    fun `the thread layout has a message list`() {
        assertTrue(
            "activity_thread.xml must host a RecyclerView message list",
            activityThread.contains("RecyclerView"),
        )
        assertTrue(
            "activity_thread.xml must identify the message list as message_list",
            activityThread.contains("@+id/message_list"),
        )
    }

    @Test
    fun `the thread layout has a compose bar`() {
        assertTrue(
            "activity_thread.xml must contain a compose bar",
            activityThread.contains("compose_bar"),
        )
        assertTrue(
            "activity_thread.xml must contain a text input for composing",
            activityThread.contains("EditText"),
        )
    }

    @Test
    fun `the thread layout has NO voice-message affordance`() {
        // docs/PRIVACY.md §5 — voice messages are never sent or received, so the
        // compose bar must not carry a voice-message send/record element. The
        // dictation mic button (dictation_button) is NOT a voice-message
        // affordance — it runs on-device SpeechRecognizer and inserts recognized
        // text into the compose field, never sending or receiving audio — so it
        // is allowed here.
        val lower = activityThread.lowercase()
        assertFalse(
            "activity_thread.xml must not contain a voice-message send/record affordance",
            lower.contains("voice_message") ||
                lower.contains("record_voice") ||
                lower.contains("send_voice") ||
                lower.contains("voice_note"),
        )
    }

    @Test
    fun `the message row is a text-first line with NO bubble or card background`() {
        // docs/PRIVACY.md §2 — "nothing ever bubbles". The row must not carry a
        // bubble/card background: no rounded-corner drawable, no card background
        // on the row container.
        val lower = itemMessage.lowercase()
        assertTrue(
            "item_message.xml must carry the message body text",
            itemMessage.contains("message_body"),
        )
        assertFalse(
            "item_message.xml must not reference a rounded-corner drawable",
            lower.contains("rounded"),
        )
        assertFalse(
            "item_message.xml must not apply a bubble/card background to the row",
            lower.contains("card") || lower.contains("bubble"),
        )
    }
}