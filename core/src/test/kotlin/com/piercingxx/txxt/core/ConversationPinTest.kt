package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPinTest {

    @Test
    fun `the pin cap is five`() {
        assertEquals(5, ConversationPin.MAX_PINNED)
    }

    @Test
    fun `a new pin is allowed below the cap`() {
        assertTrue(ConversationPin.canPin(pinnedCount = 0, alreadyPinned = false))
        assertTrue(ConversationPin.canPin(pinnedCount = 4, alreadyPinned = false))
    }

    @Test
    fun `a sixth pin is refused`() {
        assertFalse(ConversationPin.canPin(pinnedCount = 5, alreadyPinned = false))
        assertFalse(ConversationPin.canPin(pinnedCount = 6, alreadyPinned = false))
    }

    @Test
    fun `an already-pinned thread can stay pinned at the cap`() {
        assertTrue(ConversationPin.canPin(pinnedCount = 5, alreadyPinned = true))
    }
}
