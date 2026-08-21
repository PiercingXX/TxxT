package com.piercingxx.txxt.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationPolicyTest {

    @Test
    fun `notification title is the sender name`() {
        assertEquals("Alice", NotificationPolicy.notificationTitle("Alice"))
        assertEquals("+1234567890", NotificationPolicy.notificationTitle("+1234567890"))
    }

    @Test
    fun `redacted content shows sender name only, never message body`() {
        assertEquals("Alice", NotificationPolicy.redactedContent("Alice"))
        // The message body must not appear in the redacted content
        val sender = "Alice"
        val body = "Hey, can you meet at 5?"
        val redacted = NotificationPolicy.redactedContent(sender)
        assertEquals(sender, redacted)
        assertFalse("redacted content must not contain message body", redacted.contains(body))
    }

    @Test
    fun `nothing ever bubbles`() {
        assertFalse(NotificationPolicy.shouldBubble())
    }
}
