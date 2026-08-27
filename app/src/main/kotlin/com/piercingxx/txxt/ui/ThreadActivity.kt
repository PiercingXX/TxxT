package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.View
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.contacts.ContactNameResolver
import com.piercingxx.txxt.data.OutboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.core.ViewedThread
import com.piercingxx.txxt.service.MmsContentFetcher
import com.piercingxx.txxt.service.MmsDownloadRetry
import com.piercingxx.txxt.service.MmsDownloadState
import com.piercingxx.txxt.service.MmsRetrieve
import com.piercingxx.txxt.service.NotificationService
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Intent extra: the conversation id the thread screen opens. */
const val EXTRA_CONVERSATION_ID = "extra_conversation_id"

/** Intent extra: optional compose-field prefill from `sms:?body=`. */
const val EXTRA_PREFILL_BODY = "extra_prefill_body"

/**
 * The conversation thread screen (T3).
 *
 * Opens a single conversation's messages as text-first lines (never chat
 * bubbles, docs/PRIVACY.md §2) on AMOLED black. Messages are loaded from Room
 * (via [TxxTDatabase] + the message DAO, mapped to the pure `core` model) and
 * rendered by [ThreadAdapter] through [ThreadMessagePresenter]. The compose bar
 * routes a send through [SendPipeline.sendSms] — the same pipeline the
 * notification quick reply uses (docs/PRIVACY.md §3, §5) — or through
 * [SendPipeline.sendMms] when a photo is attached, which is where the
 * metadata scrub happens.
 *
 * **Photos.** The compose bar's `⊕` opens the Android photo picker
 * ([ActivityResultContracts.PickVisualMedia] with `ImageOnly`). That picker is
 * the whole reason this feature costs **no new permission**: the operator
 * chooses exactly one photo and the app is granted access to that one item and
 * nothing else — no `READ_MEDIA_IMAGES`, no `ACTION_GET_CONTENT`, no widening
 * of the manifest's justified permission list (docs/PRIVACY.md §8). The picked
 * bytes are staged into app-private cache immediately ([PhotoStaging]) because
 * the picker's grant is one-shot and not persistable; what a composed send
 * expands into is decided by [PhotoAttachment.plan].
 *
 * The picker is driven through `startActivityForResult` with the contract's own
 * `createIntent` / `parseResult` rather than `registerForActivityResult`,
 * because this screen is a plain `android.app.Activity` (as every screen in
 * this app is) and the registry API needs a `ComponentActivity`. The contract
 * — and therefore the picker selection, the ImageOnly MIME filter and the
 * pre-Android-13 `ACTION_OPEN_DOCUMENT` fallback — is exactly the same object
 * either way.
 */
class ThreadActivity : Activity() {

    private lateinit var adapter: ThreadAdapter
    private lateinit var messageList: RecyclerView
    private lateinit var composeInput: EditText
    private lateinit var sendButton: Button
    private lateinit var settingsButton: Button
    private lateinit var threadTitle: TextView
    private lateinit var attachButton: Button
    private lateinit var attachmentRow: View
    private lateinit var attachmentLabel: TextView
    private lateinit var attachmentClear: Button
    private lateinit var threadSearch: SearchView
    private var allMessages: List<com.piercingxx.txxt.core.Message> = emptyList()
    private var threadQuery: String = ""

    /**
     * The staged copy of the picked photo, or null when nothing is attached.
     *
     * This is a file in this app's own cache directory, never the picker's
     * `content://` URI — see [PhotoStaging] for why the picker's one-shot,
     * non-persistable grant cannot be held until send time. Its path is written
     * to the saved instance state so a rotation or a saved-state restore
     * re-attaches the same photo.
     */
    private var stagedPhoto: File? = null

    /** The indicator line for [stagedPhoto], from [PhotoAttachment.indicator]. */
    private var stagedLabel: String = ""

    /**
     * The photo-picker contract, held so [launchPhotoPicker] and
     * [onActivityResult] provably use the SAME contract instance to build the
     * intent and to parse its result — the pair is the contract's API surface,
     * and splitting them across two instances is how a picker silently starts
     * returning nothing.
     */
    private val photoPicker = ActivityResultContracts.PickVisualMedia()

    /**
     * Resolves the thread's participant numbers to their saved contact names
     * (the system contacts provider — the dialer's list). Held on the instance
     * so the header and any later refresh share one cache; `by lazy` because a
     * `Context` is only valid after `onCreate`.
     */
    private val contactNames: ContactNameResolver by lazy { ContactNameResolver(this) }

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

    /** Participant addresses for this thread; used to dismiss their shade tile. */
    private var participantAddresses: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, 0L)

        messageList = findViewById(R.id.message_list)
        composeInput = findViewById(R.id.compose_input)
        sendButton = findViewById(R.id.send_button)
        settingsButton = findViewById(R.id.settings_button)
        threadTitle = findViewById(R.id.thread_title)
        attachButton = findViewById(R.id.attach_button)
        attachmentRow = findViewById(R.id.attachment_row)
        attachmentLabel = findViewById(R.id.attachment_label)
        attachmentClear = findViewById(R.id.attachment_clear)
        threadSearch = findViewById(R.id.thread_search)

        adapter = ThreadAdapter(
            onMessageTap = ::onMessageTap,
            onMessageLongPress = ::promptMessageActions,
        )
        // stackFromEnd keeps the newest row against the compose bar. Without
        // it, adjustResize shrinks the list from the bottom and incoming
        // lines sit under the keyboard.
        messageList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messageList.adapter = adapter

        // WS13 read-aloud: the on-device TTS engine whose spoken text always
        // comes from MessageReadAloud.speakable (see readMessageAloud).
        tts = TextToSpeech(this) { _ -> }

        sendButton.setOnClickListener { sendComposed() }
        settingsButton.setOnClickListener { openSettings() }
        attachButton.setOnClickListener { launchPhotoPicker() }
        attachmentClear.setOnClickListener { clearAttachment() }
        threadTitle.setOnLongClickListener {
            copyThreadNumber()
            true
        }
        EmojiTypeface.apply(composeInput)
        EmojiNerdCompose.bind(composeInput)
        threadSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applyThreadSearch(query.orEmpty())
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                applyThreadSearch(newText.orEmpty())
                return true
            }
        })
        val prefill = intent.getStringExtra(EXTRA_PREFILL_BODY)
        if (!prefill.isNullOrBlank() && composeInput.text.isNullOrBlank()) {
            composeInput.setText(prefill)
            composeInput.setSelection(prefill.length)
        }

        // Re-attach the photo a rotation or a saved-state restore interrupted,
        // then collect whatever an unrestored process death orphaned in the
        // staging directory (PhotoStaging's KDoc has the full reasoning).
        restoreStagedPhoto(savedInstanceState)
        showAttachment()
        sweepStagedPhotos()

        observeMessages()
        showThreadTitle()

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
     * compose input's text/hint/field, the send/settings/attach/remove glyphs,
     * and the attachment indicator line.
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
            // The attach and remove glyphs are affordances, so they take the
            // accent exactly as send and settings do — borderless, no tint.
            attachButton.setTextColor(accent)
            attachmentClear.setTextColor(accent)
            // The indicator is type, not an affordance: muted, so a staged
            // photo announces itself without competing with the thread.
            attachmentLabel.setTextColor(muted)
            // The header is type, not chrome: it takes the theme's text token,
            // not the accent — the accent stays reserved for the affordances.
            threadTitle.setTextColor(text)
        }.apply()
    }

    /**
     * Fills the header with who this thread is with.
     *
     * The participant addresses go through the SAME title function the
     * conversation list uses ([ConversationListPresenter.title]) and the same
     * contact resolution, so the row the operator tapped and the screen it
     * opened cannot disagree about the contact's name. The DB read is off the
     * main thread's critical path (the activity scope); resolution itself is
     * cached, and every fallback — no permission, no contact, no participants
     * — still yields text, so the header is never blank.
     */
    private fun showThreadTitle() {
        scope.launch {
            val addresses = database.conversationDao().getById(conversationId)
                ?.participantAddresses
                ?.split(ADDRESS_DELIMITER)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            participantAddresses = addresses
            threadTitle.text =
                ConversationListPresenter.title(addresses) { contactNames.labelFor(it) }
            if (started) dismissShadeNotification()
        }
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
            .setItems(arrayOf("Copy message", "Copy number", "Read aloud", "Delete")) { _, which ->
                when (which) {
                    0 -> copyMessage(message)
                    1 -> copyThreadNumber()
                    2 -> readMessageAloud(message)
                    3 -> confirmDeleteMessage(message)
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

    private val mmsFetcher = MmsContentFetcher()
    private val mmsRetry = MmsDownloadRetry(
        performDownload = { messageId ->
            val row = database.messageDao().getById(messageId) ?: return@MmsDownloadRetry false
            val location = row.contentLocation ?: return@MmsDownloadRetry false
            val pdu = mmsFetcher.fetch(this@ThreadActivity, location) ?: return@MmsDownloadRetry false
            MmsRetrieve.applyPdu(
                database.messageDao(),
                messageId,
                pdu,
                saveImage = { bytes, mime ->
                    val dir = File(filesDir, "mms")
                    if (!dir.exists()) dir.mkdirs()
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val file = File(dir, "$messageId.$ext")
                    file.writeBytes(bytes)
                    file.absolutePath
                },
            )
        },
        onStateChange = { _, state ->
            if (state == MmsDownloadState.FAILED) {
                runOnUiThread {
                    Toast.makeText(this, "MMS download failed", Toast.LENGTH_LONG).show()
                }
            }
        },
    )

    /**
     * Tap: retrieve a pending inbound MMS, otherwise read the row aloud.
     */
    private fun onMessageTap(message: com.piercingxx.txxt.core.Message) {
        if (MmsRetrieve.needsRetrieve(message)) {
            Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) {
                mmsRetry.download(message.id)
            }
            return
        }
        readMessageAloud(message)
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
        // Opening the thread is the operator reading it: drop the shade tile
        // (auto-cancel only fires if they tapped the notification itself).
        dismissShadeNotification()
        // Returning to a thread that accumulated unread messages while parked
        // reads them now.
        if (hasUnread) markThreadRead()
    }

    override fun onStop() {
        super.onStop()
        started = false
        ViewedThread.close(conversationId)
    }

    /** Loads this conversation's messages from Room and submits them to the adapter. */
    private fun observeMessages() {
        scope.launch(Dispatchers.Main) {
            ThreadMessageLoader(database.messageDao(), conversationId)
                .messages()
                .collect { messages ->
                    allMessages = messages
                    val follow = followingLatest()
                    adapter.submit(ThreadSearchFilter.filter(messages, threadQuery))
                    if (follow) pinToLatest()
                    // A visible thread reads its incoming messages: clear their
                    // unread flag so the launcher's badge and any UNREAD_FIRST
                    // ordering settle. Gated on [started] (a backgrounded
                    // thread never reads for the user) and on something
                    // actually being unread — the update's own `isRead = 0`
                    // clause then makes the settled state a no-op instead of
                    // an invalidation loop.
                    hasUnread = messages.any { it.isUnread }
                    if (started) {
                        dismissShadeNotification()
                        if (hasUnread) markThreadRead()
                    }
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
     * Drops this sender's shade notification and marks the thread as the one
     * currently on screen so a follow-up SMS does not put the tile back.
     */
    private fun dismissShadeNotification() {
        val senders = (allMessages.mapNotNull { it.senderAddress } + participantAddresses)
            .filter { it.isNotBlank() }
        ViewedThread.open(conversationId, senders)
        NotificationService(this).dismiss(senders)
    }

    /**
     * Sends what is composed — text, a photo, or both — to the thread's
     * participant (H6b).
     *
     * What a tap on `➜` actually dispatches is decided by the pure
     * [PhotoAttachment.plan] seam, not by branching here, so the routing rule
     * is JVM-testable: text only still goes through
     * [SendPipeline.sendSms] exactly as it did before attachments existed; a
     * photo goes through [SendPipeline.sendMms] (where the metadata scrub
     * happens); a photo with a caption dispatches both, the caption first,
     * because the MMS entry point carries media only and a silently dropped
     * caption would be a lie about what was sent.
     *
     * Each step persists **before** sending (`sent = false`) and is marked sent
     * only after the pipeline reports the platform send was attempted. Each
     * step also clears only its OWN input on success: a failed photo leaves the
     * attachment attached even when the caption went out, and a failed caption
     * leaves the draft text in the field. Nothing composed is ever destroyed by
     * a send that did not happen, and every failure is surfaced.
     */
    private fun sendComposed() {
        val body = composeInput.text?.toString()?.trim().orEmpty()
        val photo = stagedPhoto
        val steps = PhotoAttachment.plan(body, photo != null)
        if (steps.isEmpty()) return
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
            val failures = mutableListOf<String>()
            steps.forEach { step ->
                when (step) {
                    SendStep.SMS_TEXT ->
                        if (sendTextStep(destination, body)) {
                            composeInput.setText("")
                        } else {
                            failures += "message"
                        }
                    SendStep.MMS_PHOTO ->
                        if (photo != null && sendPhotoStep(destination, photo)) {
                            clearAttachment()
                        } else {
                            failures += "photo"
                        }
                }
            }
            if (failures.isNotEmpty()) {
                Toast.makeText(
                    this@ThreadActivity,
                    "Not sent: ${failures.joinToString(" and ")}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Persists and sends the composed text over SMS. Returns whether the
     * platform send was attempted.
     *
     * Persist through the shared [OutboundStore] path (collision-safe id
     * allocation under the same lock inbound delivery uses) — NOT a bare
     * wall-clock id, which could REPLACE-destroy a row the delivery path wrote
     * in the same millisecond.
     */
    private suspend fun sendTextStep(destination: String, body: String): Boolean {
        val messageId = OutboundStore.persistOutgoingSms(
            conversations = database.conversationDao(),
            messages = database.messageDao(),
            address = destination,
            body = body,
        )
        val ok = SendPipeline.sendSms(this@ThreadActivity, destination, body)
        if (ok) database.messageDao().markSent(messageId)
        return ok
    }

    private suspend fun sendPhotoStep(destination: String, photo: File): Boolean {
        val messageId = OutboundStore.persistOutgoingMms(
            conversations = database.conversationDao(),
            messages = database.messageDao(),
            address = destination,
        )
        val ok = try {
            SendPipeline.sendMms(
                this@ThreadActivity,
                Uri.fromFile(photo),
                destination = destination,
            )
        } catch (_: Exception) {
            false
        }
        if (ok) {
            val row = database.messageDao().getById(messageId)
            if (row != null) {
                database.messageDao().upsert(row.copy(mediaPath = photo.absolutePath, sent = true))
            } else {
                database.messageDao().markSent(messageId)
            }
        }
        return ok
    }

    private fun applyThreadSearch(query: String) {
        threadQuery = query
        adapter.submit(ThreadSearchFilter.filter(allMessages, query))
        if (query.isBlank()) pinToLatest()
    }

    /**
     * True when the viewport is already on (or near) the newest row, or the
     * list has not laid out yet. Used so an incoming message scrolls into
     * view above the keyboard without yanking someone who scrolled up to
     * read history.
     */
    private fun followingLatest(): Boolean {
        if (threadQuery.isNotBlank()) return false
        val last = adapter.itemCount - 1
        if (last < 0) return true
        val lm = messageList.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = lm.findLastVisibleItemPosition()
        return lastVisible == RecyclerView.NO_POSITION || lastVisible >= last - 1
    }

    private fun pinToLatest() {
        val last = adapter.itemCount - 1
        if (last < 0) return
        messageList.post { messageList.scrollToPosition(last) }
    }

    private fun copyThreadNumber() {
        scope.launch {
            val number = destinationAddress() ?: return@launch
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return@launch
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("number", number))
            Toast.makeText(this@ThreadActivity, "Copied $number", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Photo attachment ----

    /**
     * Opens the Android photo picker, restricted to images.
     *
     * `PickVisualMedia` + [PickVisualMediaRequest] with `ImageOnly` is chosen
     * deliberately over `ACTION_GET_CONTENT` and over declaring
     * `READ_MEDIA_IMAGES`: it needs **no storage permission at all**, the
     * operator picks exactly one photo, and the app receives access to that one
     * item and nothing else. Either alternative would widen the manifest's
     * permission list — the thing this app's whole posture is about — for no
     * benefit (docs/PRIVACY.md §8).
     *
     * On a device with no picker and no document provider at all the intent
     * resolves to nothing; that is reported rather than crashing.
     */
    private fun launchPhotoPicker() {
        val request = PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build()
        try {
            startActivityForResult(photoPicker.createIntent(this, request), REQUEST_PICK_PHOTO)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No photo picker on this device", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("startActivityForResult: this screen is a plain Activity, not a ComponentActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_PHOTO) return
        // The contract owns result parsing (RESULT_OK plus data/clipData), so
        // the picker's result shape is never re-guessed here. A null means the
        // operator cancelled: leave whatever was already attached alone.
        val picked = photoPicker.parseResult(resultCode, data) ?: return
        stagePickedPhoto(picked)
    }

    /**
     * Copies the picked photo into app-private cache and attaches it.
     *
     * The copy happens NOW, on the picker's live one-shot grant, because that
     * grant is not persistable and will not survive until send time — see
     * [PhotoStaging] for the full reasoning. The read and the write both run on
     * [Dispatchers.IO]: a photo is megabytes, and doing this on the main thread
     * would jank the compose bar at exactly the moment the operator is looking
     * at it.
     *
     * A previously staged photo is replaced (and its copy deleted) only once
     * the new one is safely on disk, so a failed pick never silently discards
     * the attachment the operator already had.
     */
    private fun stagePickedPhoto(uri: Uri) {
        scope.launch(Dispatchers.Main) {
            val staged = withContext(Dispatchers.IO) {
                val bytes = readPickedBytes(uri) ?: return@withContext null
                val file = PhotoStaging.stage(
                    directory = PhotoStaging.directory(cacheDir),
                    bytes = bytes,
                    nowMillis = System.currentTimeMillis(),
                ) ?: return@withContext null
                file to PhotoAttachment.indicator(displayNameOf(uri), bytes.size.toLong())
            }
            if (staged == null) {
                Toast.makeText(
                    this@ThreadActivity,
                    "Photo could not be attached",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            PhotoStaging.discard(stagedPhoto)
            stagedPhoto = staged.first
            stagedLabel = staged.second
            showAttachment()
        }
    }

    /**
     * Reads the picked photo's bytes through the content resolver. Null — never
     * a throw — when the URI is unreadable (a revoked grant, a provider that
     * died, an I/O error), so the caller can report it.
     */
    private fun readPickedBytes(uri: Uri): ByteArray? = try {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /**
     * The picker's display name for the photo, used only to make the indicator
     * line specific enough to identify which photo is attached. Null whenever
     * the provider does not supply one — the indicator then simply omits that
     * segment.
     */
    private fun displayNameOf(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount > 0) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    /**
     * Detaches the staged photo and deletes its copy.
     *
     * This is the "remove without sending" path the `✕` on the indicator line
     * drives, and it is also what a successful photo send calls. Deleting the
     * copy is the point: a photo the operator changed their mind about must not
     * be left sitting in the app's cache.
     */
    private fun clearAttachment() {
        PhotoStaging.discard(stagedPhoto)
        stagedPhoto = null
        stagedLabel = ""
        showAttachment()
    }

    /** Shows or hides the one-line attachment indicator to match [stagedPhoto]. */
    private fun showAttachment() {
        val attached = stagedPhoto != null
        attachmentRow.visibility = if (attached) View.VISIBLE else View.GONE
        attachmentLabel.text = stagedLabel
    }

    /**
     * Re-attaches the staged photo recorded in [savedInstanceState].
     *
     * Only a real file still inside the staging directory is accepted
     * ([PhotoStaging.isStagedIn]), so a stale bundle — or one naming a copy the
     * OS reclaimed from the cache — leaves the compose bar with no attachment
     * rather than with an indicator for something the app can no longer read.
     * Losing the attachment is a supported end state; claiming one that is gone
     * is not.
     */
    private fun restoreStagedPhoto(savedInstanceState: Bundle?) {
        val path = savedInstanceState?.getString(STATE_STAGED_PATH) ?: return
        val file = File(path)
        if (!PhotoStaging.isStagedIn(PhotoStaging.directory(cacheDir), file)) return
        stagedPhoto = file
        stagedLabel = savedInstanceState.getString(STATE_STAGED_LABEL).orEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The staged FILE survives process death; the pointer to it does not.
        // Carrying the path (and its indicator line) through saved state is
        // what makes a rotation or a saved-state restore keep the attachment.
        stagedPhoto?.let { file ->
            outState.putString(STATE_STAGED_PATH, file.absolutePath)
            outState.putString(STATE_STAGED_LABEL, stagedLabel)
        }
    }

    /**
     * Deletes staged copies orphaned by a process death that never restored.
     *
     * Without this, every abandoned pick leaves a full copy of one of the
     * operator's photos in the app's cache directory indefinitely. The
     * currently-attached copy is explicitly held back regardless of age.
     */
    private fun sweepStagedPhotos() {
        val attached = stagedPhoto
        scope.launch(Dispatchers.IO) {
            PhotoStaging.sweep(
                directory = PhotoStaging.directory(cacheDir),
                nowMillis = System.currentTimeMillis(),
                keep = attached,
            )
        }
    }

    /** The remote participant address to send to, or null when the thread has none. */
    private suspend fun destinationAddress(): String? {
        val conversation = database.conversationDao().getById(conversationId)
        return conversation?.participantAddresses
            ?.split(ADDRESS_DELIMITER)
            ?.firstOrNull { it.isNotBlank() }
    }

    companion object {
        /**
         * Delimiter joining participant addresses in the conversations table
         * (the launcher's constant of the same name). Named here rather than
         * inlined so the send path and the header split on provably the same
         * character.
         */
        private const val ADDRESS_DELIMITER = "\u0001"

        /** `startActivityForResult` request code for the photo picker. */
        private const val REQUEST_PICK_PHOTO = 0x9701

        /** Saved-state key: the absolute path of the staged photo copy. */
        private const val STATE_STAGED_PATH = "state_staged_photo_path"

        /** Saved-state key: the indicator line for the staged photo. */
        private const val STATE_STAGED_LABEL = "state_staged_photo_label"

        /** Builds a launch intent for [ThreadActivity] for the given conversation. */
        fun launchIntent(
            context: android.content.Context,
            conversationId: Long,
            prefillBody: String? = null,
        ): Intent =
            Intent(context, ThreadActivity::class.java)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                .apply {
                    if (!prefillBody.isNullOrBlank()) {
                        putExtra(EXTRA_PREFILL_BODY, prefillBody)
                    }
                }
    }
}