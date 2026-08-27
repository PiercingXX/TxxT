package com.piercingxx.txxt.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewedThreadTest {

    @After
    fun tearDown() {
        ViewedThread.close(ViewedThread.conversationId)
    }

    @Test
    fun `an open thread matches the stored sender and a formatted twin`() {
        ViewedThread.open(1L, listOf("+15551234567"))
        assertTrue(ViewedThread.isOpenFor("+15551234567"))
        assertTrue(ViewedThread.isOpenFor("5551234567"))
        assertFalse(ViewedThread.isOpenFor("+15559998888"))
    }

    @Test
    fun `close of a different conversation leaves the viewed thread`() {
        ViewedThread.open(1L, listOf("+15551234567"))
        ViewedThread.close(2L)
        assertTrue(ViewedThread.isOpenFor("+15551234567"))
        ViewedThread.close(1L)
        assertFalse(ViewedThread.isOpenFor("+15551234567"))
    }

    @Test
    fun `blank senders never match an open thread`() {
        ViewedThread.open(1L, listOf("+15551234567"))
        assertFalse(ViewedThread.isOpenFor(""))
        assertFalse(ViewedThread.isOpenFor("   "))
    }
}
