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
 *  - REDACTED (default) notifications show title = sender, text = redacted
 *    content (sender name, never the body);
 *  - NOTIFY (opt-in) notifications additionally reveal the message body;
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
    private val cancelledIds = mutableListOf<Int>()
    private var mockBuilder: NotificationCompat.Builder? = null

    private fun resetCaptures() {
        capturedId = -1
        postCalled = false
        cancelledIds.clear()
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
            cancelNotification = { id -> cancelledIds.add(id) },
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
    fun `REDACTED notification text never contains the message body`() {
        val svc = makeService()
        svc.postMessageNotification(
            sender = "Alice",
            body = "Hello",
            globalPosture = NotificationPosture.Posture.REDACTED,
        )
        verify { mockBuilder!!.setContentText(withArg { text ->
            assertFalse("redacted text must never contain the body", text.contains("Hello"))
            assertEquals("redacted text must be the redacted content", "Alice", text)
        }) }
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
        // Opt-in posture: the message body is revealed as the visible text.
        verify { mockBuilder!!.setContentText("Hello") }
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
    fun `dismiss cancels the per-sender notification id`() {
        val svc = makeService()
        svc.dismiss(listOf("Alice", "Bob", ""))
        assertEquals(
            listOf(svc.notificationId("Alice"), svc.notificationId("Bob")),
            cancelledIds,
        )
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

    /**
     * The bundled-sound migration contract: a channel's sound is immutable
     * after creation, so the sound channel moved to a versioned successor id
     * (`_v2`, the xx-phone `ChannelIds` convention) and the retired v1 id is
     * deleted at creation time. These ids are persisted on user devices —
     * pinning the exact strings here makes an accidental rename (which would
     * strand users on a dead channel) a test failure, not a field bug.
     */
    @Test
    fun `sound channel id is the versioned successor of the retired v1 id`() {
        assertEquals("txxt_messages_v2", CHANNEL_ID)
        assertEquals("txxt_messages", LEGACY_CHANNEL_ID_SOUND)
    }

    @Test
    fun `retired v1 channel id is not any live channel id`() {
        // delete(LEGACY) at creation time must never delete a channel we still
        // post on — the retired id has to be disjoint from all three live ids.
        val live = setOf(CHANNEL_ID, CHANNEL_ID_VIBRATE, CHANNEL_ID_SILENT)
        assertFalse(
            "retired id must not collide with a live channel",
            LEGACY_CHANNEL_ID_SOUND in live,
        )
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
