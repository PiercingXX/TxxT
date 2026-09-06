package com.piercingxx.txxt.service

import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.block.InboundFilter
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
 * Behaviour-verifies `SmsDeliverReceiver` by driving `onReceive` with real
 * intents over injected extractors/seams (the established
 * `SmsReceiverBlockingTest` pattern — no Robolectric, no platform mocking).
 *
 * The DELIVER path's persistence runs on a background coroutine, so the tests
 * await a latch counted down inside the injected `persist` seam (and, where a
 * reply is asserted, one counted down in the reply seam; where the arrival
 * notification is asserted, one counted down in the `notify` seam) before
 * asserting. Cross-seam ordering uses the shared [Recording.events] log —
 * each seam appends before counting its latch down, so awaiting both latches
 * establishes the happens-before needed to compare the log. Every non-DELIVER
 * path returns before any coroutine is launched, so their "nothing happened"
 * assertions are deterministic after a bounded wait.
 */
class SmsDeliverReceiverTest {

    private val context: Context = mockk(relaxed = true)

    private val deliverAction = "android.provider.Telephony.SMS_DELIVER"

    private val knownDate: Long = 1_700_000_000_000L

    private fun deliverIntent(): Intent =
        spyk(Intent()).apply { every { action } returns deliverAction }

    private class Recording {
        val persisted = mutableListOf<Triple<String, String, Long>>()
        val quarantined = mutableListOf<Triple<String, String, Long>>()
        val replies = mutableListOf<Pair<String, String>>()
        val notified = mutableListOf<Pair<String, String>>()

        /** Ordered cross-seam log ("persist", "notify") for ordering assertions. */
        val events = mutableListOf<String>()
    }

    private fun receiver(
        inboundFilter: InboundFilter = InboundFilter(),
        autoReplyEnabled: Boolean = false,
        autoReplyOverrides: Map<String, ReceivePolicy.AutoReplyOverride> = emptyMap(),
        sender: String? = "+15551234567",
        body: String = "hello there",
        recording: Recording = Recording(),
        persistLatch: CountDownLatch? = null,
        quarantineLatch: CountDownLatch? = null,
        replyLatch: CountDownLatch? = null,
        notifyLatch: CountDownLatch? = null,
        notifyThrows: Boolean = false,
    ): SmsDeliverReceiver =
        SmsDeliverReceiver(
            inboundFilterProvider = { inboundFilter },
            autoReplyEnabled = autoReplyEnabled,
            autoReplyOverrides = autoReplyOverrides,
            extractSender = { sender },
            extractBody = { body },
            extractDate = { knownDate },
            sendReply = { _, to, replyBody ->
                recording.replies.add(to to replyBody)
                replyLatch?.countDown()
            },
            deliverAction = deliverAction,
            persist = { address, persistedBody, date ->
                recording.persisted.add(Triple(address, persistedBody, date))
                recording.events.add("persist")
                persistLatch?.countDown()
            },
            persistQuarantine = { address, persistedBody, date ->
                recording.quarantined.add(Triple(address, persistedBody, date))
                recording.events.add("quarantine")
                quarantineLatch?.countDown()
            },
            notify = { _, from, notifiedBody ->
                if (notifyThrows) throw IllegalStateException("notification seam exploded")
                recording.notified.add(from to notifiedBody)
                recording.events.add("notify")
                notifyLatch?.countDown()
            },
        )

    // ---- DELIVER ----

    @Test
    fun `a delivered SMS persists exactly once and reaches the auto-reply gate`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(knownContacts = setOf("+15551234567")),
            autoReplyEnabled = true,
            recording = recording,
            persistLatch = persisted,
            replyLatch = replied,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(replied.await(5, TimeUnit.SECONDS))
        // Persists once, with the extracted fields carried through untouched.
        assertEquals(1, recording.persisted.size)
        assertEquals(
            Triple("+15551234567", "hello there", knownDate),
            recording.persisted.single(),
        )
        assertEquals(1, recording.replies.size)
        assertEquals("+15551234567", recording.replies.single().first)
        assertEquals(ReceivePolicy.AUTO_REPLY_BODY, recording.replies.single().second)
    }

    @Test
    fun `a delivered SMS notifies exactly once with sender and body after persisting`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val notified = CountDownLatch(1)
        val rcv = receiver(
            recording = recording,
            persistLatch = persisted,
            notifyLatch = notified,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(notified.await(5, TimeUnit.SECONDS))
        // Exactly one notification, carrying the extracted fields untouched.
        assertEquals(1, recording.notified.size)
        assertEquals("+15551234567" to "hello there", recording.notified.single())
        // Ordering: persist FIRST, then notify — never announce a message
        // that failed to store.
        assertEquals(listOf("persist", "notify"), recording.events)
    }

    @Test
    fun `a notify seam failure still finishes the pending result`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val pendingResult = mockk<PendingResult>(relaxed = true)
        every { pendingResult.finish() } answers { finished.countDown() }
        val rcv = spyk(
            receiver(
                recording = recording,
                persistLatch = persisted,
                notifyThrows = true,
            ),
        )
        every { rcv.goAsync() } returns pendingResult

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        // The seam threw after the persist; the exception-handler backstop
        // must not stop the finally block from finishing the broadcast.
        assertTrue(
            "pendingResult.finish() must run even when the notification seam throws",
            finished.await(5, TimeUnit.SECONDS),
        )
        assertEquals(1, recording.persisted.size)
    }

    @Test
    fun `the off-by-default auto-reply gate stays closed while delivery still persists`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(knownContacts = setOf("+15551234567")),
            autoReplyEnabled = false,
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        // Persisted, but the gate (enabled=false) never answers.
        assertEquals(1, recording.persisted.size)
        assertTrue(recording.replies.isEmpty())
    }

    @Test
    fun `a per-contact OFF override suppresses the reply but not the persistence`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(knownContacts = setOf("+15559998888")),
            autoReplyEnabled = true,
            autoReplyOverrides = mapOf("+15559998888" to ReceivePolicy.AutoReplyOverride.OFF),
            sender = "+15559998888",
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertEquals(1, recording.persisted.size)
        assertTrue(recording.replies.isEmpty())
    }

    // ---- BLOCK / QUARANTINE: the only-sink drop ----

    @Test
    fun `a blocked sender neither persists nor replies`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(blockedAddresses = setOf("+15551234567")),
            autoReplyEnabled = true,
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, deliverIntent())

        // BLOCK returns before any async work starts; nothing may land.
        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.replies.isEmpty())
        assertTrue(recording.notified.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `a quarantined unknown sender persists to the hold and does not notify or reply`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val held = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(
                knownContacts = setOf("+15550000000"),
                quarantineUnknownSenders = true,
            ),
            autoReplyEnabled = true,
            sender = "+15559998888",
            recording = recording,
            persistLatch = persisted,
            quarantineLatch = held,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(held.await(5, TimeUnit.SECONDS))
        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertEquals(1, recording.quarantined.size)
        assertEquals(
            Triple("+15559998888", "hello there", knownDate),
            recording.quarantined.single(),
        )
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.replies.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `a starred unknown sender delivers to the inbox not the hold`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val held = CountDownLatch(1)
        val notified = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(
                knownContacts = setOf("+15550000000"),
                starredContacts = setOf("+15559998888"),
                quarantineUnknownSenders = true,
            ),
            sender = "+15559998888",
            recording = recording,
            persistLatch = persisted,
            quarantineLatch = held,
            notifyLatch = notified,
        )

        rcv.onReceive(context, deliverIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(notified.await(5, TimeUnit.SECONDS))
        assertFalse(held.await(200, TimeUnit.MILLISECONDS))
        assertEquals(1, recording.persisted.size)
        assertTrue(recording.quarantined.isEmpty())
    }

    // ---- guards ----

    @Test
    fun `a wrong action is a no-op`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(autoReplyEnabled = true, recording = recording, persistLatch = persisted)

        rcv.onReceive(context, Intent("some.other.action"))

        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.replies.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `a null sender is a no-op`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            autoReplyEnabled = true,
            sender = null,
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, deliverIntent())

        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.replies.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }
}
