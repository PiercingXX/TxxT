package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Behaviour-verifies `SendPipeline`'s no-delivery/read-report send request (T3).
 *
 * `SendPipeline.sendSms` (SendPipeline.kt:26-33) passes `null` for both the
 * sent and delivery `PendingIntent`s to `SmsManager.sendTextMessage`, and
 * `SendPipeline.sendMms` (SendPipeline.kt:44-51) passes `null` for the sent
 * `PendingIntent` to `sendMultimediaMessage` — so the carrier is never asked to
 * report delivery or read (`docs/PRIVACY.md:23,32`). The Android `SmsManager`
 * / `PendingIntent` dispatch itself is not JVM-testable without Robolectric
 * (not in the offline cache — see the plan's deferred verification), so the box
 * tests the extracted decision seam the pipeline actually consumes:
 * `SendPolicy.requestsDeliveryReport()` and `requestsReadReport()` are always
 * `false`, which is exactly why the pipeline passes `null` PendingIntents. This
 * mirrors the established `SendPolicyTest` / `SmsReceiverTest` seam.
 */
class SendPipelineTest {

    @Test
    fun `an outgoing SMS never requests a delivery report`() {
        // SendPipeline.sendSms passes a null delivery PendingIntent because the
        // policy never requests a delivery report (docs/PRIVACY.md:23).
        assertFalse(SendPolicy.requestsDeliveryReport())
    }

    @Test
    fun `an outgoing MMS never requests a read report`() {
        // SendPipeline.sendMms passes a null sent PendingIntent because the
        // policy never requests a read report (docs/PRIVACY.md:23).
        assertFalse(SendPolicy.requestsReadReport())
    }
}