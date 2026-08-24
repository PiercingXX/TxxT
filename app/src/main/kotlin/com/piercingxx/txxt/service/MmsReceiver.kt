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
     * Resolves the inbound message filter at receive time — evaluates sender
     * against block lists, content filters, and unknown-sender rules. Messages
     * that are [BLOCK] or [QUARANTINE] do not reach the attachment policy gate.
     * A lazy provider (resolved inside `onReceive`, after
     * `LiveInboundFilter.ensureLoaded`) rather than a constructor-time capture,
     * so the filter always reflects the freshest loaded rules instead of a
     * snapshot taken when this receiver was instantiated.
     */
    private val inboundFilterProvider: () -> InboundFilter = { LiveInboundFilter.current },
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
    /**
     * The T4 retry tracker for tap-initiated MMS downloads. Defaults to a real
     * [MmsDownloadRetry] whose download seam is a no-op (the actual carrier
     * fetch lands with the WS6 data layer); injectable so a JVM unit test can
     * drive the receiver's registration of stored MMS messages.
     */
    private val downloadRetry: MmsDownloadRetry = MmsDownloadRetry(
        performDownload = { false },
    ),
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Hydrate the persisted blocking rules once per process (M5): without
        // this the filter stays empty after process death/reboot until Settings
        // is reopened. Resolved before any evaluation.
        LiveInboundFilter.ensureLoaded(context)

        if (intent.action != mmsAction) return

        val sender = extractSender(intent) ?: return

        // Apply InboundFilter first — blocked/quarantined senders do not
        // reach the attachment policy gate.
        val (disposition, _) = inboundFilterProvider().evaluate(sender, "")
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
        // workstream; until it lands, no write is attempted here. The message is
        // still registered with the T4 retry tracker so a later tap-initiated
        // download that fails is retried with backoff and its failed state is
        // surfaced — the running receiver reaches MmsDownloadRetry here.
        intent.getStringExtra("messageId")?.toLongOrNull()?.let { messageId ->
            downloadRetry.state(messageId)
        }
    }
}