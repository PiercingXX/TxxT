package com.piercingxx.txxt.block

import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.service.ReceivePolicy
import com.piercingxx.txxt.service.SmsReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsReceiverBlockingTest {

    private val context: Context = mockk(relaxed = true)

    // ---- Helper: build an SmsReceiver with injectable extractors ----

    private fun receiver(
        inboundFilter: InboundFilter = InboundFilter(),
        autoReplyEnabled: Boolean = false,
        autoReplyOverrides: Map<String, ReceivePolicy.AutoReplyOverride> = emptyMap(),
        sender: String? = "+1 555 1000",
        body: String = "Hello",
    ): Triple<SmsReceiver, MutableList<String>, MutableList<Pair<String, String>>> {
        val repliesSent = mutableListOf<Pair<String, String>>()
        val contextsSeen = mutableListOf<String>()

        val rcv = SmsReceiver(
            inboundFilter = inboundFilter,
            autoReplyEnabled = autoReplyEnabled,
            autoReplyOverrides = autoReplyOverrides,
            extractSender = { intent ->
                contextsSeen.add(intent.`package` ?: "null")
                sender
            },
            extractBody = { body },
            sendReply = { _, to, message ->
                repliesSent.add(to to message)
            },
            smsAction = "android.provider.Telephony.SMS_RECEIVED",
        )
        return Triple(rcv, contextsSeen, repliesSent)
    }

    // ---- Helper: build an SMS_RECEIVED intent ----

    private fun smsIntent(): Intent =
        spyk(Intent()).apply {
            every { action } returns "android.provider.Telephony.SMS_RECEIVED"
            `package` = "test"
        }

    // ---- BLOCK: blocked address drops the message ----

    @Test
    fun `blocked address does not trigger auto-reply`() {
        val filter = InboundFilter(blockedAddresses = setOf("+1 555 1000"))
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
        )
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    @Test
    fun `blocked address with content keyword both block independently`() {
        // Blocked address blocks even without keyword match
        val filter = InboundFilter(
            knownContacts = setOf("+1 555 1000"),
            blockedAddresses = setOf("+1 555 1000"),
        )
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
            body = "clean message",
        )
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- QUARANTINE: unknown sender drops the message ----

    @Test
    fun `unknown sender is quarantined and does not trigger auto-reply`() {
        val filter = InboundFilter(knownContacts = setOf("+1 555 2000"))
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 9999",
        )
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- DELIVER: known sender passes through ----

    @Test
    fun `known sender with clean message is delivered and auto-reply works`() {
        val filter = InboundFilter(knownContacts = setOf("+1 555 1000"))
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
        )
        rcv.onReceive(context, smsIntent())
        assertEquals(1, replies.size)
        assertEquals("+1 555 1000", replies[0].first)
        assertEquals(ReceivePolicy.AUTO_REPLY_BODY, replies[0].second)
    }

    @Test
    fun `known sender with auto-reply off does not send reply`() {
        val filter = InboundFilter(knownContacts = setOf("+1 555 1000"))
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = false,
            sender = "+1 555 1000",
        )
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- Content filter: keyword match blocks ----

    @Test
    fun `keyword match blocks the message`() {
        val filter = InboundFilter(
            knownContacts = setOf("+1 555 1000"),
            contentKeywords = setOf("loan"),
        )
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
            body = "Get a loan today",
        )
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- Starred contact bypass ----

    @Test
    fun `starred contact bypasses block and reaches auto-reply`() {
        val filter = InboundFilter(
            blockedAddresses = setOf("+1 555 1000"),
            starredContacts = setOf("+1 555 1000"),
        )
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
        )
        rcv.onReceive(context, smsIntent())
        assertEquals(1, replies.size)
        assertEquals("+1 555 1000", replies[0].first)
    }

    // ---- Wrong action is ignored ----

    @Test
    fun `non-SMS action is ignored`() {
        val (rcv, _, replies) = receiver(autoReplyEnabled = true)
        rcv.onReceive(context, Intent("some.other.action"))
        assertTrue(replies.isEmpty())
    }

    // ---- Null sender is ignored ----

    @Test
    fun `null sender is ignored`() {
        val (rcv, _, replies) = receiver(sender = null)
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- Empty body with known sender ----

    @Test
    fun `empty body with known sender is delivered`() {
        val filter = InboundFilter(knownContacts = setOf("+1 555 1000"))
        val (rcv, _, replies) = receiver(
            inboundFilter = filter,
            autoReplyEnabled = true,
            sender = "+1 555 1000",
            body = "",
        )
        rcv.onReceive(context, smsIntent())
        assertEquals(1, replies.size)
    }
}
