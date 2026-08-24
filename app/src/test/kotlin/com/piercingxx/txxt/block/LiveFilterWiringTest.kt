package com.piercingxx.txxt.block

import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.service.ReceivePolicy
import com.piercingxx.txxt.service.SmsReceiver
import com.piercingxx.txxt.ui.SettingsBlocking
import com.piercingxx.txxt.ui.SettingsStarred
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS12-corrective T2 — the inbound receivers apply the live filter.
 *
 * The finding's closing defect: even after T1 builds the filter, the
 * manifest-declared inbound receivers defaulted to an empty `InboundFilter()`
 * and ignored the live filter entirely. This test drives `SmsReceiver.onReceive`
 * **by name** with the receiver's **default** `inboundFilter` (no explicit
 * filter passed) so it reads `LiveInboundFilter.current` at construction, and
 * asserts the live filter's decisions reach the inbound path.
 *
 * This would have failed before the fix: the receiver defaulted to
 * `InboundFilter()` and ignored the live filter, so a blocked address would not
 * have been dropped.
 */
class LiveFilterWiringTest {

    private val context: Context = mockk(relaxed = true)

    private fun smsIntent(): Intent =
        spyk(Intent()).apply {
            every { action } returns "android.provider.Telephony.SMS_RECEIVED"
            `package` = "test"
        }

    /**
     * Builds an [SmsReceiver] with the **default** `inboundFilterProvider` (no
     * explicit filter argument), so `onReceive` resolves
     * [LiveInboundFilter.current] after `ensureLoaded`. The caller must call
     * [LiveInboundFilter.apply] before driving `onReceive` (which also marks
     * the store loaded, so hydration cannot clobber the applied filter).
     */
    private fun receiver(
        autoReplyEnabled: Boolean,
        sender: String,
        body: String = "Hello",
    ): Pair<SmsReceiver, MutableList<Pair<String, String>>> {
        val replies = mutableListOf<Pair<String, String>>()
        val rcv = SmsReceiver(
            autoReplyEnabled = autoReplyEnabled,
            extractSender = { sender },
            extractBody = { body },
            sendReply = { _, to, message -> replies.add(to to message) },
            smsAction = "android.provider.Telephony.SMS_RECEIVED",
        )
        return rcv to replies
    }

    // ---- Blocked address in the live filter is dropped ----

    @Test
    fun `blocked address in the live filter is dropped - no auto-reply`() {
        LiveInboundFilter.apply(
            SettingsBlocking().blockAddress("+1 555 8888"),
            SettingsStarred(),
        )
        val (rcv, replies) = receiver(autoReplyEnabled = true, sender = "+1 555 8888")
        rcv.onReceive(context, smsIntent())
        assertTrue(replies.isEmpty())
    }

    // ---- Control: a deliverable sender in the live filter reaches auto-reply ----

    // The live filter built by SettingsBlocking.filter() carries no known-contact
    // set (SettingsActivity applies it with the default empty knownContacts), so
    // the only way a sender DELIVERs through the live filter is the starred
    // bypass. Starred is the settings-screen's call-through list, so this is the
    // faithful control for "the live filter delivers".
    @Test
    fun `starred sender in the live filter is delivered - reply sent`() {
        LiveInboundFilter.apply(
            SettingsBlocking(),
            SettingsStarred().star("+1 555 2000"),
        )
        val (rcv, replies) = receiver(autoReplyEnabled = true, sender = "+1 555 2000")
        rcv.onReceive(context, smsIntent())
        assertEquals(1, replies.size)
        assertEquals("+1 555 2000", replies[0].first)
        assertEquals(ReceivePolicy.AUTO_REPLY_BODY, replies[0].second)
    }
}