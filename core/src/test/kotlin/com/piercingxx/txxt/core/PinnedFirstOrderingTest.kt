package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves [ConversationList.sorted] wires the pin flag into the ordering: under
 * [ConversationSortOrder.PINNED_FIRST], pinned conversations sort before
 * unpinned ones regardless of unread state or recency.
 */
class PinnedFirstOrderingTest {

    private fun message(
        id: Long,
        conversationId: Long,
        isRead: Boolean = false,
        timestampMillis: Long = id * 1000L,
    ) = Message(
        id = id,
        conversationId = conversationId,
        direction = MessageDirection.INCOMING,
        transport = MessageTransport.SMS,
        body = "hello",
        timestampMillis = timestampMillis,
        senderAddress = "+15550001111",
        isRead = isRead,
    )

    private fun conversation(
        id: Long,
        vararg messages: Message,
    ) = Conversation(
        id = id,
        participantAddresses = setOf("+15550001111"),
        messages = messages.toList(),
    )

    private fun ids(conversations: List<Conversation>): List<Long> =
        conversations.map { it.id }

    @Test
    fun `pinned conversation sorts before unpinned under pinned first`() {
        val unpinned = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 3000L),
        )
        val pinned = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 1000L),
        )
        val ordered = ConversationList.sorted(
            listOf(unpinned, pinned),
            ConversationSortOrder.PINNED_FIRST,
            pinnedIds = setOf(2L),
        )
        assertEquals(listOf(2L, 1L), ids(ordered))
    }

    @Test
    fun `pinned first falls back to recency within each group`() {
        val newerUnpinned = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 3000L),
        )
        val olderUnpinned = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 1000L),
        )
        val olderPinned = conversation(
            3L,
            message(id = 3L, conversationId = 3L, timestampMillis = 500L),
        )
        val newerPinned = conversation(
            4L,
            message(id = 4L, conversationId = 4L, timestampMillis = 2000L),
        )
        val ordered = ConversationList.sorted(
            listOf(newerUnpinned, olderUnpinned, olderPinned, newerPinned),
            ConversationSortOrder.PINNED_FIRST,
            pinnedIds = setOf(3L, 4L),
        )
        // Pinned group newest-first, then unpinned group newest-first.
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(ordered))
    }

    @Test
    fun `pinned first defaults to no pins and orders by recency`() {
        val older = conversation(
            1L,
            message(id = 1L, conversationId = 1L, timestampMillis = 1000L),
        )
        val newer = conversation(
            2L,
            message(id = 2L, conversationId = 2L, timestampMillis = 2000L),
        )
        val ordered = ConversationList.sorted(listOf(older, newer), ConversationSortOrder.PINNED_FIRST)
        assertEquals(listOf(2L, 1L), ids(ordered))
    }
}