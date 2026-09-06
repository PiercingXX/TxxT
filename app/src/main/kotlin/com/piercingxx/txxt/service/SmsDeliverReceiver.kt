package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.QuarantineStore
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for the platform's **primary** inbound-SMS delivery,
 * `Telephony.Sms.Intents.SMS_DELIVER_ACTION` ("android.provider.Telephony.SMS_DELIVER").
 *
 * When TxxT is the default SMS app the system delivers each inbound SMS here
 * instead of `SmsReceiver`'s SMS_RECEIVED path: the manifest registers this
 * component for SMS_DELIVER behind a receiver-permission filter whose
 * `BROADCAST_SMS` permission is signature-held by the platform, so only the OS
 * can invoke it.
 *
 * **The default-app contract, stated honestly:** in that role THIS receiver is
 * the only sink an inbound message ever reaches — whatever it declines to
 * persist is lost, full stop. That is why the [InboundFilter] decision
 * deliberately runs *before* any persistence:
 *
 *  - [MessageDisposition.BLOCK] — dropped, nothing stored, no auto-reply.
 *    Dropping blocked mail here IS the block working as intended.
 *  - [MessageDisposition.QUARANTINE] — persisted unread through
 *    [QuarantineStore] (hidden from the main list) and **not** notified.
 *    Starred senders never reach this branch ([InboundFilter] delivers them).
 *  - [MessageDisposition.DELIVER] — persisted unread through [InboundStore]
 *    (`isRead = false`, so the unread-count derivation lights up), then the
 *    arrival notification is posted through [notify], then the optional
 *    auto-reply gate ([ReceivePolicy.shouldAutoReply], off by default) may
 *    answer through [SendPipeline]. The delivery flow is therefore
 *    **filter → persist → notify → auto-reply**, in that order: a notification
 *    is never posted about a message that failed to store, and the auto-reply
 *    decision stays last.
 *
 * **Notification permission, stated honestly:** the arrival notification fires
 * only when `POST_NOTIFICATIONS` is granted — which is automatic below API 33
 * (the runtime permission does not exist pre-Tiramisu, [PermissionGate.canNotify]
 * returns `true`) and user-gated from API 33 on. When it is denied there, this
 * receiver posts nothing: delivery is silent-by-permission, surfaced once via
 * the gate's Toast from this receiver's context.
 *
 * Persistence plus a possible synchronous SMS send would not fit the ~10 s
 * receiver ANR budget (BootReceiver precedent): [goAsync] extends the
 * receiver's lifetime and the work runs on [Dispatchers.IO], with
 * `pendingResult.finish()` guaranteed in `finally` and a
 * [CoroutineExceptionHandler] backstop so an unexpected throw can never crash
 * the delivery process.
 */
class SmsDeliverReceiver(
    /**
     * Resolves the inbound message filter at receive time — evaluates sender
     * and body against block lists, content filters, and unknown-sender rules.
     * A lazy provider (resolved inside `onReceive`, after
     * `LiveInboundFilter.ensureLoaded`) rather than a constructor-time capture,
     * so the filter always reflects the freshest loaded rules (SmsReceiver
     * precedent).
     */
    private val inboundFilterProvider: () -> InboundFilter = { LiveInboundFilter.current },
    /**
     * Whether the auto-reply SMS is enabled. Off by default (`docs/PRIVACY.md` §1);
     * drivable exactly like `SmsReceiver`'s flag until the data layer stores
     * the setting.
     */
    private val autoReplyEnabled: Boolean = false,
    private val autoReplyOverrides: Map<String, ReceivePolicy.AutoReplyOverride> = emptyMap(),
    /**
     * Extracts the sender address of the inbound SMS from [Intent]. Defaults to
     * reading the platform `Telephony.Sms.Intents.getMessagesFromIntent` and
     * taking the first message's originating address; injectable so a JVM unit
     * test can drive `onReceive` without mocking the platform static call
     * (SmsReceiver precedent).
     */
    private val extractSender: (Intent) -> String? = { intent ->
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.firstOrNull()
            ?.originatingAddress
    },
    /**
     * Extracts the body text of the inbound SMS from [Intent]. A message over
     * one SMS segment arrives as several PDUs in a single DELIVER intent —
     * one [android.telephony.SmsMessage] per PDU — so the segments are joined
     * in order; reading only the first would truncate every long message.
     * Injectable for JVM tests.
     */
    private val extractBody: (Intent) -> String = { intent ->
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull()
            ?.joinToString(separator = "") { it.displayMessageBody ?: "" }
            ?: ""
    },
    /**
     * Extracts the message timestamp from [Intent]. Defaults to the receive-time
     * wall clock: the DELIVER payload carries one timestamp per PDU segment and
     * which segment's `timestampMillis` should win varies across multi-part
     * messages, so no single platform read is trustworthy here — an injectable
     * seam keeps that policy honest and testable instead of hiding it.
     */
    private val extractDate: (Intent) -> Long = { System.currentTimeMillis() },
    /**
     * Sends the auto-reply SMS. Defaults to [SendPipeline.sendSms]; injectable
     * so a JVM unit test can observe the send decision without mocking the
     * object (SmsReceiver precedent).
     */
    private val sendReply: (Context, String, String) -> Unit = { context, destination, body ->
        SendPipeline.sendSms(context, destination, body)
    },
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * platform constant; injectable so a JVM unit test can drive [onReceive]
     * without the Android stub returning null for the constant.
     */
    private val deliverAction: String = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
    /**
     * Persists a delivered message as `(address, body, dateMillis)`. Defaults to
     * `null`, meaning the real [InboundStore.persistInboundSms] runs over a
     * lazily built Room database inside `onReceive` (ComposeActivity precedent:
     * the database is never touched when a test supplies its own seam).
     */
    private val persist: (suspend (String, String, Long) -> Unit)? = null,
    /**
     * Persists a quarantined message as `(address, body, dateMillis)`.
     * Defaults to `null`, meaning [QuarantineStore.persistInboundSms].
     * A hold is never announced and never auto-replied.
     */
    private val persistQuarantine: (suspend (String, String, Long) -> Unit)? = null,
    /**
     * Posts the arrival notification for a delivered message as
     * `(context, address, body)`. Defaults to `null`, meaning the real posting
     * runs through [ArrivalNotify] (permission gate, starred bypass,
     * Business-tier silent hours from XX-Dialer).
     * Injectable so a JVM unit test can observe the notification decision
     * without touching Android's NotificationManager.
     */
    private val notify: (suspend (Context, String, String) -> Unit)? = null,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Hydrate the persisted blocking rules once per process (M5) before any
        // evaluation — process death must not blank the filter (SmsReceiver).
        LiveInboundFilter.ensureLoaded(context)

        if (intent.action != deliverAction) return

        val sender = extractSender(intent) ?: return
        val body = extractBody(intent)
        val date = extractDate(intent)

        // The filter decision deliberately precedes persistence: in the
        // default-app role this receiver is the only sink. BLOCK ends here
        // with nothing written. QUARANTINE persists to the hold, not the
        // inbox, and never notifies.
        val (disposition, _) = inboundFilterProvider().evaluate(sender, body)
        if (disposition == MessageDisposition.BLOCK) return

        val pendingResult = goAsync()
        // Backstop: an unexpected throw must never crash the delivery process —
        // the receiver finishes quietly instead (BootReceiver precedent).
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                if (disposition == MessageDisposition.QUARANTINE) {
                    val hold: suspend (String, String, Long) -> Unit =
                        persistQuarantine ?: { address, text, dateMillis ->
                            val database = TxxTDatabase.instance(context)
                            QuarantineStore.persistInboundSms(
                                database.conversationDao(),
                                database.messageDao(),
                                address,
                                text,
                                dateMillis,
                            )
                        }
                    hold(sender, body, date)
                    return@launch
                }
                val store: suspend (String, String, Long) -> Unit =
                    persist ?: { address, text, dateMillis ->
                        val database = TxxTDatabase.instance(context)
                        InboundStore.persistInboundSms(
                            database.conversationDao(),
                            database.messageDao(),
                            address,
                            text,
                            dateMillis,
                        )
                    }
                store(sender, body, date)
                // Notify strictly AFTER the persist succeeded: never announce
                // a message that failed to store. The auto-reply gate stays
                // after the notification (see class KDoc flow order).
                val post: suspend (Context, String, String) -> Unit =
                    notify ?: { ctx, from, text ->
                        ArrivalNotify.post(ctx, from, text)
                    }
                post(context, sender, body)
                if (ReceivePolicy.shouldAutoReply(
                        enabled = autoReplyEnabled,
                        sender = sender,
                        overrides = autoReplyOverrides,
                    )
                ) {
                    sendReply(context, sender, ReceivePolicy.AUTO_REPLY_BODY)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
