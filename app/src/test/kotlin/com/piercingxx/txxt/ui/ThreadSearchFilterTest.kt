package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadSearchFilterTest {

    private fun msg(id: Long, body: String) = Message(
        id = id,
        conversationId = 1L,
        direction = MessageDirection.INCOMING,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = id,
    )

    @Test
    fun `blank query returns every message`() {
        val messages = listOf(msg(1, "hello"), msg(2, "world"))
        assertEquals(messages, ThreadSearchFilter.filter(messages, "  "))
    }

    @Test
    fun `query matches body case-insensitively`() {
        val messages = listOf(msg(1, "Hello there"), msg(2, "nope"))
        assertEquals(listOf(messages[0]), ThreadSearchFilter.filter(messages, "HELLO"))
    }
}
