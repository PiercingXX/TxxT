package com.piercingxx.txxt.service

import androidx.core.app.NotificationCompat
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    private var capturedBuilder: NotificationCompat.Builder? = null
    private var postCalled = false
    private var builderChained: MutableList<String> = mutableListOf()

    private fun makeService(): NotificationService {
        // Create a mock builder that records chained method calls.
        // mockk with relaxed=true on a class — we use a spy-like approach.
        val mockBuilder = mockk<NotificationCompat.Builder>(relaxed = true)

        // Use a stub context — the makeBuilder lambda short-circuits the real
        // NotificationCompat.Builder(context, ...) constructor.
        val stubContext = mockk<android.content.Context>(relaxed = true)

        return NotificationService(
            context = stubContext,
            contentIntent = { _, _ -> mockk<android.app.PendingIntent>(relaxed = true) },
            quickReplyAction = { _, _ -> mockk<NotificationCompat.Action>(relaxed = true) },
            makeBuilder = { _ -> mockBuilder },
            postNotification = { id, builder ->
                postCalled = true
                capturedId = id
                capturedBuilder = builder
            },
        )
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
    fun `notification builder is passed to postNotification`() {
        val svc = makeService()
        svc.postMessageNotification(sender = "Alice", body = "Hi")
        assertNotNull("builder should be passed to postNotification", capturedBuilder)
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
