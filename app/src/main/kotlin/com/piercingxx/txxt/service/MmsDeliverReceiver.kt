package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import com.piercingxx.txxt.core.MmsPduHeader

/**
 * BroadcastReceiver for the platform's **primary** inbound-MMS delivery —
 * "android.provider.Telephony.WAP_PUSH_DELIVER" with MIME type
 * `application/vnd.wap.mms-message`. The WAP-push twin of [SmsDeliverReceiver]:
 * it fires when TxxT IS the default SMS app (`BROADCAST_WAP_PUSH` is
 * signature-held by the platform, so only the OS can invoke it), whereas
 * [MmsReceiver] handles the SMS_RECEIVED-era WAP_PUSH_RECEIVED path.
 *
 * Pipeline: parse the PDU header → resolve the sender → [InboundFilter] →
 * audio-drop policy → drop. Holding `ROLE_SMS` still consumes
 * `WAP_PUSH_DELIVER` so the OS does not hand the PDU to another app; inbound
 * MMS is never stored or notified (a metadata-only persist used to render as
 * a fake `[MMS]` line).
 *
 *  - **Parse failure = fail closed.** An unparseable PDU cannot prove what it
 *    carries — for all this receiver knows it is exactly the voice message
 *    docs/PRIVACY.md §5 promises is never received. Rather than guess-storing a
 *    row whose nature is unknown, the broadcast ends with nothing written;
 *    failing closed beats storing a guess under §5's receive guarantee.
 *  - Filter BLOCK/QUARANTINE → nothing stored, broadcast aborted (the same
 *    only-sink posture as [SmsDeliverReceiver]: no quarantine store exists,
 *    docs/PRIVACY.md §8.7 is proposed, not adopted).
 *  - Header CONTENT-TYPE is audio → [ReceivePolicy.decideAttachment] says
 *    DROP_UNSTORED: voice messages are never received, never stored, never
 *    downloaded (§5).
 *  - Everything else is also dropped unstored: no metadata row, no arrival
 *    notification. MMS auto-download stays off (docs/PRIVACY.md §8.1).
 */
class MmsDeliverReceiver(
    /**
     * Resolves the inbound message filter at receive time (SmsReceiver /
     * MmsReceiver precedent: lazy provider resolved after
     * `LiveInboundFilter.ensureLoaded`, never a constructor-time capture).
     */
    private val inboundFilterProvider: () -> InboundFilter = { LiveInboundFilter.current },
    /**
     * Extracts the raw MM PDU bytes from [Intent]. Defaults to reading the
     * platform-documented `"data"` extra (the WAP-push entity body, which for
     * MMS is the MM PDU beginning with the message-type octet), falling back to
     * the `"header"` extra. There is no public `getPdu` helper on
     * `Telephony.Mms.Intents` (it exposes action constants only), so this is
     * the honest platform contract. Injectable so a JVM unit test can feed
     * crafted PDUs without mocking intent extras.
     */
    private val extractPdu: (Intent) -> ByteArray? = { intent ->
        intent.getByteArrayExtra("data") ?: intent.getByteArrayExtra("header")
    },
    /**
     * Fallback sender resolution when the parsed header carries no FROM:
     * the same extras keys [MmsReceiver] reads ("address", then the legacy
     * Messaging "com.android.mms.address"); injectable for JVM tests.
     */
    private val extractSenderFallback: (Intent) -> String? = { intent ->
        intent.getStringExtra("address")
            ?: intent.getStringExtra("com.android.mms.address")
    },
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * platform literal (no public constant); injectable so a JVM unit test can
     * drive [onReceive] without the Android stub returning null for it.
     */
    private val mmsDeliverAction: String = "android.provider.Telephony.WAP_PUSH_DELIVER",
    /**
     * Optional persist seam retained so existing JVM tests can inject a
     * recorder. Production never writes: inbound MMS is dropped unstored.
     */
    @Suppress("unused")
    private val persist: (suspend (String, Long, String?) -> Unit)? = null,
    /**
     * Optional notify seam retained so existing JVM tests can inject a
     * recorder. Production never notifies: inbound MMS is not a message.
     */
    @Suppress("unused")
    private val notify: (suspend (Context, String, String) -> Unit)? = null,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Hydrate the persisted blocking rules once per process (M5) before any
        // evaluation — process death must not blank the filter.
        LiveInboundFilter.ensureLoaded(context)

        if (intent.action != mmsDeliverAction) return

        // Fail closed: an unparseable PDU cannot prove it is not the voice
        // message §5 promises is never received — store nothing rather than
        // guess-store (see class KDoc).
        val info = extractPdu(intent)?.let { MmsPduHeader.parse(it) }
        if (info == null) return

        // Strip the "/TYPE=PLMN"-style addressing suffix before comparing or
        // storing: the block list and the conversation key hold bare numbers.
        val sender = info.from?.let(::cleanAddress)
            ?: extractSenderFallback(intent)
            ?: return

        val (disposition, _) = inboundFilterProvider().evaluate(sender, "")
        if (disposition != MessageDisposition.DELIVER) {
            abortBroadcast()
            return
        }

        // Audio-MMS drop policy: voice messages are never received or stored
        // (docs/PRIVACY.md §5); the header CONTENT-TYPE is all we will ever see
        // because content is never auto-downloaded.
        if (ReceivePolicy.decideAttachment(info.contentType) ==
            ReceivePolicy.AttachmentDecision.DROP_UNSTORED
        ) {
            abortBroadcast()
            return
        }

        // Consume the broadcast (ROLE_SMS) and store nothing. A metadata-only
        // row would surface as `[MMS]` — inbound MMS is not a message here.
        return
    }

    companion object {

        /**
         * Strips a "/TYPE=..." addressing suffix (e.g. "/TYPE=PLMN") from a
         * parsed FROM address, leaving the bare number the rest of the app
         * stores and compares. Pure — JVM-testable.
         */
        fun cleanAddress(address: String): String =
            address.substringBefore("/TYPE=").trim()
    }
}
