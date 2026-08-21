package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * BroadcastReceiver for inbound MMS (android.provider.Telephony.WAP_PUSH_RECEIVED).
 *
 * Completes the default-SMS-handler role the manifest declares
 * (app/src/main/AndroidManifest.xml). On an inbound MMS it applies the T1
 * receive policy's audio-MMS drop (docs/PRIVACY.md:91-92): an attachment whose
 * content type is an audio MIME type is dropped at the inbox boundary, never
 * downloaded and never stored. Non-audio attachments pass through for storage.
 *
 * MMS auto-download stays off (docs/PRIVACY.md:151-153); remote content is
 * fetched only on explicit tap, never by this receiver.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_RECEIVED") return

        // The WAP-push intent's MIME type is the message envelope
        // (application/vnd.wap.mms-message); the per-part attachment type lives
        // inside the PDU. Feed what the intent carries through the policy so the
        // decision path is the single source of truth.
        val decision = ReceivePolicy.decideAttachment(intent.type)
        if (decision == ReceivePolicy.AttachmentDecision.DROP_UNSTORED) {
            // Drop at the inbox boundary: do not download, do not store.
            abortBroadcast()
        }
        // STORE (non-audio) persists through the data layer (WS6), a separate
        // workstream; until it lands, no write is attempted here.
    }
}