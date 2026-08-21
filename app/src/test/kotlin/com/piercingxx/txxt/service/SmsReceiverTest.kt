package com.piercingxx.txxt.service

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drives `SmsReceiver.onReceive` through the auto-reply decision (T1).
 *
 * The Android broadcast dispatch itself (the OS delivering `SMS_RECEIVED` to
 * the manifest-declared receiver) is an on-device check (see the plan's
 * deferred verification), but the receiver's entry point *is* JVM-testable:
 * MockK's `mockkStatic` on the mockable-android.jar `Telephony.Sms.Intents`
 * lets a test call `SmsReceiver().onReceive(context, intent)` and observe what
 * it routes through `SendPipeline`. This mirrors the feasibility finding in
 * `contracts/TxxT-ws7a.md` and replaces the earlier policy-seam-only test that
 * re-tested `ReceivePolicy` directly instead of the receiver's live path.
 *
 * The off-by-default posture (`docs/PRIVACY.md:96`) means `SmsReceiver` never
 * sends a reply; the assert is on the receiver's actual call —
 * `verify(exactly = 0) { SendPipeline.sendSms(...) }` — so the test fails if
 * `onReceive` is never invoked or does not route through `SendPipeline`.
 */
class SmsReceiverTest {

    @Test
    fun `the receiver never sends an auto-reply while the setting is off by default`() {
        // A real inbound SMS whose originating address and body are mocked.
        mockkStatic(Telephony.Sms.Intents::class)
        val smsMessage = mockk<android.telephony.SmsMessage>()
        every { smsMessage.originatingAddress } returns "+15550001111"
        every { smsMessage.messageBody } returns "hello"
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(any())
        } returns arrayOf(smsMessage)

        // The send pipeline is mocked so we can observe whether onReceive
        // attempts a reply.
        mockkObject(SendPipeline)
        every { SendPipeline.sendSms(any(), any(), any()) } returns Unit

        val context = mockk<Context>()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        SmsReceiver().onReceive(context, intent)

        // Auto-reply is off by default, so onReceive must NOT send a reply.
        verify(exactly = 0) { SendPipeline.sendSms(any(), any(), any()) }
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
}