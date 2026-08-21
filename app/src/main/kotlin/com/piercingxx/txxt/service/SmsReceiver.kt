package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * `BroadcastReceiver` for inbound SMS (`android.provider.Telephony.SMS_RECEIVED`).
 *
 * Completes the default-SMS-handler role the manifest declares
 * (`app/src/main/AndroidManifest.xml:4-7`). On an inbound SMS it applies the T1
 * receive policy's auto-reply gate (`docs/PRIVACY.md:96-97`): the optional
 * auto-reply SMS telling the sender voice messages aren't accepted is **off by
 * default**, so unless the off-by-default setting is enabled (and any per-contact
 * override allows it) no reply is sent. The reply itself is sent through
 * [SendPipeline], which never requests a delivery or read report.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        // Auto-reply gate, off by default (`docs/PRIVACY.md:96`). The enabled
        // flag and per-contact overrides live in the data layer (WS6); until
        // that lands the default posture — off — is the only honest value.
        val autoReplyEnabled = false
        if (ReceivePolicy.shouldAutoReply(enabled = autoReplyEnabled, sender = sender)) {
            SendPipeline.sendSms(context, sender, ReceivePolicy.AUTO_REPLY_BODY)
        }
    }
}