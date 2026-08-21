package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.piercingxx.txxt.block.InboundFilter
import com.piercingxx.txxt.block.MessageDisposition

/**
 * `BroadcastReceiver` for inbound SMS (`android.provider.Telephony.SMS_RECEIVED`).
 *
 * Completes the default-SMS-handler role the manifest declares
 * (`app/src/main/AndroidManifest.xml:4-7`). On an inbound SMS it applies the
 * [InboundFilter] first: blocked messages are dropped entirely, quarantined
 * messages are held aside. Only delivered messages reach the T1 receive policy's
 * auto-reply gate (`docs/PRIVACY.md:96-97`): the optional auto-reply SMS telling
 * the sender voice messages aren't accepted is **off by default**, so unless the
 * off-by-default setting is enabled (and any per-contact override allows it) no
 * reply is sent. The reply itself is sent through [SendPipeline], which never
 * requests a delivery or read report.
 */
class SmsReceiver(
    /**
     * Inbound message filter — evaluates sender and body against block lists,
     * content filters, and unknown-sender rules. Messages that are [BLOCK] or
     * [QUARANTINE] do not reach the auto-reply gate.
     */
    private val inboundFilter: InboundFilter = InboundFilter(),
    /**
     * Whether the auto-reply SMS is enabled. Off by default (`docs/PRIVACY.md:96`);
     * the flag is drivable so the reply can be turned on (and per-contact
     * overrides supplied) once the data layer (WS6) stores the setting. The
     * default keeps the honest off-by-default posture until then.
     */
    private val autoReplyEnabled: Boolean = false,
    private val autoReplyOverrides: Map<String, ReceivePolicy.AutoReplyOverride> = emptyMap(),
    /**
     * Extracts the sender address of the inbound SMS from [Intent]. Defaults to
     * reading the platform `Telephony.Sms.Intents.getMessagesFromIntent` and
     * taking the first message's originating address; injectable so a JVM unit
     * test can drive `onReceive` with a real sender without mocking the
     * platform static call or the finicky mockable-jar `SmsMessage`.
     */
    private val extractSender: (Intent) -> String? = { intent ->
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.firstOrNull()
            ?.originatingAddress
    },
    /**
     * Extracts the body text of the inbound SMS from [Intent]. Defaults to
     * reading the first message's body; injectable so a JVM unit test can
     * drive `onReceive` with a real body without mocking [android.telephony.SmsMessage].
     */
    private val extractBody: (Intent) -> String = { intent ->
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.firstOrNull()
            ?.displayMessageBody
            ?: ""
    },
    /**
     * Sends the auto-reply SMS. Defaults to [SendPipeline.sendSms]; injectable
     * so a JVM unit test can observe the send decision without mocking the
     * object.
     */
    private val sendReply: (Context, String, String) -> Unit = SendPipeline::sendSms,
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * platform constant; injectable so a JVM unit test can drive [onReceive]
     * without the Android stub returning null for the constant.
     */
    private val smsAction: String = Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != smsAction) return

        val sender = extractSender(intent) ?: return
        val body = extractBody(intent)

        val (disposition, _) = inboundFilter.evaluate(sender, body)
        if (disposition != MessageDisposition.DELIVER) return

        if (ReceivePolicy.shouldAutoReply(
                enabled = autoReplyEnabled,
                sender = sender,
                overrides = autoReplyOverrides,
            )
        ) {
            sendReply(context, sender, ReceivePolicy.AUTO_REPLY_BODY)
        }
    }
}