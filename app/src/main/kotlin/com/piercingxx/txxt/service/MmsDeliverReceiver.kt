package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import com.piercingxx.txxt.core.MmsPduHeader
import com.piercingxx.txxt.core.MmsRetrievedContent
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
 * audio-drop policy → persist a `[Photo]` row → fetch the PDU → notify.
 *
 *  - **Parse failure = fail closed.** An unparseable PDU cannot prove what it
 *    carries — for all this receiver knows it is exactly the voice message
 *    docs/PRIVACY.md §5 promises is never received.
 *  - Filter BLOCK/QUARANTINE → nothing stored, broadcast aborted.
 *  - Header CONTENT-TYPE is audio → DROP_UNSTORED: voice messages are never
 *    received, never stored, never downloaded (§5).
 *  - Otherwise persist, auto-fetch the photo, and notify. A photo stays
 *    `[Photo]` until the operator taps it. A retrieve that turns out to be
 *    audio/empty deletes the row and does not notify.
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
     * Persists the inbound row as `(address, dateMillis, contentLocation)` and
     * returns the message id. Defaults to `null`, meaning the real
     * [InboundStore.persistInboundMmsMetadata] runs over a lazily built Room
     * database inside `onReceive`.
     */
    private val persist: (suspend (String, Long, String?) -> Long)? = null,
    /**
     * Fetches the PDU for `(context, messageId, contentLocation)` and returns
     * whether a message row still exists afterwards. Defaults to `null`,
     * meaning [MmsRetrieveService] is started (or [MmsRetrieve.retrieveAndStore]
     * if a background start is refused).
     */
    private val retrieve: (suspend (Context, Long, String?) -> Boolean)? = null,
    /**
     * Posts the arrival notification as `(context, address, body)`. Defaults
     * to `null`, meaning [ArrivalNotify]. Injectable for JVM tests.
     */
    private val notify: (suspend (Context, String, String) -> Unit)? = null,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        LiveInboundFilter.ensureLoaded(context)

        if (intent.action != mmsDeliverAction) return

        val info = extractPdu(intent)?.let { MmsPduHeader.parse(it) }
        if (info == null) return

        val sender = info.from?.let(::cleanAddress)
            ?: extractSenderFallback(intent)
            ?: return

        val (disposition, _) = inboundFilterProvider().evaluate(sender, "")
        if (disposition != MessageDisposition.DELIVER) {
            abortBroadcast()
            return
        }

        if (ReceivePolicy.decideAttachment(info.contentType) ==
            ReceivePolicy.AttachmentDecision.DROP_UNSTORED
        ) {
            abortBroadcast()
            return
        }

        val dateMillis = info.dateMillis ?: System.currentTimeMillis()
        val pendingResult = goAsync()
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val store: suspend (String, Long, String?) -> Long =
                    persist ?: { address, date, location ->
                        val database = TxxTDatabase.instance(context)
                        InboundStore.persistInboundMmsMetadata(
                            database.conversationDao(),
                            database.messageDao(),
                            address,
                            date,
                            contentLocation = location,
                        )
                    }
                val messageId = store(sender, dateMillis, info.contentLocation)
                val fetch: suspend (Context, Long, String?) -> Boolean =
                    retrieve ?: { ctx, id, location -> defaultRetrieve(ctx, id, location) }
                val kept = fetch(context, messageId, info.contentLocation)
                if (kept) {
                    val post: suspend (Context, String, String) -> Unit =
                        notify ?: { ctx, from, text ->
                            ArrivalNotify.post(ctx, from, text)
                        }
                    post(
                        context,
                        sender,
                        MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                    )
                }
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

        internal suspend fun defaultRetrieve(
            context: Context,
            messageId: Long,
            location: String?,
        ): Boolean {
            val app = context.applicationContext
            if (MmsRetrieveService.enqueue(app, messageId, location)) {
                // Service notifies after the fetch so goAsync can finish now.
                return false
            }
            val dao = TxxTDatabase.instance(app).messageDao()
            MmsRetrieve.retrieveAndStore(app, messageId, location)
            return dao.getById(messageId) != null
        }
    }
}
