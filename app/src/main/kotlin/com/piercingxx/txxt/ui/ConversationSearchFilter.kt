package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation

/**
 * Filters the conversation list by a search query (WS10 corrective-corrective T3).
 *
 * The pure `filterConversations` maps search input → filtered list: a conversation
 * matches when the query appears in one of its participant addresses or in the body
 * of one of its messages (case-insensitive). An empty or blank query returns the
 * list unchanged.
 *
 * The submit step is injected as a lambda so the real
 * `apply` → `filterConversations` → submit path is drivable in a plain JVM unit
 * test without Robolectric (not in the offline cache): the test injects a
 * recording submit and verifies the side effect fires with the filtered list,
 * mirroring the `ConversationSwipeHelper` injectable-`attach` seam. The production
 * submit is the conversation-list adapter `MainActivity` owns.
 */
class ConversationSearchFilter(
    private val submit: (List<Conversation>) -> Unit = {},
) {

    /**
     * Returns [conversations] reduced to those matching [query] (case-insensitive
     * over participant addresses and message bodies). A blank query matches all.
     */
    fun filterConversations(conversations: List<Conversation>, query: String): List<Conversation> {
        val q = query.trim()
        if (q.isEmpty()) return conversations
        val lower = q.lowercase()
        return conversations.filter { conversation ->
            conversation.participantAddresses.any { it.lowercase().contains(lower) } ||
                conversation.messages.any { it.body.lowercase().contains(lower) }
        }
    }

    /**
     * Applies [query] to [conversations] and submits the filtered list through the
     * injectable [submit] seam. This is the live path `MainActivity.applySearchQuery`
     * reaches when the SearchView listener fires.
     */
    fun apply(query: String, conversations: List<Conversation>) {
        submit(filterConversations(conversations, query))
    }
}