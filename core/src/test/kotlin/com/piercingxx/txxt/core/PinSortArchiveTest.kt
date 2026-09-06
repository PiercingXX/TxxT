package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSortArchiveTest {

    @Test
    fun `default flags are unpinned unarchived unquarantined pinned-first`() {
        val flags = ConversationFlags()
        assertFalse(flags.isPinned)
        assertFalse(flags.isArchived)
        assertFalse(flags.isQuarantined)
        assertFalse(flags.isMuted)
        assertEquals(0L, flags.mutedUntilMillis)
        assertEquals(ConversationSortOrder.PINNED_FIRST, flags.sortOrder)
    }

    @Test
    fun `pin sets the pinned flag`() {
        val flags = ConversationFlags().pin()
        assertTrue(flags.isPinned)
    }

    @Test
    fun `unpin clears the pinned flag`() {
        val flags = ConversationFlags().pin().unpin()
        assertFalse(flags.isPinned)
    }

    @Test
    fun `archive sets the archived flag`() {
        val flags = ConversationFlags().archive()
        assertTrue(flags.isArchived)
    }

    @Test
    fun `unarchive clears the archived flag`() {
        val flags = ConversationFlags().archive().unarchive()
        assertFalse(flags.isArchived)
    }

    @Test
    fun `transitions are immutable and return new instances`() {
        val flags = ConversationFlags()
        val pinned = flags.pin()
        val archived = flags.archive()
        // The original is untouched.
        assertFalse(flags.isPinned)
        assertFalse(flags.isArchived)
        assertTrue(pinned.isPinned)
        assertFalse(pinned.isArchived)
        assertTrue(archived.isArchived)
        assertFalse(archived.isPinned)
    }

    @Test
    fun `pin and archive compose independently`() {
        val flags = ConversationFlags().pin().archive()
        assertTrue(flags.isPinned)
        assertTrue(flags.isArchived)
    }

    @Test
    fun `withSortOrder changes the sort order`() {
        val flags = ConversationFlags().withSortOrder(ConversationSortOrder.UNREAD_FIRST)
        assertEquals(ConversationSortOrder.UNREAD_FIRST, flags.sortOrder)
    }

    @Test
    fun `withSortOrder keeps pin and archive state`() {
        val flags = ConversationFlags().pin().archive().withSortOrder(ConversationSortOrder.OLDEST_FIRST)
        assertTrue(flags.isPinned)
        assertTrue(flags.isArchived)
        assertEquals(ConversationSortOrder.OLDEST_FIRST, flags.sortOrder)
    }

    @Test
    fun `quarantine sets the quarantined flag`() {
        val flags = ConversationFlags().quarantine()
        assertTrue(flags.isQuarantined)
        assertFalse(flags.releaseFromQuarantine().isQuarantined)
    }

    @Test
    fun `mute until is not forever-mute`() {
        val flags = ConversationFlags().muteUntil(1_700_000_000_000L)
        assertFalse(flags.isMuted)
        assertEquals(1_700_000_000_000L, flags.mutedUntilMillis)
        assertEquals(0L, flags.unmute().mutedUntilMillis)
        assertTrue(flags.mute().isMuted)
        assertEquals(0L, flags.mute().mutedUntilMillis)
    }

    @Test
    fun `all sort orders are distinct values`() {
        val orders = setOf(
            ConversationSortOrder.PINNED_FIRST,
            ConversationSortOrder.NEWEST_FIRST,
            ConversationSortOrder.OLDEST_FIRST,
            ConversationSortOrder.UNREAD_FIRST,
        )
        assertEquals(4, orders.size)
    }
}