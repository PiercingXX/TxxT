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
     * The row/thread title for a set of participant [addresses].
     *
     * Each address is passed through [displayName] first, so a saved contact
     * shows as the name the operator gave them instead of a raw number
     * (`contacts/ContactNameResolver`). The seam is a plain lambda defaulting
     * to identity, which is what keeps this file free of `android.*` imports
     * and JVM-testable: the presenter decides the *shape* of a title (join
     * order, separator, the untitled marker), the caller supplies the
     * *resolution*.
     *
     * A resolver that returns blank for an address falls back to the address —
     * a row must never render an empty title (see the resolver's never-blank
     * contract). Shared with the thread screen's header so the list and the
     * thread cannot disagree about what a conversation is called.
     */
    fun title(
        addresses: Collection<String>,
        displayName: (String) -> String = { it },
    ): String = addresses
        .joinToString(", ") { address -> displayName(address).ifBlank { address } }
        .ifEmpty { UNTITLED }

    /**
     * Maps a conversation to its list-row view state.
     *
     * The title joins the participant addresses (resolved through
     * [displayName], see [title]); the snippet is the FIRST line of the latest
     * message's body (a multi-line body must not blow the row open), trimmed;
     * the unread count comes straight from the model's derivation. An empty
     * conversation presents an empty snippet and a null timestamp.
     */
    fun present(
        conversation: Conversation,
        displayName: (String) -> String = { it },
    ): ConversationRow {
        val latest = conversation.latestMessage
        return ConversationRow(
            conversationId = conversation.id,
            title = title(conversation.participantAddresses, displayName),
            snippet = buildSnippet(latest?.body, conversation.isMuted),
            timestampMillis = conversation.latestTimestampMillis,
            unreadCount = conversation.unreadCount,
            muted = conversation.isMuted,
        )
    }

    private fun buildSnippet(body: String?, muted: Boolean): String {
        val line = body?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return if (muted) {
            if (line.isEmpty()) "muted" else "muted · $line"
        } else {
            line
        }
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
    val muted: Boolean = false,
    /** Star / business / family / block marks, shown after the name. */
    val marks: String = "",
)
