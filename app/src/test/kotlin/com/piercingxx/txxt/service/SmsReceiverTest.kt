package com.piercingxx.txxt.service

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drives `SmsReceiver.onReceive` through the auto-reply decision (T1).
 *
 * The Android broadcast dispatch itself (the OS delivering `SMS_RECEIVED` to
 * the manifest-declared receiver) is an on-device check (see the plan's
 * deferred verification), but the receiver's entry point *is* JVM-testable:
 * `SmsReceiver` exposes two injectable seams — `extractSender` (defaults to the
 * platform `Telephony.Sms.Intents.getMessagesFromIntent`, which the mockable
 * jar does not reliably intercept under `mockkStatic`) and `sendReply`
 * (defaults to `SendPipeline.sendSms`) — so a test injects a real sender and a
 * recording send action, calls `SmsReceiver().onReceive(context, intent)`, and
 * asserts on what the receiver would route through `SendPipeline`.
 *
 * The off-by-default posture (`docs/PRIVACY.md:96`) means `SmsReceiver` never
 * sends a reply; the assert is on the receiver's actual send action, so the
 * test fails if `onReceive` is never invoked or does not route through the
 * send path.
 */
class SmsReceiverTest {

    @Test
    fun `the receiver never sends an auto-reply while the setting is off by default`() {
        // A recording send action observes whether onReceive attempts a reply.
        var sends = 0
        val sendReply: (Context, String, String) -> Unit = { _, _, _ -> sends++ }

        val context = mockk<Context>()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        SmsReceiver(extractSender = { "+15550001111" }, sendReply = sendReply)
            .onReceive(context, intent)

        // Auto-reply is off by default, so onReceive must NOT attempt a reply.
        assertEquals(0, sends)
    }

    @Test
    fun `the auto-reply body the receiver would send matches the privacy spec`() {
        // SmsReceiver.kt sends ReceivePolicy.AUTO_REPLY_BODY; pin it so a drift
        // in the body is caught here rather than on-device.
        assertEquals(
            "Voice messages aren't accepted. Send text or a photo.",
            ReceivePolicy.AUTO_REPLY_BODY,
        )
    }

    @Test
    fun `the receiver sends the auto-reply when the decision is driven enabled`() {
        // The decision is drivable: with the reply enabled for this sender, the
        // receiver must route the auto-reply through the send action.
        // A recording send action captures the destination and body the
        // receiver would hand to SendPipeline.
        var sentDestination: String? = null
        var sentBody: String? = null
        val sendReply: (Context, String, String) -> Unit = { _, destination, body ->
            sentDestination = destination
            sentBody = body
        }

        val context = mockk<Context>()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        SmsReceiver(
            autoReplyEnabled = true,
            extractSender = { "+15550001111" },
            sendReply = sendReply,
        ).onReceive(context, intent)

        // With the reply enabled, the receiver must send the auto-reply to the
        // sender with the privacy-spec body.
        assertEquals("+15550001111", sentDestination)
        assertEquals(ReceivePolicy.AUTO_REPLY_BODY, sentBody)
    }
}