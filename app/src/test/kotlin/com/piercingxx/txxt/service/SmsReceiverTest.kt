package com.piercingxx.txxt.service

import com.piercingxx.txxt.service.ReceivePolicy.AutoReplyOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Behaviour-verifies `SmsReceiver`'s auto-reply decision seam (T1).
 *
 * `SmsReceiver.onReceive` (SmsReceiver.kt:21-35) encodes the off-by-default
 * posture from `docs/PRIVACY.md:96`: it hardcodes `autoReplyEnabled = false`
 * and feeds that through `ReceivePolicy.shouldAutoReply`. The Android broadcast
 * dispatch itself (`onReceive`, `Intent`, `Telephony`) is not JVM-testable
 * without Robolectric (not in the offline cache — see the plan's deferred
 * verification), so the box tests the extracted decision seam the receiver
 * actually calls: with `enabled = false` the auto-reply decision is always
 * `false`, for any sender and any per-contact override, so the receiver never
 * sends a reply. This mirrors the established `ReceivePolicyTest` seam.
 */
class SmsReceiverTest {

    @Test
    fun `the receiver's off-by-default posture never triggers an auto-reply`() {
        // SmsReceiver.kt hardcodes autoReplyEnabled = false; the policy must
        // therefore always decline the reply, regardless of sender.
        assertFalse(ReceivePolicy.shouldAutoReply(enabled = false, sender = "alice"))
        assertFalse(ReceivePolicy.shouldAutoReply(enabled = false, sender = "bob"))
    }

    @Test
    fun `a per-contact override cannot enable the reply while the setting is off`() {
        // Even an explicit ON override for the sender must not fire the reply
        // while the global off-by-default posture holds (docs/PRIVACY.md:96).
        assertFalse(
            ReceivePolicy.shouldAutoReply(
                enabled = false,
                sender = "alice",
                overrides = mapOf("alice" to AutoReplyOverride.ON),
            )
        )
    }

    @Test
    fun `the auto-reply body the receiver would send matches the privacy spec`() {
        // SmsReceiver.kt:33 sends ReceivePolicy.AUTO_REPLY_BODY; pin it so a
        // drift in the body is caught here rather than on-device.
        assertEquals(
            "Voice messages aren't accepted. Send text or a photo.",
            ReceivePolicy.AUTO_REPLY_BODY,
        )
    }
}