package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.service.SendPipeline
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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
    private lateinit var dictationButton: Button

    /**
     * The on-device text-to-speech engine (WS13 read-aloud). Created in
     * [onCreate] and shut down in [onDestroy]. The spoken text always comes
     * from [MessageReadAloud.speakable] — the seam is the only source of what
     * is read aloud (docs/FEATURES.md §Accessibility).
     */
    private var tts: TextToSpeech? = null

    private val database: TxxTDatabase by lazy { TxxTDatabase.build(this) }

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

    private val scope: CoroutineScope = MainScope()

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
        dictationButton = findViewById(R.id.dictation_button)

        adapter = ThreadAdapter(onMessageTap = ::readMessageAloud)
        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = adapter

        // WS13 read-aloud: the on-device TTS engine whose spoken text always
        // comes from MessageReadAloud.speakable (see readMessageAloud).
        tts = TextToSpeech(this) { _ -> }

        sendButton.setOnClickListener { sendComposed() }
        settingsButton.setOnClickListener { openSettings() }
        dictationButton.setOnClickListener { startDictation() }
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
     * compose input's text/hint/field, and the send button's text/tint.
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
            val accentOn = tokens.accentOn.toInt()

            root.setBackgroundColor(bg)
            composeBar.setBackgroundColor(surface)
            composeInput.setTextColor(text)
            composeInput.setHintTextColor(muted)
            composeInput.setBackgroundColor(surface)
            sendButton.setTextColor(accentOn)
            sendButton.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }.apply()
    }

    /** Opens the settings screen (WS12 T5) from the thread's settings affordance. */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /**
     * Starts on-device speech recognition (dictation, WS13).
     *
     * The last hop — `SpeechRecognizer` capturing audio and returning recognized
     * text — needs a mic and cannot run on the box; what this does is route the
     * recognition result through [DictationInsert.insert] into the compose
     * field. No audio is ever sent or received — dictation only writes text into
     * the local compose field (docs/PRIVACY.md §5).
     */
    private fun startDictation() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                val recognized = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?: return
                applyDictation(recognized)
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    /**
     * Applies recognized speech text to the compose field via [DictationInsert].
     *
     * This is the deterministic dictation step the box can verify: the field's
     * current text plus the recognized text become the field's new text through
     * [DictationInsert.insert]. The on-device recognition hop is upstream.
     */
    private fun applyDictation(recognized: String) {
        val current = composeInput.text?.toString().orEmpty()
        composeInput.setText(DictationInsert.insert(current, recognized))
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
        tts?.shutdown()
        tts = null
    }

    /** Loads this conversation's messages from Room and submits them to the adapter. */
    private fun observeMessages() {
        scope.launch(Dispatchers.Main) {
            ThreadMessageLoader(database.messageDao(), conversationId)
                .messages()
                .collect { messages ->
                    adapter.submit(messages)
                }
        }
    }

    /** Sends the composed text via [SendPipeline.sendSms] to the thread's participant. */
    private fun sendComposed() {
        val body = composeInput.text?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val destination = destinationAddress() ?: return@launch
            SendPipeline.sendSms(this@ThreadActivity, destination, body)
            composeInput.setText("")
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