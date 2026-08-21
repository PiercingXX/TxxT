package com.piercingxx.txxt.service

import com.piercingxx.txxt.service.ReceivePolicy.AttachmentDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Behaviour-verifies `MmsReceiver`'s audio-MMS drop decision seam (T2).
 *
 * `MmsReceiver.onReceive` (MmsReceiver.kt:22-36) feeds the WAP-push intent's
 * MIME type through `ReceivePolicy.decideAttachment` and drops (aborts) the
 * broadcast when the decision is `DROP_UNSTORED`. The Android broadcast
 * dispatch itself (`onReceive`, `Intent`, `Telephony`) is not JVM-testable
 * without Robolectric (not in the offline cache — see the plan's deferred
 * verification), so the box tests the extracted decision seam the receiver
 * actually calls: an audio MIME type yields `DROP_UNSTORED` (dropped at the
 * inbox boundary, never downloaded, never stored — `docs/PRIVACY.md:91-92`),
 * and any non-audio type yields `STORE`. This mirrors the established
 * `ReceivePolicyTest` / `SmsReceiverTest` seam.
 */
class MmsReceiverTest {

    @Test
    fun `an audio MMS attachment is dropped un-stored`() {
        // docs/PRIVACY.md:91-92 — audio parts are not downloaded and not stored.
        assertEquals(
            AttachmentDecision.DROP_UNSTORED,
            ReceivePolicy.decideAttachment("audio/mpeg"),
        )
        assertEquals(
            AttachmentDecision.DROP_UNSTORED,
            ReceivePolicy.decideAttachment("audio/ogg"),
        )
    }

    @Test
    fun `a non-audio MMS attachment passes through for storage`() {
        // Non-audio parts are normal; the message may be persisted.
        assertEquals(
            AttachmentDecision.STORE,
            ReceivePolicy.decideAttachment("image/jpeg"),
        )
        assertEquals(
            AttachmentDecision.STORE,
            ReceivePolicy.decideAttachment("text/plain"),
        )
    }

    @Test
    fun `an absent attachment type is not treated as audio`() {
        // The WAP-push envelope type is application/vnd.wap.mms-message, never
        // audio/; a null type (no per-part info) must not be dropped.
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment(null))
        assertNotEquals(
            AttachmentDecision.DROP_UNSTORED,
            ReceivePolicy.decideAttachment("application/vnd.wap.mms-message"),
        )
    }
}