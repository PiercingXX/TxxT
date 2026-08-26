package com.piercingxx.txxt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.piercingxx.txxt.MainActivity
import com.piercingxx.txxt.contacts.ContactNameResolver
import com.piercingxx.txxt.data.OutboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Intent extra: sender address for the quick-reply receiver. */
const val EXTRA_SENDER = "extra_sender"

/** RemoteInput key for the reply text in the notification quick-reply action. */
const val KEY_REPLY_TEXT = "key_reply_text"

/**
 * Notification channel ID for incoming message notifications (sound).
 *
 * `_v2` because a channel's sound is immutable after creation: v1 shipped with
 * the system default sound, and the only way to give existing installs the
 * bundled `res/raw/txxt.wav` is to mint a successor id and delete the old one
 * (the xx-phone `ChannelIds` versioning pattern — `purpose_vN`, append-only,
 * never recreate a retired id, because delete-and-recreate resurrects the old
 * immutable settings instead of applying the new ones).
 */
const val CHANNEL_ID = "txxt_messages_v2"

/**
 * The retired v1 sound-channel id (system default sound). Deleted at channel
 * creation time so devices that created it before the `_v2` bump migrate to
 * the bundled-sound channel instead of keeping a dead channel in Settings.
 * Kept as a named constant (not inlined at the delete site) so the migration
 * pair — mint [CHANNEL_ID], delete this — reads as one auditable unit and the
 * unit tests can prove the ids actually differ.
 */
const val LEGACY_CHANNEL_ID_SOUND = "txxt_messages"

/** Channel ID for the vibrate-only alert style (no sound). */
const val CHANNEL_ID_VIBRATE = "txxt_messages_vibrate"

/** Channel ID for the silent alert style (no sound, no vibration). */
const val CHANNEL_ID_SILENT = "txxt_messages_silent"

/**
 * Posts incoming-message notifications with the privacy posture from
 * [NotificationPolicy] and [NotificationPosture].
 *
 * Behaviour (docs/PRIVACY.md §3):
 *  - **Privacy postures** — REDACTED (the default) shows title = sender name
 *    and text = the redacted content (sender name, never the message body);
 *    NOTIFY (opt-in) additionally reveals the message body as the visible text;
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
        // SINGLE_TOP so a tap while the launcher is already up delivers the
        // sender through onNewIntent instead of recreating the activity; the
        // launcher consumes EXTRA_SENDER and opens that sender's thread.
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SENDER, sender)
        }
        // Request code = the per-sender notification id: the intents differ
        // only in extras, so a shared request code would make filterEquals
        // match and the platform hand every sender the FIRST sender's intent.
        // FLAG_UPDATE_CURRENT keeps the extras fresh on re-posts.
        PendingIntent.getActivity(
            ctx,
            idFor(sender),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    },

    /**
     * Builds the quick-reply action. Defaults to creating an action with a
     * RemoteInput that targets [SendPipeline.sendSms] via a broadcast.
     */
    private val quickReplyAction: (Context, String) -> NotificationCompat.Action = { ctx, sender ->
        val replyIntent = Intent(ctx, NotificationReplyReceiver::class.java).apply {
            putExtra(EXTRA_SENDER, sender)
        }
        // The reply PendingIntent MUST be mutable on API 31+: the system writes
        // the RemoteInput results bundle into it, and with FLAG_IMMUTABLE that
        // write is refused — `RemoteInput.getResultsFromIntent` then returns
        // null in the receiver and every quick reply is silently discarded.
        // Per-sender request code + FLAG_UPDATE_CURRENT for the same
        // filterEquals reason as the content intent above.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            ctx,
            idFor(sender),
            replyIntent,
            mutability or PendingIntent.FLAG_UPDATE_CURRENT,
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
     * Resolves the sender address to the name saved for it in the system
     * contacts provider, falling back to the address
     * ([ContactNameResolver.labelFor]) — so a notification from a saved
     * contact is titled with their name rather than a raw number.
     *
     * A resolver is built per posted notification rather than held process-wide
     * because this service is itself constructed per inbound message (the
     * deliver receivers do `NotificationService(ctx)` inline), so there is no
     * longer-lived instance to hang a cache on. That is at most one contacts
     * query per arriving message, which is nothing next to the persist and the
     * `NotificationManager` round trip it sits between — unlike a list bind,
     * which is why the LIST path caches and this one does not need to.
     *
     * Injectable so a JVM unit test can pin the title without a provider.
     */
    private val displayName: (String) -> String = { sender ->
        ContactNameResolver(context).labelFor(sender)
    },

    /**
     * Posts the built notification. Defaults to the system [NotificationManager].
     */
    private val postNotification: (Int, NotificationCompat.Builder) -> Unit = { id, builder ->
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Migration: the v1 sound channel carried the system default sound,
            // and that setting is frozen into the channel forever — so the
            // bundled-sound channel is a NEW id ([CHANNEL_ID], `_v2`) and the
            // retired id is deleted here, before the create below, so a device
            // that had v1 ends up with exactly one sound channel in Settings.
            // deleteNotificationChannel is a no-op for an id that never
            // existed, so fresh installs pay nothing for this line.
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_SOUND)

            // The bundled notification sound, resolved by resource NAME
            // (`raw/txxt`, not the numeric R.raw id): the numeric id is
            // re-assigned across builds, and the platform persists this URI
            // string inside the immutable channel — a numeric URI would
            // silently point at the wrong resource (or nothing) after an app
            // update, while the name form survives every rebuild.
            val bundledSound = Uri.parse(
                "android.resource://${context.packageName}/raw/txxt"
            )
            // USAGE_NOTIFICATION / CONTENT_TYPE_SONIFICATION so the platform
            // routes the sound through the notification volume stream (and
            // respects DND) instead of defaulting to the media stream.
            val soundAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // One channel per alert style (a channel's sound/vibration cannot
            // be changed after creation, so the style choice picks the channel
            // at post time instead). All three stay lock-screen SECRET.
            listOf(
                NotificationChannel(
                    CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(bundledSound, soundAttributes)
                },
                NotificationChannel(
                    CHANNEL_ID_VIBRATE,
                    "Messages (vibrate)",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ID_SILENT,
                    "Messages (silent)",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            ).forEach { channel ->
                channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
                manager.createNotificationChannel(channel)
            }
        }
        manager.notify(id, builder.build())
    },
) {

    /**
     * Notification ID — one per sender, derived from the sender string hash.
     */
    fun notificationId(sender: String): Int = idFor(sender)

    companion object {
        /**
         * The per-sender notification id, shared with the PendingIntent request
         * codes above and the quick-reply receiver's post-reply cancel — every
         * consumer must derive the SAME id for a given sender.
         */
        fun idFor(sender: String): Int = sender.hashCode() and 0x7fffffff
    }

    /**
     * Post a notification for a message from [sender] with [body].
     *
     * Uses [NotificationPosture.decide] to determine whether to notify, redact,
     * or suppress. [NotificationPolicy.notificationTitle] provides the title;
     * the visible text is the message body under NOTIFY (opt-in) and the
     * redacted content under REDACTED (the default).
     *
     * Returns `true` if a notification was posted, `false` if suppressed.
     */
    fun postMessageNotification(
        sender: String,
        body: String,
        starred: Boolean = false,
        globalPosture: NotificationPosture.Posture = NotificationPosture.Posture.REDACTED,
        overrides: Map<String, NotificationPosture.Override> = emptyMap(),
        /**
         * The channel to post on — the alert-style choice (sound / vibrate /
         * silent) picks between [CHANNEL_ID], [CHANNEL_ID_VIBRATE], and
         * [CHANNEL_ID_SILENT] via [NotificationPrefs.channelIdFor].
         */
        channelId: String = CHANNEL_ID,
        muted: Boolean = false,
    ): Boolean {
        val posture = NotificationPosture.decide(
            sender = sender,
            starred = starred,
            globalPosture = globalPosture,
            overrides = overrides,
            muted = muted,
        )

        return when (posture) {
            NotificationPosture.Posture.SUPPRESS -> false
            NotificationPosture.Posture.REDACTED,
            NotificationPosture.Posture.NOTIFY -> {
                // The contact lookup is resolved ONCE and handed to both policy
                // calls: title and redacted text must name the sender the same
                // way, and a single resolution also means a single (cached)
                // provider consultation per posted notification.
                val title = NotificationPolicy.notificationTitle(sender, displayName)
                // NOTIFY (opt-in) reveals the message body; REDACTED (default)
                // shows only the redacted content — never the body.
                val text = if (posture == NotificationPosture.Posture.NOTIFY) {
                    body
                } else {
                    NotificationPolicy.redactedContent(sender, displayName)
                }

                val builder = makeBuilder(channelId)
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
 *
 * **Persist-then-send contract** (mirrors the compose path,
 * `ThreadActivity.sendComposed`): the reply is persisted FIRST through
 * [OutboundStore.persistOutgoingSms] as an outgoing row with `sent = false` —
 * so it is visible in thread history and, if the process dies before the
 * platform send, `RebootReconcile` re-drives it on next boot — then handed to
 * [SendPipeline.sendSms], and only a successful send marks the row sent
 * (`MessageDao.markSent`). A gate-denied send leaves the row pending, never
 * marked. A reply that never reaches Room would be invisible in its own
 * thread — this receiver exists to close exactly that gap.
 *
 * Persistence plus a synchronous SMS send would not fit the ~10 s receiver
 * ANR budget: [goAsync] extends the receiver's lifetime, the work runs on
 * [Dispatchers.IO] with `pendingResult.finish()` guaranteed in `finally`, and
 * a [CoroutineExceptionHandler] backstop keeps an unexpected throw from
 * crashing the process (the deliver receivers' precedent). The guard reads
 * (sender / reply text) run BEFORE [goAsync] — nothing async starts for a
 * malformed intent.
 *
 * Injectable seams (ComposeActivity / deliver-receiver precedent): the system
 * instantiates this receiver through the no-arg constructor, so every seam
 * defaults either to the real behaviour ([extractReply], [send]) or to `null`
 * meaning "resolve the real implementation inside [onReceive]" ([persist],
 * [markSent]) — which keeps the class JVM-testable without Robolectric and
 * without mocking the static `RemoteInput.getResultsFromIntent`.
 */
class NotificationReplyReceiver(
    /**
     * Extracts `(sender, replyText)` from the received [Intent]. Defaults to
     * the platform read — `RemoteInput.getResultsFromIntent` for the reply
     * text plus the [EXTRA_SENDER] string extra; `null` when the bundle or
     * either field is missing. Injectable so a JVM unit test can drive
     * [onReceive] without mocking the platform static call.
     */
    private val extractReply: (Intent) -> Pair<String, String>? = { intent ->
        val bundle = RemoteInput.getResultsFromIntent(intent)
        val replyText = bundle?.getCharSequence(KEY_REPLY_TEXT)?.toString()
        val sender = intent.getStringExtra(EXTRA_SENDER)
        if (bundle == null || replyText == null || sender == null) null else sender to replyText
    },
    /**
     * Sends the reply SMS as `(context, recipient, body) -> send attempted?`.
     * Defaults to [SendPipeline.sendSms]; injectable so a JVM unit test can
     * observe the send decision (and force a gate denial) without touching
     * the telephony stack.
     */
    private val send: (Context, String, String) -> Boolean =
        { ctx, recipient, text -> SendPipeline.sendSms(ctx, recipient, text) },
    /**
     * Persists the outgoing reply as `(address, body) -> messageId`. Defaults
     * to `null`, meaning the real [OutboundStore.persistOutgoingSms] runs over
     * a lazily built Room database inside [onReceive] (the database is never
     * touched when a test supplies its own seam).
     */
    private val persist: (suspend (String, String) -> Long)? = null,
    /**
     * Marks a persisted message transmitted as `(messageId) -> Unit`.
     * Defaults to `null`, meaning the real `MessageDao.markSent` runs over the
     * same lazily built Room database as [persist].
     */
    private val markSent: (suspend (Long) -> Unit)? = null,
    /**
     * Dismisses the sender's notification once the reply is handled. Defaults
     * to `null`, meaning the real `NotificationManager.cancel` runs inside
     * [onReceive]: after an inline reply Android shows a progress spinner on
     * the notification until the app updates or cancels it — without this the
     * notification hangs in a "sending" state forever.
     */
    private val clearNotification: ((Context, String) -> Unit)? = null,
) : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Guards BEFORE goAsync: a malformed quick-reply intent never starts
        // async work (deliver receivers' precedent).
        val extracted = extractReply(intent) ?: return
        val sender = extracted.first
        val replyText = extracted.second
        if (sender.isBlank() || replyText.isBlank()) return

        val pendingResult = goAsync()
        // Backstop: an unexpected throw must never crash the process — the
        // receiver finishes quietly instead (BootReceiver precedent).
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val database by lazy { TxxTDatabase.instance(context) }
                val persistStep = persist ?: { address, text ->
                    OutboundStore.persistOutgoingSms(
                        database.conversationDao(),
                        database.messageDao(),
                        address,
                        text,
                    )
                }
                val markSentStep = markSent ?: { id -> database.messageDao().markSent(id) }

                // Persist FIRST (sent=false), send SECOND, mark sent LAST —
                // never mark a message whose send was denied (see class KDoc).
                val messageId = persistStep(sender, replyText)
                val ok = send(context, sender, replyText)
                if (ok) {
                    markSentStep(messageId)
                }
                // Dismiss the notification whether or not the send was gated:
                // the RemoteInput spinner must never hang forever (see the
                // [clearNotification] KDoc). The safe-cast keeps a JVM test's
                // relaxed mock Context from throwing here.
                val clearStep = clearNotification ?: { ctx, from ->
                    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                        ?.cancel(NotificationService.idFor(from))
                }
                clearStep(context, sender)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
