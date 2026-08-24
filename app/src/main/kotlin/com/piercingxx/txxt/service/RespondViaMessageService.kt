package com.piercingxx.txxt.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder

/**
 * The call-screen quick-reply service (`ACTION_RESPOND_VIA_MESSAGE`).
 *
 * The platform's in-call UI starts this service when the user picks a canned
 * quick response during a call: the data URI carries the other party and the
 * `EXTRA_TEXT` extra carries the response text. The service resolves both,
 * sends the SMS through [SendPipeline] (the same pipeline every outgoing
 * message uses — gated, no delivery/read reports), reports the outcome, and
 * stops. Started work is never re-delivered after a crash, so the launch is
 * [START_NOT_STICKY]: a quick reply dropped mid-flight must not resurrect and
 * send twice later.
 *
 * Declared in the manifest under `android.permission.SEND_RESPOND_VIA_MESSAGE`
 * — a signature-held permission, so only the system can start it.
 *
 * The three decisions behind the reply are injectable seams (the established
 * `SmsReceiver` / `PermissionGate` pattern) so the behaviour is drivable in a
 * plain JVM unit test without Robolectric (not in the offline cache):
 *  - [resolveRecipient] — data URI to recipient address;
 *  - [resolveText] — intent to response text;
 *  - [send] — the actual send, defaulting to [SendPipeline.sendSms];
 *  - [reportResult] — where the outcome code lands (see its KDoc for why it
 *    is not the BroadcastReceiver-style `setResult`).
 */
class RespondViaMessageService(
    /**
     * Resolves the recipient out of the RESPOND_VIA_MESSAGE data URI.
     * Defaults to trimming the decoded scheme-specific part and rejecting a
     * blank payload; injectable so tests can drive the resolution directly.
     */
    private val resolveRecipient: (Uri?) -> String? = { uri ->
        uri?.schemeSpecificPart?.trim()?.takeIf { it.isNotEmpty() }
    },
    /**
     * Resolves the quick-response text out of the intent. Defaults to reading
     * `Intent.EXTRA_TEXT` (empty when absent); injectable for the same reason.
     */
    private val resolveText: (Intent) -> String = { intent ->
        intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    },
    /**
     * Sends the SMS. Defaults to [SendPipeline.sendSms]; injectable so a JVM
     * unit test can observe the send decision without touching SmsManager or
     * the SEND_SMS gate.
     */
    private val send: (Context, String, String) -> Boolean = { context, recipient, text ->
        SendPipeline.sendSms(context, recipient, text)
    },
    /**
     * Receives the outcome code ([RESULT_OK] or [RESULT_IO_ERROR]). A started
     * service has no result channel the platform reads back over
     * ACTION_RESPOND_VIA_MESSAGE (`Service` — unlike `BroadcastReceiver` — has
     * no `setResult`; the framework fires this via `startService()` with no
     * result contract), so the production sink deliberately keeps nothing:
     * success/failure of the send itself surfaces through the normal SMS
     * result path. Injectable so a test can observe which outcome the wiring
     * computed.
     */
    private val reportResult: (Int) -> Unit = {},
) : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = resolveRecipient(intent?.data)
        val text = intent?.let(resolveText).orEmpty()

        // The service instance is the send context; respond() itself stays
        // pure over recipient/text/outcome so the JVM tests drive it directly.
        reportResult(respond(recipient, text) { to, body -> send(this, to, body) })
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {

        /** The platform action string this service answers. */
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"

        /**
         * Outcome: the quick reply was handed to the send pipeline. Anchored
         * to [android.app.Activity.RESULT_OK] (-1), the conventional success
         * result value.
         */
        const val RESULT_OK = Activity.RESULT_OK

        /**
         * Outcome: the reply could not be carried out (no usable recipient,
         * blank text, or a refused send). Anchored to 1,
         * [android.app.Activity.RESULT_FIRST_USER] — the first non-generic
         * result value; there is no dedicated platform constant on this path.
         */
        const val RESULT_IO_ERROR = 1

        /**
         * The respond decision, purely over its inputs: the reply goes out
         * only when a non-blank recipient **and** non-blank text are present
         * **and** [send] reports the send was attempted — [RESULT_OK];
         * anything short of that is [RESULT_IO_ERROR]. [send] takes only the
         * recipient and text — the caller binds its own Context (a pure
         * companion function has none to give). Pure — JVM-testable.
         */
        fun respond(
            recipient: String?,
            text: String,
            send: (String, String) -> Boolean,
        ): Int =
            if (!recipient.isNullOrBlank() && text.isNotBlank() && send(recipient, text)) {
                RESULT_OK
            } else {
                RESULT_IO_ERROR
            }
    }
}
