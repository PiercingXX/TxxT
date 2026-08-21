package com.piercingxx.txxt.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/** Intent extra: sender address for the quick-reply receiver. */
const val EXTRA_SENDER = "extra_sender"

/** RemoteInput key for the reply text in the notification quick-reply action. */
const val KEY_REPLY_TEXT = "key_reply_text"

/** Notification channel ID for incoming message notifications. */
const val CHANNEL_ID = "txxt_messages"

/**
 * Posts incoming-message notifications with the privacy posture from
 * [NotificationPolicy] and [NotificationPosture].
 *
 * Behaviour (docs/PRIVACY.md §3):
 *  - **Sender-name-only** — title is the sender name, text is the redacted
 *    content (sender name, never the message body);
 *  - **No bubbles** — never attaches bubble metadata or flags;
 *  - **Quick reply** — attaches an inline-reply action whose target reaches
 *    [SendPipeline.sendSms].
 *
 * Android-specific dependencies are injected as lambdas so the service is
 * drivable in a plain JVM unit test without Robolectric.
 */
class NotificationService(
    private val context: Context,

    /**
     * Creates the content intent that opens the app when the notification is tapped.
     * Defaults to a pending intent that launches [MainActivity].
     */
    private val contentIntent: (Context, String) -> PendingIntent = { ctx, sender ->
        val intent = Intent(ctx, Class.forName("com.piercingxx.txxt.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER, sender)
        }
        PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    },

    /**
     * Builds the quick-reply action. Defaults to creating an action with a
     * RemoteInput that targets [SendPipeline.sendSms] via a broadcast.
     */
    private val quickReplyAction: (Context, String) -> NotificationCompat.Action = { ctx, sender ->
        val replyIntent = Intent(ctx, NotificationReplyReceiver::class.java).apply {
            putExtra(EXTRA_SENDER, sender)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            ctx, 1, replyIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT).run {
            setLabel("Reply")
            build()
        }
        NotificationCompat.Action.Builder(
            android.R.drawable.sym_action_email, "Reply", replyPendingIntent
        ).addRemoteInput(remoteInput).build()
    },

    /**
     * Creates a [NotificationCompat.Builder] for the given channel.
     * Defaults to the standard constructor; injectable so a JVM unit test
     * can provide a builder without needing a real [Context].
     */
    private val makeBuilder: (String) -> NotificationCompat.Builder = { channelId ->
        NotificationCompat.Builder(context, channelId)
    },

    /**
     * Posts the built notification. Defaults to the system [NotificationManager].
     */
    private val postNotification: (Int, NotificationCompat.Builder) -> Unit = { id, builder ->
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lockscreenVisibility = 2 /* NotificationManager.VISIBILITY_SECRET */
            }
            manager.createNotificationChannel(channel)
        }
        manager.notify(id, builder.build())
    },
) {

    /**
     * Notification ID — one per sender, derived from the sender string hash.
     */
    fun notificationId(sender: String): Int = sender.hashCode() and 0x7fffffff

    /**
     * Post a notification for a message from [sender] with [body].
     *
     * Uses [NotificationPosture.decide] to determine whether to notify, redact,
     * or suppress. The [NotificationPolicy] provides the title and redacted content.
     *
     * Returns `true` if a notification was posted, `false` if suppressed.
     */
    fun postMessageNotification(
        sender: String,
        body: String,
        starred: Boolean = false,
        globalPosture: NotificationPosture.Posture = NotificationPosture.Posture.REDACTED,
        overrides: Map<String, NotificationPosture.Override> = emptyMap(),
    ): Boolean {
        val posture = NotificationPosture.decide(
            sender = sender,
            starred = starred,
            globalPosture = globalPosture,
            overrides = overrides,
        )

        return when (posture) {
            NotificationPosture.Posture.SUPPRESS -> false
            NotificationPosture.Posture.REDACTED,
            NotificationPosture.Posture.NOTIFY -> {
                val title = NotificationPolicy.notificationTitle(sender)
                val text = NotificationPolicy.redactedContent(sender)

                val builder = makeBuilder(CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_action_email)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(contentIntent(context, sender))
                    .setAutoCancel(true)
                    .addAction(quickReplyAction(context, sender))

                // No bubble metadata — NotificationPolicy.shouldBubble() is always false.
                // We never call setBubbleMetadata() or setBubbleIntent().

                val id = notificationId(sender)
                postNotification(id, builder)
                true
            }
        }
    }
}

/**
 * Broadcast receiver that handles the quick-reply action from the notification.
 * Extracts the reply text from the RemoteInput and sends it via [SendPipeline.sendSms].
 */
class NotificationReplyReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = bundle.getCharSequence(KEY_REPLY_TEXT)?.toString() ?: return
        val sender = intent.getStringExtra("extra_sender") ?: return
        SendPipeline.sendSms(context, sender, replyText)
    }
}
