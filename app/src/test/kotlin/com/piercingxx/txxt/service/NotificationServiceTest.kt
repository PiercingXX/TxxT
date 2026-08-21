package com.piercingxx.txxt.service

import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour-verifies [NotificationService] (T2):
 *  - Posts sender-name-only notifications (title = sender, text = redacted);
 *  - Includes a quick-reply action;
 *  - Never attaches bubble metadata;
 *  - Respects the [NotificationPosture] decision (suppress, redact, notify).
 *
 * The Android [android.content.Context] / [android.app.NotificationManager]
 * dispatch is not JVM-testable without Robolectric (not in the offline cache),
 * so the box tests the extracted decision seam: the posture decision, the
 * policy-derived title/text, and the notification builder construction.
 * This mirrors the established [SmsReceiverTest] / [SendPipelineTest] seam pattern.
 */
class NotificationServiceTest {

    private var capturedId: Int = -1
    private var postCalled = false
    private var mockBuilder: NotificationCompat.Builder? = null

    private fun resetCaptures() {
        capturedId = -1
        postCalled = false
        mockBuilder = null
    }

    private fun makeService(): NotificationService {
        // Relaxed mock builder that returns itself on chained calls so
        // verify() sees the calls on the same instance.
        val builder = mockk<NotificationCompat.Builder>(relaxed = true)
        mockBuilder = builder

        // Configure all builder methods to return the same mock so chaining works.
        io.mockk.every { builder.setSmallIcon(any<Int>()) } answers { builder }
        io.mockk.every { builder.setContentTitle(any()) } answers { builder }
        io.mockk.every { builder.setContentText(any()) } answers { builder }
        io.mockk.every { builder.setContentIntent(any()) } answers { builder }
        io.mockk.every { builder.setAutoCancel(any()) } answers { builder }
        io.mockk.every { builder.addAction(any()) } answers { builder }

        return NotificationService(
            context = mockk(relaxed = true),
            contentIntent = { _, _ -> mockk(relaxed = true) },
            quickReplyAction = { _, _ -> mockk(relaxed = true) },
            makeBuilder = { _ -> builder },
            postNotification = { id, _ ->
                postCalled = true
                capturedId = id
            },
        )
    }

    @Before
    fun setUp() {
        resetCaptures()
    }

    @Test
    fun `notification is posted for REDACTED posture`() {
        val svc = makeService()
        val result = svc.postMessageNotification(
            sender = "Alice",
            body = "Hello",
            globalPosture = NotificationPosture.Posture.REDACTED,
        )
        assertTrue("should return true", result)
        assertTrue("postNotification should be called", postCalled)
        // Verify notification content — title and text come from policy
        verify { mockBuilder!!.setContentTitle("Alice") }
        verify { mockBuilder!!.setContentText("Alice") }
    }

    @Test
    fun `notification is posted for NOTIFY posture`() {
        val svc = makeService()
        val result = svc.postMessageNotification(
            sender = "Alice",
            body = "Hello",
            globalPosture = NotificationPosture.Posture.NOTIFY,
        )
        assertTrue("should return true", result)
        assertTrue("postNotification should be called", postCalled)
        verify { mockBuilder!!.setContentTitle("Alice") }
        verify { mockBuilder!!.setContentText("Alice") }
    }

    @Test
    fun `suppressed posture does not post a notification`() {
        val svc = makeService()
        val result = svc.postMessageNotification(
            sender = "SpamBot",
            body = "Buy now!",
            globalPosture = NotificationPosture.Posture.SUPPRESS,
        )
        assertFalse("suppressed messages should not post", result)
        assertFalse("postNotification should not be called", postCalled)
    }

    @Test
    fun `starred contacts always notify even with SUPPRESS global posture`() {
        val svc = makeService()
        val result = svc.postMessageNotification(
            sender = "Emergency",
            body = "Urgent",
            starred = true,
            globalPosture = NotificationPosture.Posture.SUPPRESS,
        )
        assertTrue("starred bypasses suppress", result)
        assertTrue("notification should be posted", postCalled)
    }

    @Test
    fun `per-contact override beats global posture`() {
        val svc = makeService()
        val result = svc.postMessageNotification(
            sender = "Boss",
            body = "Meeting at 3",
            starred = false,
            globalPosture = NotificationPosture.Posture.SUPPRESS,
            overrides = mapOf("Boss" to NotificationPosture.Override.NOTIFY),
        )
        assertTrue("per-contact NOTIFY override beats global SUPPRESS", result)
        assertTrue("notification should be posted", postCalled)
    }

    @Test
    fun `notification id is derived from the sender`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        assertEquals(svc.notificationId("Alice"), capturedId)
    }

    @Test
    fun `notification includes quick-reply action`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        // Verify addAction was called (the quick-reply action)
        verify { mockBuilder!!.addAction(any()) }
    }

    @Test
    fun `notification has content intent set`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        verify { mockBuilder!!.setContentIntent(any()) }
    }

    @Test
    fun `notification is auto-cancel`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        verify { mockBuilder!!.setAutoCancel(true) }
    }

    @Test
    fun `notification has small icon set`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        verify { mockBuilder!!.setSmallIcon(android.R.drawable.sym_action_email) }
    }

    @Test
    fun `nothing ever bubbles — policy always false`() {
        assertFalse(NotificationPolicy.shouldBubble())
    }

    @Test
    fun `title and text come from NotificationPolicy`() {
        assertEquals("Alice", NotificationPolicy.notificationTitle("Alice"))
        assertEquals("Alice", NotificationPolicy.redactedContent("Alice"))
        val body = "Secret message"
        val redacted = NotificationPolicy.redactedContent("Alice")
        assertFalse("redacted must not contain body", redacted.contains(body))
    }
}
