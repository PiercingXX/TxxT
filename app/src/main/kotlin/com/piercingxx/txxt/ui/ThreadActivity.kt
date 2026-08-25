package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.data.OutboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.service.SendPipeline
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Intent extra: the conversation id the thread screen opens. */
const val EXTRA_CONVERSATION_ID = "extra_conversation_id"

/**
 * The conversation thread screen (T3).
 *
 * Opens a single conversation's messages as text-first lines (never chat
 * bubbles, docs/PRIVACY.md §2) on AMOLED black. Messages are loaded from Room
 * (via [TxxTDatabase] + the message DAO, mapped to the pure `core` model) and
 * rendered by [ThreadAdapter] through [ThreadMessagePresenter]. The compose bar
 * routes a send through [SendPipeline.sendSms] — the same pipeline the
 * notification quick reply uses (docs/PRIVACY.md §3, §5).
 *
 * FLAG_SECURE is set in code (docs/PRIVACY.md §3) so the thread never appears
 * in recents previews or screenshots.
 */
class ThreadActivity : Activity() {

    private lateinit var adapter: ThreadAdapter
    private lateinit var messageList: RecyclerView
    private lateinit var composeInput: EditText
    private lateinit var sendButton: Button
    private lateinit var settingsButton: Button

    /**
     * The on-device text-to-speech engine (WS13 read-aloud). Created in
     * [onCreate] and shut down in [onDestroy]. The spoken text always comes
     * from [MessageReadAloud.speakable] — the seam is the only source of what
     * is read aloud (docs/FEATURES.md §Accessibility).
     */
    private var tts: TextToSpeech? = null

    private val database: TxxTDatabase by lazy { TxxTDatabase.instance(this) }

    /**
     * The theme controller over the same `txxt_theme` SharedPreferences the
     * launcher (MainActivity) wires, so this screen reads the same persisted
     * manual theme / auto-sync toggle. T6's applier reads [ThemeController.effectiveTheme]
     * from it and paints the thread screen's chrome.
     */
    private val themeController: ThemeController by lazy {
        ThemeController(
            ThemeStore(
                SharedPreferencesThemeKeyValueStore(
                    getSharedPreferences("txxt_theme", MODE_PRIVATE)
                )
            )
        )
    }

    /**
     * The activity-scoped coroutine scope. A [SupervisorJob] keeps one failed
     * child from cancelling the others; [Dispatchers.Main.immediate] keeps UI
     * work on the main thread without an extra post when already there. The
     * scope is cancelled in [onDestroy] so the Room Flow collection started by
     * [observeMessages] cannot outlive the activity and leak it.
     */
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var conversationId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, 0L)

        messageList = findViewById(R.id.message_list)
        composeInput = findViewById(R.id.compose_input)
        sendButton = findViewById(R.id.send_button)
        settingsButton = findViewById(R.id.settings_button)

        adapter = ThreadAdapter(
            onMessageTap = ::readMessageAloud,
            onMessageLongPress = ::promptMessageActions,
        )
        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = adapter

        // WS13 read-aloud: the on-device TTS engine whose spoken text always
        // comes from MessageReadAloud.speakable (see readMessageAloud).
        tts = TextToSpeech(this) { _ -> }

        sendButton.setOnClickListener { sendComposed() }
        settingsButton.setOnClickListener { openSettings() }
        observeMessages()

        // T6 wire-in: the running thread screen reaches the theme applier, which
        // reads the effective theme from the controller and paints this screen's
        // chrome from the chosen tokens. Without this call the applier is dead
        // code and a theme change never reaches the UI.
        applyTheme()
    }

    /**
     * Paints this screen's chrome from the current effective theme (T6). Builds
     * a [ThemeApplier] over [themeController] whose seam applies the derived
     * tokens to the thread screen's views: the ground, the compose bar, the
     * compose input's text/hint/field, and the send/settings glyphs.
     *
     * Buttons stay borderless: the accent token (the reserved bright-white
     * signal in every preset) colors the glyph itself and NO background tint is
     * applied — a filled accent pill would out-shout the text-first lines, so
     * the accent lives in the type, not in chrome.
     */
    private fun applyTheme() {
        val root = findViewById<android.view.View>(R.id.thread_root)
        val composeBar = findViewById<android.view.View>(R.id.compose_bar)
        ThemeApplier(themeController) { tokens ->
            val bg = tokens.background.toInt()
            val surface = tokens.surface.toInt()
            val text = tokens.text.toInt()
            val muted = tokens.muted.toInt()
            val accent = tokens.accent.toInt()

            root.setBackgroundColor(bg)
            composeBar.setBackgroundColor(surface)
            composeInput.setTextColor(text)
            composeInput.setHintTextColor(muted)
            composeInput.setBackgroundColor(surface)
            sendButton.setTextColor(accent)
            settingsButton.setTextColor(accent)
        }.apply()
    }

    /** Opens the settings screen (WS12 T5) from the thread's settings affordance. */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /**
     * Long-press actions for a message row: copy the body to the clipboard,
     * read it aloud (the tap action, offered here too for discoverability),
     * or delete the single message. Delete is confirmed — it is the only
     * destructive one.
     */
    private fun promptMessageActions(message: com.piercingxx.txxt.core.Message) {
        android.app.AlertDialog.Builder(this)
            .setItems(arrayOf("Copy", "Read aloud", "Delete")) { _, which ->
                when (which) {
                    0 -> copyMessage(message)
                    1 -> readMessageAloud(message)
                    2 -> confirmDeleteMessage(message)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Copies the message body to the clipboard. */
    private fun copyMessage(message: com.piercingxx.txxt.core.Message) {
        val clipboard =
            getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("message", message.body)
        )
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    /** Confirms, then deletes one message; the live Flow refreshes the list. */
    private fun confirmDeleteMessage(message: com.piercingxx.txxt.core.Message) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch { database.messageDao().deleteById(message.id) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Reads a message aloud via the on-device [TextToSpeech] engine (WS13).
     *
     * The spoken text comes exclusively from [MessageReadAloud.speakable] — the
     * seam is the only source of what is read, and when it returns an empty
     * string (a blank body) the `speak` call is skipped so nothing is read
     * aloud. This is read-only TTS of existing text: no voice is ever sent or
     * received (docs/PRIVACY.md §5).
     */
    private fun readMessageAloud(message: com.piercingxx.txxt.core.Message) {
        val text = MessageReadAloud.speakable(message)
        if (text.isEmpty()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-message-${message.id}")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the scope first so the Room Flow collection stops feeding a
        // dead activity — without this, every open leaks the activity, its
        // adapter and the DB observer forever.
        scope.cancel()
        tts?.shutdown()
        tts = null
    }

    /**
     * Whether this screen is currently in front of the user ([onStart] to
     * [onStop]). A VISIBLE thread reads its messages; one parked in the back
     * stack must NOT — an inbound message arriving while the user is elsewhere
     * would otherwise be marked read unseen and the launcher badge wiped.
     */
    private var started = false

    /** Whether the latest collected message list still holds unread messages. */
    private var hasUnread = false

    override fun onStart() {
        super.onStart()
        started = true
        // Returning to a thread that accumulated unread messages while parked
        // reads them now.
        if (hasUnread) markThreadRead()
    }

    override fun onStop() {
        super.onStop()
        started = false
    }

    /** Loads this conversation's messages from Room and submits them to the adapter. */
    private fun observeMessages() {
        scope.launch(Dispatchers.Main) {
            ThreadMessageLoader(database.messageDao(), conversationId)
                .messages()
                .collect { messages ->
                    adapter.submit(messages)
                    // A visible thread reads its incoming messages: clear their
                    // unread flag so the launcher's badge and any UNREAD_FIRST
                    // ordering settle. Gated on [started] (a backgrounded
                    // thread never reads for the user) and on something
                    // actually being unread — the update's own `isRead = 0`
                    // clause then makes the settled state a no-op instead of
                    // an invalidation loop.
                    hasUnread = messages.any { it.isUnread }
                    if (started && hasUnread) markThreadRead()
                }
        }
    }

    /** Clears this conversation's unread flags (the visible-thread read). */
    private fun markThreadRead() {
        scope.launch(Dispatchers.Main) {
            database.messageDao().markConversationRead(conversationId)
        }
    }

    /**
     * Sends the composed text to the thread's participant (H6b).
     *
     * The outgoing message is persisted **before** sending (`sent = false`, so
     * an interrupted send is re-driven by the reboot reconcile) and marked sent
     * only after [SendPipeline.sendSms] reports the platform send was attempted
     * — which is also when the compose field clears. A denied gate or missing
     * recipient never destroys the draft: the field keeps its text and a toast
     * says why nothing was sent.
     */
    private fun sendComposed() {
        val body = composeInput.text?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val destination = destinationAddress()
            if (destination == null) {
                Toast.makeText(
                    this@ThreadActivity,
                    "No recipient for this thread",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            // Persist through the shared OutboundStore path (collision-safe id
            // allocation under the same lock inbound delivery uses) — NOT a
            // bare wall-clock id, which could REPLACE-destroy a row the
            // delivery path wrote in the same millisecond.
            val messageId = OutboundStore.persistOutgoingSms(
                conversations = database.conversationDao(),
                messages = database.messageDao(),
                address = destination,
                body = body,
            )
            val ok = SendPipeline.sendSms(this@ThreadActivity, destination, body)
            if (ok) {
                database.messageDao().markSent(messageId)
                composeInput.setText("")
            } else {
                Toast.makeText(
                    this@ThreadActivity,
                    "Message not sent",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** The remote participant address to send to, or null when the thread has none. */
    private suspend fun destinationAddress(): String? {
        val conversation = database.conversationDao().getById(conversationId)
        return conversation?.participantAddresses
            ?.split("\u0001")
            ?.firstOrNull { it.isNotBlank() }
    }

    companion object {
        /** Builds a launch intent for [ThreadActivity] for the given conversation. */
        fun launchIntent(context: android.content.Context, conversationId: Long): Intent =
            Intent(context, ThreadActivity::class.java)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
    }
}