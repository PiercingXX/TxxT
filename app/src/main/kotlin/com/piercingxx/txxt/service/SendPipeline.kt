package com.piercingxx.txxt.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager

/**
 * The send pipeline: wraps [SmsManager] to send SMS and MMS.
 *
 * Consumes the T2 send policy — an outgoing SMS/MMS **never requests a delivery
 * or read report** (`docs/PRIVACY.md:23`), so the delivery/read `PendingIntent`s
 * are always `null` — and the MMS download policy (no auto-download; remote
 * content is fetched only on explicit tap, `docs/PRIVACY.md:151-153`). Every
 * send is gated by [PermissionGate] (T1): a denied `SEND_SMS` permission is
 * reported and the send is skipped, never silently dropped.
 */
object SendPipeline {

    /**
     * Sends a text SMS to [destination] with [body].
     *
     * The delivery-report `PendingIntent` is `null` (`SendPolicy.requestsDeliveryReport()`
     * is always `false`), so the carrier is never asked to report delivery.
     *
     * The send is gated by [PermissionGate] (T1): if the `SEND_SMS` permission is
     * denied, the gate says so and the send is skipped — an outgoing message never
     * silently fails to send. [gate] defaults to the real permission check, so the
     * running call sites (`ThreadActivity.sendComposed`, the notification quick
     * reply) reach it automatically.
     */
    fun sendSms(context: Context, destination: String, body: String, gate: PermissionGate = PermissionGate()) {
        if (!gate.canSend(context)) return
        val smsManager = SmsManager.getDefault()
        // scAddress null = use the device default; sent/delivery null = never
        // request a delivery or read report (`docs/PRIVACY.md:23`).
        smsManager.sendTextMessage(destination, null, body, null, null)
    }

    /**
     * Sends an MMS whose body lives at [contentUri].
     *
     * The sent `PendingIntent` is `null` (`SendPolicy.requestsReadReport()` is
     * always `false`), so a read report is never requested. The MMS send itself
     * is initiated only on explicit user action — never automatically.
     *
     * The send is gated by [PermissionGate] (T1), exactly like [sendSms]: a
     * denied `SEND_SMS` permission reports and skips the send instead of
     * silently failing.
     */
    fun sendMms(context: Context, contentUri: Uri, gate: PermissionGate = PermissionGate()) {
        if (!gate.canSend(context)) return
        val smsManager = SmsManager.getDefault()
        val configOverrides: Bundle? = null
        // sentIntent null = never request a read report (`docs/PRIVACY.md:23`).
        smsManager.sendMultimediaMessage(context, contentUri, null, configOverrides, null)
    }
}