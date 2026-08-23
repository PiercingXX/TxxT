package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the conversation-search wiring (WS10 corrective-corrective T3):
 * `MainActivity.applySearchQuery` filters the conversation list through
 * [ConversationSearchFilter] and submits the filtered list to the adapter.
 *
 * The core verification is behavioural: a real [ConversationSearchFilter] with an
 * injected recording submit is driven through its real `apply` path, and the test
 * asserts the side effect fires with the filtered list — search input → filtered
 * list → submitted to the adapter. If `apply` never reached `filterConversations`
 * or never submitted, the recorded submit stays empty and this fails.
 *
 * The framework-bound `MainActivity` (an Activity) cannot be instantiated in a
 * plain JVM unit test (no Robolectric in the offline cache), so its call-site
 * wiring — the SearchView listener routing into `applySearchQuery`, and
 * `applySearchQuery` reaching `ConversationSearchFilter.filterConversations` — is
 * locked by reading the source, the same pattern `ThreadWiringTest` and
 * `MainActivityWiringTest` use. Whether the OS delivers the keystroke to the
 * widget's listener is on-device (see the plan's deferred verification).
 */
class MainActivitySearchTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }

    private fun conversation(
        id: Long,
        address: String,
        body: String,
    ): Conversation {
        val message = Message(
            id = id,
            conversationId = id,
            direction = MessageDirection.INCOMING,
            transport = MessageTransport.SMS,
            body = body,
            timestampMillis = id * 1000L,
            senderAddress = address,
        )
        return Conversation(id = id, participantAddresses = setOf(address), messages = listOf(message))
    }

    // ---- Behavioural: the filter's real apply path fires the submit seam ----

    @Test
    fun `apply filters by participant address and submits the filtered list`() {
        val alice = conversation(1L, "+15550001111", "lunch tomorrow?")
        val bob = conversation(2L, "+15550002222", "meeting notes")
        val conversations = listOf(alice, bob)

        var submitted: List<Conversation>? = null
        val filter = ConversationSearchFilter(submit = { submitted = it })

        filter.apply("111", conversations)

        // If apply never reached filterConversations and the submit seam, submitted
        // stays null and this fails.
        assertEquals(listOf(alice), submitted)
    }

    @Test
    fun `apply filters by message body case-insensitively`() {
        val alice = conversation(1L, "+15550001111", "Lunch tomorrow?")
        val bob = conversation(2L, "+15550002222", "meeting notes")
        val conversations = listOf(alice, bob)

        var submitted: List<Conversation>? = null
        ConversationSearchFilter(submit = { submitted = it }).apply("lunch", conversations)

        assertEquals(listOf(alice), submitted)
    }

    @Test
    fun `a blank query submits the full list unchanged`() {
        val conversations = listOf(
            conversation(1L, "+15550001111", "lunch"),
            conversation(2L, "+15550002222", "meeting"),
        )

        var submitted: List<Conversation>? = null
        ConversationSearchFilter(submit = { submitted = it }).apply("   ", conversations)

        assertEquals(conversations, submitted)
    }

    @Test
    fun `a non-matching query submits an empty list`() {
        val conversations = listOf(
            conversation(1L, "+15550001111", "lunch"),
            conversation(2L, "+15550002222", "meeting"),
        )

        var submitted: List<Conversation>? = null
        ConversationSearchFilter(submit = { submitted = it }).apply("zzz", conversations)

        assertEquals(emptyList<Conversation>(), submitted)
    }

    // ---- Wiring: MainActivity is the reachable call site (not dead code) ----

    @Test
    fun `MainActivity routes the SearchView listener into applySearchQuery`() {
        assertTrue(
            "MainActivity must resolve the SearchView by its runtime view ID",
            mainActivity.contains("findViewById<SearchView>(R.id.searchView)"),
        )
        assertTrue(
            "MainActivity must wire a query listener onto the SearchView",
            mainActivity.contains("setOnQueryTextListener"),
        )
        assertTrue(
            "the listener must call applySearchQuery",
            mainActivity.contains("applySearchQuery"),
        )
    }

    @Test
    fun `MainActivity applySearchQuery reaches filterConversations`() {
        assertTrue(
            "MainActivity.applySearchQuery must filter through ConversationSearchFilter.filterConversations",
            mainActivity.contains("searchFilter.filterConversations(conversations, query)"),
        )
    }
}