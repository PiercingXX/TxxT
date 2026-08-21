package com.piercingxx.txxt.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDispositionTest {

    // ---- MessageDisposition enum ----

    @Test
    fun `disposition has three values`() {
        val values = MessageDisposition.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(MessageDisposition.DELIVER))
        assertTrue(values.contains(MessageDisposition.QUARANTINE))
        assertTrue(values.contains(MessageDisposition.BLOCK))
    }

    @Test
    fun `disposition valueOf round-trips`() {
        for (d in MessageDisposition.values()) {
            assertEquals(d, MessageDisposition.valueOf(d.name))
        }
    }

    // ---- BlockReason Type enum ----

    @Test
    fun `block reason type has three values`() {
        val types = BlockReason.Type.values()
        assertEquals(3, types.size)
        assertTrue(types.contains(BlockReason.Type.UNKNOWN_SENDER))
        assertTrue(types.contains(BlockReason.Type.BLOCKED_LIST))
        assertTrue(types.contains(BlockReason.Type.STARRED_CONTACT_RULE))
    }

    // ---- BlockReason factory instances ----

    @Test
    fun `UnknownSender reason has correct properties`() {
        val reason = BlockReason.UnknownSender
        assertEquals(BlockReason.Type.UNKNOWN_SENDER, reason.type)
        assertNotNull(reason.message)
        assertFalse(reason.canOverride)
    }

    @Test
    fun `BlockedList reason has correct properties`() {
        val reason = BlockReason.BlockedList
        assertEquals(BlockReason.Type.BLOCKED_LIST, reason.type)
        assertNotNull(reason.message)
        assertFalse(reason.canOverride)
    }

    @Test
    fun `StarredContactRule reason is overridable`() {
        val reason = BlockReason.StarredContactRule("no-spoilers")
        assertEquals(BlockReason.Type.STARRED_CONTACT_RULE, reason.type)
        assertTrue(reason.message.contains("no-spoilers"))
        assertTrue(reason.canOverride)
    }

    // ---- BlockReason data class behaviour ----

    @Test
    fun `BlockReason equals and hashCode work`() {
        val a = BlockReason(BlockReason.Type.UNKNOWN_SENDER, "test")
        val b = BlockReason(BlockReason.Type.UNKNOWN_SENDER, "test")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `BlockReason copy changes one field`() {
        val original = BlockReason.UnknownSender
        val updated = original.copy(canOverride = true)
        assertEquals(original.type, updated.type)
        assertEquals(original.message, updated.message)
        assertTrue(updated.canOverride)
    }
}
