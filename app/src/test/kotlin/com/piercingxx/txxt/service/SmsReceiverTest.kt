package com.piercingxx.txxt.service

import com.piercingxx.txxt.service.ReceivePolicy.AutoReplyOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies `SmsReceiver`'s auto-reply decision seam (T1).
 *
 * `SmsReceiver.onReceive` (SmsReceiver.kt:48-61) applies `ReceivePolicy.shouldAutoReply`
 * before routing any reply through `SendPipeline`. The Android broadcast dispatch
 * itself (`onReceive`, `Intent`, `Telephony`) is not JVM-testable without Robolectric
 * (not in the offline cache — see the plan's deferred verification), so the box tests
 * the extracted decision seam the receiver actually calls: the auto-reply is off by
 * default (`docs/PRIVACY.md:96`), is drivable on, and a per-contact override can keep
 * a specific sender off. This mirrors the established `MmsReceiverTest` seam.
 */
class SmsReceiverTest {

    @Test
    fun `the auto-reply is off by default even when a per-contact override is on`() {
        // docs/PRIVACY.md:96 — the reply never fires while the global setting is off,
        // regardless of any per-contact override.
        assertFalse(
            ReceivePolicy.shouldAutoReply(
                enabled = false,
                sender = "+15550001111",
                overrides = mapOf("+15550001111" to AutoReplyOverride.ON),
            )
        )
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
    fun `the auto-reply fires when the decision is driven enabled`() {
        // The decision is drivable: with the reply enabled and no override, the
        // receiver's decision seam says the reply goes to the sender.
        assertTrue(
            ReceivePolicy.shouldAutoReply(
                enabled = true,
                sender = "+15550001111",
                overrides = emptyMap(),
            )
        )
    }

    @Test
    fun `a per-contact off override keeps a specific sender off while the reply is enabled`() {
        // Even with the reply enabled globally, a per-contact OFF keeps that sender off.
        assertFalse(
            ReceivePolicy.shouldAutoReply(
                enabled = true,
                sender = "+15550001111",
                overrides = mapOf("+15550001111" to AutoReplyOverride.OFF),
            )
        )
    }
}