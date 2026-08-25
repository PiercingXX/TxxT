package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour-verifies [ConversationListPresenter]: the pure mapping from a
 * `core` [Conversation] to the launcher row's view state — title from the
 * participant addresses, one-line snippet from the LATEST message, the
 * latest-activity timestamp, and the unread count for the badge.
 */
class ConversationListPresenterTest {

    private fun message(
        id: Long,
        conversationId: Long,
        body: String,
        timestampMillis: Long,
        direction: MessageDirection = MessageDirection.INCOMING,
        isRead: Boolean = false,
    ): Message = Message(
        id = id,
        conversationId = conversationId,
        direction = direction,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
        isRead = isRead,
    )

    @Test
    fun `title joins the participant addresses`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 1L, participantAddresses = setOf("+15550001111"))
        )
        assertEquals("+15550001111", row.title)
    }

    @Test
    fun `a conversation with no participants presents the untitled marker`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 1L, participantAddresses = emptySet())
        )
        assertEquals(ConversationListPresenter.UNTITLED, row.title)
    }

    @Test
    fun `snippet is the first line of the LATEST message`() {
        val conversation = Conversation(
            id = 7L,
            participantAddresses = setOf("+15550001111"),
            messages = listOf(
                message(1L, 7L, "older", timestampMillis = 1_000L),
                message(2L, 7L, "newest first line\nsecond line", timestampMillis = 2_000L),
            ),
        )
        val row = ConversationListPresenter.present(conversation)
        assertEquals("newest first line", row.snippet)
        assertEquals(2_000L, row.timestampMillis)
    }

    @Test
    fun `an empty conversation presents an empty snippet and null timestamp`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 1L, participantAddresses = setOf("+15550001111"))
        )
        assertEquals("", row.snippet)
        assertEquals(null, row.timestampMillis)
    }

    @Test
    fun `unread count comes from the model derivation`() {
        val conversation = Conversation(
            id = 7L,
            participantAddresses = setOf("+15550001111"),
            messages = listOf(
                message(1L, 7L, "unread one", 1_000L),
                message(2L, 7L, "unread two", 2_000L),
                message(3L, 7L, "already read", 3_000L, isRead = true),
                message(4L, 7L, "mine", 4_000L, direction = MessageDirection.OUTGOING),
            ),
        )
        assertEquals(2, ConversationListPresenter.present(conversation).unreadCount)
    }

    @Test
    fun `row carries the conversation id the tap handler opens`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 42L, participantAddresses = setOf("+15550001111"))
        )
        assertEquals(42L, row.conversationId)
    }

    // ---- The adapter's pure display helpers ----

    @Test
    fun `unread badge label is the count plus new`() {
        assertEquals("3 new", ConversationListAdapter.unreadLabel(3))
    }

    @Test
    fun `same-day timestamps show the clock time`() {
        // 2026-01-05 (UTC-agnostic: both instants land in the same local day
        // because they are one minute apart).
        val now = 1_767_614_400_000L
        val oneMinuteEarlier = now - 60_000L
        val formatted = ConversationListAdapter.formatTimestamp(now, oneMinuteEarlier)
        assertEquals("clock time is HH:mm", 5, formatted.length)
        assertEquals(':', formatted[2])
    }

    @Test
    fun `older timestamps show the calendar day`() {
        val now = 1_767_614_400_000L
        val tenDaysEarlier = now - 10L * 24 * 60 * 60 * 1_000
        val formatted = ConversationListAdapter.formatTimestamp(now, tenDaysEarlier)
        // "MMM d" — a month word, a space, a day number; never a colon.
        assertEquals(false, formatted.contains(':'))
        assertEquals(true, formatted.contains(' '))
    }
}
