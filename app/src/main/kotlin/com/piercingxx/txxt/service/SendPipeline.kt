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
 * content is fetched only on explicit tap, `docs/PRIVACY.md:151-153`).
 */
object SendPipeline {

    /**
     * Sends a text SMS to [destination] with [body].
     *
     * The delivery-report `PendingIntent` is `null` (`SendPolicy.requestsDeliveryReport()`
     * is always `false`), so the carrier is never asked to report delivery.
     */
    fun sendSms(context: Context, destination: String, body: String) {
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
     */
    fun sendMms(context: Context, contentUri: Uri) {
        val smsManager = SmsManager.getDefault()
        val configOverrides: Bundle? = null
        // sentIntent null = never request a read report (`docs/PRIVACY.md:23`).
        smsManager.sendMultimediaMessage(context, contentUri, null, configOverrides, null)
    }
}