package com.piercingxx.txxt.service

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
 * Behaviour-verifies `MmsDeliverReceiver` by driving `onReceive` with real
 * intents and crafted PDU bytes over injected seams (no Robolectric). The PDUs
 * are hand-built in the OMA-MMS-ENC subset `core/MmsPduHeader.parse` supports:
 * leading message-type octet, FROM (Value-length span, address-present token,
 * charset octet, null-terminated text), DATE (Long-integer seconds), CONTENT-TYPE
 * (extension-media), END_OF_HEADER.
 *
 * The STORE path persists on a background coroutine, so its tests await a latch
 * counted down inside the injected `persist` seam (and, where the arrival
 * notification is asserted, one counted down in the `notify` seam). Every drop
 * path (parse-null,
 * missing PDU, blocked sender, audio content type) returns before any coroutine
 * is launched, so their "nothing stored" assertions are deterministic after a
 * bounded wait.
 */
class MmsDeliverReceiverTest {

    private val context: Context = mockk(relaxed = true)

    private val wapPushDeliver = "android.provider.Telephony.WAP_PUSH_DELIVER"

    private val knownDateSeconds: Long = 1_700_000_000L

    /** Builds a parseable m-notification-ind PDU for the given FROM/content-type. */
    private fun pdu(
        from: String,
        contentType: String = "application/vnd.wap.multipart.related",
        dateSeconds: Long = knownDateSeconds,
    ): ByteArray {
        val bytes = mutableListOf<Byte>()
        // X-Mms-Message-Type value octet: 0x82 = m-notification-ind.
        bytes.add(0x82.toByte())
        // FROM: field name 0x89, Short-length span, address-present token 0x80,
        // UTF-8 charset octet 0xEA, null-terminated US-ASCII address.
        val fromText = from.toByteArray(Charsets.US_ASCII)
        val fromSpan = byteArrayOf(0x80.toByte(), 0xEA.toByte()) + fromText + byteArrayOf(0x00)
        check(fromSpan.size <= 30) { "fixture span exceeds Short-length range" }
        bytes.add(0x89.toByte())
        bytes.add(fromSpan.size.toByte())
        bytes.addAll(fromSpan.toList())
        // DATE: field name 0x85, Long-integer length 8, big-endian epoch seconds.
        bytes.add(0x85.toByte())
        bytes.add(0x08.toByte())
        for (shift in 7 downTo 0) {
            bytes.add((dateSeconds shr (shift * 8)).toByte())
        }
        // CONTENT-TYPE: field name 0x84, extension-media null-terminated string.
        bytes.add(0x84.toByte())
        bytes.addAll(contentType.toByteArray(Charsets.US_ASCII).toList())
        bytes.add(0x00)
        return bytes.toByteArray()
    }

    private class Recording {
        val persisted = mutableListOf<Pair<String, Long>>()
        val notified = mutableListOf<Pair<String, String>>()
    }

    private fun receiver(
        inboundFilter: InboundFilter = InboundFilter(),
        pduBytes: ByteArray? = pdu(from = "+15551234567"),
        recording: Recording = Recording(),
        persistLatch: CountDownLatch? = null,
        notifyLatch: CountDownLatch? = null,
    ): MmsDeliverReceiver =
        MmsDeliverReceiver(
            inboundFilterProvider = { inboundFilter },
            extractPdu = { pduBytes },
            mmsDeliverAction = wapPushDeliver,
            persist = { address, date ->
                recording.persisted.add(address to date)
                persistLatch?.countDown()
            },
            notify = { _, from, body ->
                recording.notified.add(from to body)
                notifyLatch?.countDown()
            },
        )

    private fun pushIntent(): Intent =
        spyk(Intent()).apply { every { action } returns wapPushDeliver }

    // ---- STORE ----

    @Test
    fun `a parsed notification persists metadata once`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val notified = CountDownLatch(1)
        val rcv = receiver(recording = recording, persistLatch = persisted, notifyLatch = notified)

        rcv.onReceive(context, pushIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertEquals(1, recording.persisted.size)
        assertEquals("+15551234567", recording.persisted.single().first)
        assertEquals(knownDateSeconds * 1000L, recording.persisted.single().second)
    }

    @Test
    fun `a stored MMS notifies exactly once with an empty body after persisting`() {
        // The row is metadata-only — no content is ever fetched here — so the
        // notification seam is handed body="" (under the default REDACTED
        // posture the visible text derives from the sender alone anyway; see
        // MmsDeliverReceiver's class KDoc for the honest NOTIFY-posture limit).
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val notified = CountDownLatch(1)
        val rcv = receiver(recording = recording, persistLatch = persisted, notifyLatch = notified)

        rcv.onReceive(context, pushIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertTrue(notified.await(5, TimeUnit.SECONDS))
        assertEquals(1, recording.notified.size)
        assertEquals("+15551234567" to "", recording.notified.single())
    }

    @Test
    fun `a PLMN-suffixed FROM is stripped before storing`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            pduBytes = pdu(from = "+15551234567/TYPE=PLMN"),
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, pushIntent())

        assertTrue(persisted.await(5, TimeUnit.SECONDS))
        assertEquals(listOf<Pair<String, Long>>("+15551234567" to (knownDateSeconds * 1000L)), recording.persisted)
    }

    // ---- fail closed ----

    @Test
    fun `an unparseable PDU stores nothing`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        // First octet < 0x80 can be neither a message-type value nor the 0x8C
        // field name — MmsPduHeader.parse fails closed on it.
        val rcv = receiver(pduBytes = byteArrayOf(0x01, 0x02), recording = recording, persistLatch = persisted)

        rcv.onReceive(context, pushIntent())

        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.notified.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `a missing PDU stores nothing`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(pduBytes = null, recording = recording, persistLatch = persisted)

        rcv.onReceive(context, pushIntent())

        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    // ---- filter / attachment policy drops ----

    @Test
    fun `a blocked sender stores nothing`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            inboundFilter = InboundFilter(blockedAddresses = setOf("+15551234567")),
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, pushIntent())

        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `an audio content type stores nothing`() {
        val recording = Recording()
        val persisted = CountDownLatch(1)
        val rcv = receiver(
            pduBytes = pdu(from = "+15551234567", contentType = "audio/mpeg"),
            recording = recording,
            persistLatch = persisted,
        )

        rcv.onReceive(context, pushIntent())

        // Voice messages are never received or stored (docs/PRIVACY.md §5).
        assertFalse(persisted.await(200, TimeUnit.MILLISECONDS))
        assertTrue(recording.persisted.isEmpty())
        assertTrue(recording.notified.isEmpty())
        assertTrue(recording.notified.isEmpty())
    }

    @Test
    fun `the cleanAddress helper strips any TYPE suffix`() {
        assertEquals("+15551234567", MmsDeliverReceiver.cleanAddress("+15551234567/TYPE=PLMN"))
        assertEquals("+15559998888", MmsDeliverReceiver.cleanAddress("+15559998888/TYPE=IPv4"))
        assertEquals("+15550000000", MmsDeliverReceiver.cleanAddress("+15550000000"))
    }
}
