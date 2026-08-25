package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation

/**
 * Presenter for a single conversation row in the launcher's list.
 *
 * Pure Kotlin with zero `android.*` imports so the mapping is JVM-testable
 * without a device ([ThreadMessagePresenter] precedent). Maps a
 * `core.Conversation` to the view state a row layout binds: the title (the
 * remote participant addresses), a one-line snippet of the latest message, the
 * latest-activity timestamp, and the unread count for the badge. The row
 * renders as a text-first line on AMOLED black — no avatar, no card
 * (docs/DESIGN.md §"Conversation list").
 */
object ConversationListPresenter {

    /** Title shown for a conversation with no remote participant. */
    const val UNTITLED = "(no recipient)"

    /**
     * Maps a conversation to its list-row view state.
     *
     * The title joins the participant addresses; the snippet is the FIRST line
     * of the latest message's body (a multi-line body must not blow the row
     * open), trimmed; the unread count comes straight from the model's
     * derivation. An empty conversation presents an empty snippet and a null
     * timestamp.
     */
    fun present(conversation: Conversation): ConversationRow {
        val latest = conversation.latestMessage
        return ConversationRow(
            conversationId = conversation.id,
            title = conversation.participantAddresses
                .joinToString(", ")
                .ifEmpty { UNTITLED },
            snippet = latest?.body
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                .orEmpty(),
            timestampMillis = conversation.latestTimestampMillis,
            unreadCount = conversation.unreadCount,
        )
    }
}

/**
 * View state for one conversation-list row, produced by
 * [ConversationListPresenter].
 */
data class ConversationRow(
    val conversationId: Long,
    val title: String,
    val snippet: String,
    /** Epoch millis of the latest activity, or null for an empty conversation. */
    val timestampMillis: Long?,
    val unreadCount: Int,
)
