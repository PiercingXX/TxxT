package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import com.piercingxx.txxt.core.MmsPduHeader
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for the platform's **primary** inbound-MMS delivery —
 * "android.provider.Telephony.WAP_PUSH_DELIVER" with MIME type
 * `application/vnd.wap.mms-message`. The WAP-push twin of [SmsDeliverReceiver]:
 * it fires when TxxT IS the default SMS app (`BROADCAST_WAP_PUSH` is
 * signature-held by the platform, so only the OS can invoke it), whereas
 * [MmsReceiver] handles the SMS_RECEIVED-era WAP_PUSH_RECEIVED path.
 *
 * Pipeline: parse the PDU header → resolve the sender → [InboundFilter] →
 * audio-drop policy → persist metadata only → arrival notification.
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
 *  - STORE → a metadata row is persisted through [InboundStore]
 *    (`persistInboundMmsMetadata`, body empty): MMS auto-download stays off
 *    (docs/PRIVACY.md §8.1) and remote content is fetched only on explicit tap
 *    later — never by this receiver. After the persist succeeds, the arrival
 *    notification is posted through [notify].
 *
 * **Notification honesty (body is empty on purpose):** the row this receiver
 * stores is metadata-only — the message content is never downloaded here — so
 * [notify] is handed an empty body. Under the default REDACTED posture that
 * costs nothing: the visible text is derived from the sender alone, never the
 * body. But if the posture is ever relaxed to NOTIFY, an MMS notification
 * would show empty text rather than content this receiver never had; that is
 * the honest limit of notifying before download.
 *
 * As with SMS ([SmsDeliverReceiver]), the notification fires only when
 * `POST_NOTIFICATIONS` is granted — automatic below API 33, user-gated from
 * API 33 on; denied → delivery is silent-by-permission, surfaced once via the
 * gate's Toast from this receiver's context.
 *
 * Unlike [MmsReceiver] there is **no `messageId` extra contract** on the
 * DELIVER path, so MmsDownloadRetry registration is deliberately deferred to
 * the tap-initiated download slice; nothing is registered here.
 *
 * The Room write runs off the main thread ([goAsync] + [Dispatchers.IO],
 * `pendingResult.finish()` guaranteed in `finally`, a
 * [CoroutineExceptionHandler] backstop) — BootReceiver precedent.
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
     * Persists a delivered message's metadata as `(address, dateMillis)`.
     * Defaults to `null`, meaning the real [InboundStore.persistInboundMmsMetadata]
     * runs over a lazily built Room database inside `onReceive` (ComposeActivity
     * precedent: the database is never touched when a test supplies its own seam).
     */
    private val persist: (suspend (String, Long) -> Unit)? = null,
    /**
     * Posts the arrival notification for a delivered message as
     * `(context, address, body)` — the body is `""` here, because the stored
     * row is metadata-only and no content was ever fetched (see class KDoc).
     * Defaults to `null`, meaning the real posting runs:
     * [PermissionGate.canNotify] gates it on `POST_NOTIFICATIONS` (denied →
     * silent delivery + one Toast), then
     * [NotificationService.postMessageNotification] posts under the default
     * REDACTED posture. Injectable for JVM tests.
     */
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

        // No DATE field in the header → fall back to the receive-time wall
        // clock rather than dropping an otherwise-delivered message.
        val dateMillis = info.dateMillis ?: System.currentTimeMillis()

        val pendingResult = goAsync()
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val store: suspend (String, Long) -> Unit =
                    persist ?: { address, date ->
                        val database = TxxTDatabase.instance(context)
                        InboundStore.persistInboundMmsMetadata(
                            database.conversationDao(),
                            database.messageDao(),
                            address,
                            date,
                        )
                    }
                store(sender, dateMillis)
                // Notify strictly AFTER the metadata persist succeeded: never
                // announce a message that failed to store. Body is "" — the
                // row is metadata-only (see class KDoc).
                val post: suspend (Context, String, String) -> Unit =
                    notify ?: { ctx, from, text ->
                        val gate = PermissionGate()
                        if (gate.canNotify(ctx)) {
                            NotificationService(ctx).postMessageNotification(sender = from, body = text)
                        }
                    }
                post(context, sender, "")
            } finally {
                pendingResult.finish()
            }
        }
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
