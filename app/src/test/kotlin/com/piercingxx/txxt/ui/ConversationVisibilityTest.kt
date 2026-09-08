package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationVisibilityTest {

    @Test
    fun blockedThreadsStayHiddenUntilSearch() {
        assertTrue(ConversationVisibility.showOnList(blocked = false, searchQuery = null))
        assertTrue(!ConversationVisibility.showOnList(blocked = true, searchQuery = null))
        assertTrue(!ConversationVisibility.showOnList(blocked = true, searchQuery = "  "))
        assertTrue(ConversationVisibility.showOnList(blocked = true, searchQuery = "Ada"))
    }
}
