package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition

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
class MmsReceiver(
    /**
     * Inbound message filter — evaluates sender against block lists,
     * content filters, and unknown-sender rules. Messages that are [BLOCK] or
     * [QUARANTINE] do not reach the attachment policy gate.
     */
    private val inboundFilter: InboundFilter = LiveInboundFilter.current,
    /**
     * Extracts the sender address of the inbound MMS from [Intent]. Defaults to
     * reading the intent's extras; injectable so a JVM unit test can drive
     * `onReceive` with a real sender without mocking the platform PDU parsing.
     */
    private val extractSender: (Intent) -> String? = { intent ->
        intent.getStringExtra("address")
            ?: intent.getStringExtra("com.android.mms.address")
    },
    /**
     * Extracts the content type (MIME) of the inbound MMS attachment from [Intent].
     * Defaults to the intent's type; injectable so a JVM unit test can drive
     * `onReceive` with a real content type.
     */
    private val extractContentType: (Intent) -> String? = { intent ->
        intent.type
    },
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * platform constant; injectable so a JVM unit test can drive [onReceive]
     * without the Android stub returning null for the constant.
     */
    private val mmsAction: String = "android.provider.Telephony.WAP_PUSH_RECEIVED",
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != mmsAction) return

        val sender = extractSender(intent) ?: return

        // Apply InboundFilter first — blocked/quarantined senders do not
        // reach the attachment policy gate.
        val (disposition, _) = inboundFilter.evaluate(sender, "")
        if (disposition != MessageDisposition.DELIVER) {
            abortBroadcast()
            return
        }

        // Audio-MMS drop policy: audio attachments are dropped at the inbox
        // boundary, never downloaded and never stored (docs/PRIVACY.md:91-92).
        val contentType = extractContentType(intent)
        if (ReceivePolicy.decideAttachment(contentType) == ReceivePolicy.AttachmentDecision.DROP_UNSTORED) {
            abortBroadcast()
            return
        }
        // STORE (non-audio) persists through the data layer (WS6), a separate
        // workstream; until it lands, no write is attempted here.
    }
}