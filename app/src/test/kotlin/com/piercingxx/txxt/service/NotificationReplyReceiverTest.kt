package com.piercingxx.txxt.service

import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Behaviour-verifies [NotificationReplyReceiver]'s persist-then-send contract
 * by driving `onReceive` over injected extractors/seams (the established
 * `SmsDeliverReceiver` pattern — no Robolectric, no platform mocking, no
 * static `RemoteInput.getResultsFromIntent`).
 *
 * The receiver's work runs on a background coroutine after `goAsync`, so the
 * tests await latches counted down inside the seams (each seam appends to the
 * shared [Recording.events] log BEFORE counting down, giving the happens-before
 * needed to compare cross-seam ordering). Guards (missing/blank extraction)
 * return before any coroutine starts, so their "nothing happened" assertions
 * are deterministic after a bounded wait.
 *
 * Contract under test: a quick reply is PERSISTED first (`sent = false`,
 * visible in thread history, re-drivable by RebootReconcile), THEN sent, THEN
 * marked sent only on success — never the old send-only flow whose replies
 * were invisible in their own thread.
 */
class NotificationReplyReceiverTest {

    private val context: Context = mockk(relaxed = true)

    private class Recording {
        val persisted = mutableListOf<Pair<String, String>>()
        val sent = mutableListOf<Pair<String, String>>()
        val markedSent = mutableListOf<Long>()

        /** Ordered cross-seam log ("persist", "send", "markSent"). */
        val events = mutableListOf<String>()
    }

    private fun receiver(
        recording: Recording,
        extracted: Pair<String, String>? = "+15551234567" to "quick reply",
        sendOk: Boolean = true,
        persistLatch: CountDownLatch? = null,
        sentLatch: CountDownLatch? = null,
        markSentLatch: CountDownLatch? = null,
    ): NotificationReplyReceiver =
        NotificationReplyReceiver(
            extractReply = { extracted },
            send = { _, recipient, body ->
                recording.sent.add(recipient to body)
                recording.events.add("send")
                sentLatch?.countDown()
                sendOk
            },
            persist = { address, body ->
                recording.persisted.add(address to body)
                recording.events.add("persist")
                persistLatch?.countDown()
                4_242L
            },
            markSent = { id ->
                recording.markedSent.add(id)
                recording.events.add("markSent")
                markSentLatch?.countDown()
            },
        )

    private fun withGoAsyncStub(
        receiver: NotificationReplyReceiver,
        finished: CountDownLatch?,
    ): NotificationReplyReceiver {
        val pendingResult = mockk<PendingResult>(relaxed = true)
        every { pendingResult.finish() } answers { finished?.countDown() }
        return spyk(receiver).also { every { it.goAsync() } returns pendingResult }
    }

    // ---- happy path ----

    @Test
    fun `a quick reply persists unsent then sends then marks sent`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val sent = CountDownLatch(1)
        val marked = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val rcv = withGoAsyncStub(
            receiver(recording, persistLatch = persisted, sentLatch = sent, markSentLatch = marked),
            finished,
        )

        rcv.onReceive(context, Intent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(sent.await(5, TimeUnit.SECONDS))
        assertTrue(marked.await(5, TimeUnit.SECONDS))
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        // Extracted fields carried through untouched.
        assertEquals("+15551234567" to "quick reply", recording.persisted.single())
        assertEquals("+15551234567" to "quick reply", recording.sent.single())
        // Ordering: persist (sent=false) FIRST, send SECOND, mark sent LAST —
        // exactly the compose path's contract.
        assertEquals(listOf("persist", "send", "markSent"), recording.events)
        assertEquals(listOf(4_242L), recording.markedSent)
    }

    // ---- gate denial ----

    @Test
    fun `a denied send leaves the row pending - markSent is never invoked`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val sent = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val rcv = withGoAsyncStub(
            receiver(
                recording,
                sendOk = false,
                persistLatch = persisted,
                sentLatch = sent,
            ),
            finished,
        )

        rcv.onReceive(context, Intent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(sent.await(5, TimeUnit.SECONDS))
        // The row stays unsent so RebootReconcile can re-drive it; the broadcast
        // still finishes cleanly.
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("persist", "send"), recording.events)
        assertTrue(recording.markedSent.isEmpty())
    }

    // ---- guards: nothing async, nothing written ----

    @Test
    fun `a blank reply text persists nothing and sends nothing`() {
        val recording = Recording()
        val finished = CountDownLatch(1)
        val rcv = withGoAsyncStub(
            receiver(recording, extracted = "+15551234567" to "   "),
            finished,
        )

        rcv.onReceive(context, Intent())

        // Guard returns BEFORE goAsync — no coroutine, no writes, no dispatch.
        assertFalse(finished.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.sent.isEmpty())
        assertTrue(recording.markedSent.isEmpty())
        assertTrue(recording.events.isEmpty())
    }

    @Test
    fun `a missing extraction (no RemoteInput results) persists nothing and sends nothing`() {
        val recording = Recording()
        val finished = CountDownLatch(1)
        val rcv = withGoAsyncStub(
            receiver(recording, extracted = null),
            finished,
        )

        rcv.onReceive(context, Intent())

        assertFalse(finished.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.sent.isEmpty())
        assertTrue(recording.markedSent.isEmpty())
        assertTrue(recording.events.isEmpty())
    }

    @Test
    fun `a blank sender persists nothing and sends nothing`() {
        val recording = Recording()
        val finished = CountDownLatch(1)
        val rcv = withGoAsyncStub(
            receiver(recording, extracted = "" to "hello"),
            finished,
        )

        rcv.onReceive(context, Intent())

        assertFalse(finished.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.sent.isEmpty())
        assertTrue(recording.events.isEmpty())
    }

    // ---- backstop ----

    @Test
    fun `a persist seam failure still finishes the pending result`() {
        val recording = Recording()
        val failed = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val pendingResult = mockk<PendingResult>(relaxed = true)
        every { pendingResult.finish() } answers { finished.countDown() }
        val base = NotificationReplyReceiver(
            extractReply = { "+15551234567" to "boom" },
            send = { _, _, _ -> true },
            persist = { _, _ ->
                recording.events.add("persist")
                failed.countDown()
                throw IllegalStateException("db exploded")
            },
            markSent = { _ ->
                recording.events.add("markSent")
            },
        )
        val rcv = spyk(base).also { every { it.goAsync() } returns pendingResult }

        rcv.onReceive(context, Intent())

        assertTrue(failed.await(5, TimeUnit.SECONDS))
        // The exception backstop must not stop finally from finishing.
        assertTrue(
            "pendingResult.finish() must run even when the persist seam throws",
            finished.await(5, TimeUnit.SECONDS),
        )
        assertTrue(recording.events == listOf("persist"))
    }
}
