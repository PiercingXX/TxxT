package com.piercingxx.txxt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.service.DefaultHandlerMonitor
import com.piercingxx.txxt.service.EXTRA_SENDER
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import com.piercingxx.txxt.ui.BlockingRules
import com.piercingxx.txxt.ui.ConversationListAdapter
import com.piercingxx.txxt.ui.ConversationListLoader
import com.piercingxx.txxt.ui.ConversationSearchFilter
import com.piercingxx.txxt.ui.ConversationSwipeHelper
import com.piercingxx.txxt.ui.SwipeActionCallback
import com.piercingxx.txxt.ui.ThreadActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Launcher activity for TxxT: the conversation list.
 *
 * Declared in the manifest with the MAIN/LAUNCHER intent-filter. FLAG_SECURE
 * is set in code (docs/PRIVACY.md §3) so the launcher never appears in recents
 * previews or screenshots.
 *
 * The list is live: [ConversationListLoader] combines the conversations and
 * messages tables into sorted, archive-filtered `core` [Conversation]s, and
 * every emission re-applies the current search query before submitting to
 * [ConversationListAdapter]. Tapping a row opens its [ThreadActivity]; the NEW
 * affordance prompts for a number and opens (or creates) that thread; swiping
 * left archives, swiping right deletes (WS10).
 */
class MainActivity : Activity(), SwipeActionCallback {

    /**
     * The app's theme store, backed by this activity's SharedPreferences. Wired
     * here (the launcher) so the running application reaches the store on every
     * launch: the settings screen (WS12) reads/writes it and T6's applier drives
     * the UI from its effective theme. Held on the instance so the store is
     * reachable for the lifetime of the launcher.
     */
    lateinit var themeStore: ThemeStore
        private set

    /**
     * The app's theme controller, built over [themeStore]. Wired here (the
     * launcher) so the running application reaches the manual-wins precedence
     * rule on every launch: the settings screen (WS12) and the launcher-sync
     * receiver (T5) report their intent through it, and T6's applier reads the
     * effective theme from it. Held on the instance alongside the store.
     */
    lateinit var themeController: ThemeController
        private set

    /**
     * The conversation-search filter (T3). Filters the conversation list by query
     * and submits the filtered list to the conversation-list adapter. Held on the
     * instance so the SearchView listener reaches the same filter on every query.
     */
    private val searchFilter = ConversationSearchFilter()

    /** The conversation list the launcher filters and displays. */
    private var conversations: List<Conversation> = emptyList()

    /** The current search query, re-applied when the live list re-emits. */
    private var currentQuery: String = ""

    /** The process-wide Room database behind the live list. */
    private val database: TxxTDatabase by lazy { TxxTDatabase.instance(this) }

    /**
     * The activity-scoped coroutine scope (ThreadActivity precedent). Cancelled
     * in [onDestroy] so the Room Flow collection cannot outlive the launcher.
     */
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The conversation-list adapter. Stable-id rows (the swipe helper reads
     * `viewHolder.itemId` as the conversation id); a tapped row opens its
     * thread.
     */
    private val adapter = ConversationListAdapter(
        onConversationTap = { row -> openThread(row.conversationId) },
        onConversationLongPress = { row -> promptRowActions(row.conversationId) },
    )

    private lateinit var emptyState: TextView
    private lateinit var newMessageButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        // The real store over this app's preferences; the settings screen and
        // theme applier (T6) read the persisted manual theme / auto-sync toggle.
        themeStore = ThemeStore(
            SharedPreferencesThemeKeyValueStore(
                getSharedPreferences("txxt_theme", MODE_PRIVATE)
            )
        )
        // The controller carries the manual-wins precedence over that store.
        themeController = ThemeController(themeStore)

        setContentView(R.layout.activity_main)
        emptyState = findViewById(R.id.empty_state)
        newMessageButton = findViewById(R.id.new_message_button)

        // The launcher's conversation-list host (activity_main.xml): the live
        // adapter plus the swipe helper (T1) over the same RecyclerView.
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        attachSwipeHelper(findViewById<RecyclerView>(R.id.recyclerView))

        // Search wiring (T3): the SearchView widget's query listener routes every
        // keystroke and submit through applySearchQuery, which filters the
        // conversation list and submits the result to the adapter.
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applySearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                applySearchQuery(newText ?: "")
                return true
            }
        })

        newMessageButton.setOnClickListener { promptNewConversation() }

        observeConversations()
        applyTheme()
        requestDefaultHandlerGrants()

        // A notification tap lands here carrying the sender: open their
        // thread. Only on a fresh launch — a recreate (rotation) re-delivers
        // the same intent and must not re-open the thread.
        if (savedInstanceState == null) {
            openThreadFromNotification(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // A notification tap while the launcher is already up (SINGLE_TOP).
        openThreadFromNotification(intent)
    }

    /**
     * Consumes a notification content intent's sender extra: finds (or
     * creates) that sender's conversation and opens its thread — a tapped
     * notification must land on the message, not the bare list.
     */
    private fun openThreadFromNotification(intent: Intent?) {
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return
        openNewConversation(sender)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the Room Flow collection feeding a dead activity (ThreadActivity
        // precedent) — without this every recreate leaks the launcher.
        scope.cancel()
    }

    /**
     * Collects the live conversation list. Every emission — a new inbound
     * message, a send, an archive/delete, a restore — updates [conversations]
     * and re-applies the current query, so the visible list, snippets, and
     * unread badges track Room without a manual refresh.
     */
    private fun observeConversations() {
        scope.launch {
            ConversationListLoader(database.conversationDao(), database.messageDao())
                .conversations()
                .collect { loaded ->
                    conversations = loaded
                    applySearchQuery(currentQuery)
                }
        }
    }

    /**
     * Paints the launcher's chrome from the current effective theme (T6) — the
     * same applier path the thread screen uses, so both surfaces follow the
     * store's effective theme. The NEW affordance stays borderless: the accent
     * token (the reserved bright-white signal in every preset) colors the word
     * itself and NO background tint is applied — the accent lives in the type,
     * not in a filled pill.
     */
    private fun applyTheme() {
        val root = findViewById<View>(R.id.main_root)
        ThemeApplier(themeController) { tokens ->
            root.setBackgroundColor(tokens.background.toInt())
            emptyState.setTextColor(tokens.muted.toInt())
            newMessageButton.setTextColor(tokens.accent.toInt())
        }.apply()
    }

    /** Opens the thread screen for [conversationId]. */
    private fun openThread(conversationId: Long) {
        startActivity(ThreadActivity.launchIntent(this, conversationId))
    }

    /**
     * The NEW affordance: prompts for a phone number, finds or creates its
     * conversation through [InboundStore] (the same collision-safe path inbound
     * delivery and the SENDTO hand-off use), and opens the thread.
     */
    private fun promptNewConversation() {
        val input = EditText(this).apply {
            hint = "Phone number"
            inputType = InputType.TYPE_CLASS_PHONE
            typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(this)
            .setTitle("New message")
            .setView(input)
            .setPositiveButton("Open") { _, _ ->
                val address = input.text?.toString()?.trim().orEmpty()
                if (address.isNotEmpty()) openNewConversation(address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Finds or creates the conversation for [address] and opens its thread. */
    private fun openNewConversation(address: String) {
        scope.launch {
            val conversationId =
                InboundStore.findOrCreateConversation(database.conversationDao(), address)
            openThread(conversationId)
        }
    }

    /**
     * Asks for the two grants the manifest cannot self-grant (the manifest
     * comment at `app/src/main/AndroidManifest.xml:14-16` promises both):
     * the default-SMS-handler role and the API 33+ POST_NOTIFICATIONS runtime
     * permission.
     *
     * The SMS role goes through [DefaultHandlerMonitor.roleRequest], which is
     * honest about the platform: on API 29+ it returns the system
     * `RoleManager` request intent only while the role is available and not
     * already held; on API <29 there is no RoleManager path, so it returns
     * null and the user must grant the default-handler role manually through
     * system settings. Because the "not held" check lives inside the monitor,
     * this fires on every [onCreate] while the role stays unheld —
     * re-prompting after a denial is accepted for a sideloaded single-user
     * app. POST_NOTIFICATIONS below API 33 is auto-granted, so the prompt is
     * skipped there.
     */
    private fun requestDefaultHandlerGrants() {
        val roleIntent = DefaultHandlerMonitor().roleRequest(this)
        if (shouldRequestRole(roleIntent)) {
            startActivityForResult(roleIntent!!, REQUEST_ROLE_SMS)
        }
        val notificationsGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission(Build.VERSION.SDK_INT, notificationsGranted)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
        }
    }

    /**
     * The conversation-list swipe seam (WS10). The launcher constructs a
     * [ConversationSwipeHelper] over this activity (the [SwipeActionCallback])
     * and attaches it to the conversation-list RecyclerView; the helper hands
     * swiped rows' stable ids to the callbacks below.
     */
    fun attachSwipeHelper(recyclerView: RecyclerView) {
        ConversationSwipeHelper(this).attachTo(recyclerView)
    }

    /**
     * The conversation-search seam (T3). Filters the conversation list by [query]
     * through [ConversationSearchFilter] and submits the filtered list to the
     * conversation-list adapter. The SearchView listener calls this on every query
     * change and submit — the reachable path the running app drives when the user
     * types in the launcher's search box.
     */
    fun applySearchQuery(query: String) {
        currentQuery = query
        val filtered = searchFilter.filterConversations(conversations, query)
        adapter.submit(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Long-press actions for a conversation row: pin/unpin (feeds the
     * PINNED_FIRST ordering), star/unstar (the persisted starred-contacts set
     * that bypasses every blocking rule, docs/PRIVACY.md §6), call, block the
     * sender (adds them to the persisted blocked-addresses set and re-applies
     * the live filter), and archive (the swipe-left action, offered here too
     * for discoverability). Reads the persisted state first so the dialog
     * names the toggles it will actually perform.
     */
    private fun promptRowActions(conversationId: Long) {
        scope.launch {
            val entity = database.conversationDao().getById(conversationId) ?: return@launch
            val address = entity.participantAddresses
                .split(ADDRESS_DELIMITER)
                .firstOrNull { it.isNotBlank() }
            val pinLabel = if (entity.isPinned) "Unpin" else "Pin"
            val starLabel = if (
                address != null && BlockingRules.isStarred(blockingMap(), address)
            ) "Unstar" else "Star"
            AlertDialog.Builder(this@MainActivity)
                .setTitle(entity.participantAddresses.replace(ADDRESS_DELIMITER, ", "))
                .setItems(
                    arrayOf(pinLabel, starLabel, "Call", "Block sender", "Archive")
                ) { _, which ->
                    when (which) {
                        0 -> togglePinned(conversationId)
                        1 -> address?.let { toggleStarred(it) }
                        2 -> onCall(conversationId)
                        3 -> address?.let { confirmBlockSender(it) }
                        4 -> onArchive(conversationId)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** The persisted blocking/starred string map (the settings-screen shape). */
    private fun blockingMap(): Map<String, String> {
        val prefs = getSharedPreferences("txxt_settings", MODE_PRIVATE)
        return com.piercingxx.txxt.ui.SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
    }

    /** Persists [map] and re-applies it to the live inbound filter. */
    private fun persistBlockingMap(map: Map<String, String>) {
        val prefs = getSharedPreferences("txxt_settings", MODE_PRIVATE)
        prefs.edit().apply {
            map.forEach { (key, value) -> putString(key, value) }
        }.apply()
        com.piercingxx.txxt.ui.SettingsBlockingStore.fromMap(map).loadAndApply()
    }

    /** Toggles [address] in the persisted starred-contacts set, live. */
    private fun toggleStarred(address: String) {
        val (map, nowStarred) = BlockingRules.withStarredToggled(blockingMap(), address)
        persistBlockingMap(map)
        Toast.makeText(
            this,
            if (nowStarred) "Starred $address" else "Unstarred $address",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * Confirms, then adds [address] to the persisted blocked set and
     * re-applies the live filter — the very next message from them is dropped.
     * Reversible in Settings → Blocking & starred.
     */
    private fun confirmBlockSender(address: String) {
        AlertDialog.Builder(this)
            .setTitle("Block $address?")
            .setMessage("New messages from this sender will be dropped. Undo in Settings → Blocking & starred.")
            .setPositiveButton("Block") { _, _ ->
                persistBlockingMap(
                    BlockingRules.withEntry(
                        blockingMap(),
                        com.piercingxx.txxt.ui.SettingsBlockingStore.KEY_BLOCKED_ADDRESSES,
                        address,
                    )
                )
                Toast.makeText(this, "Blocked $address", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Flips a conversation's persisted pinned flag (PINNED_FIRST ordering). */
    private fun togglePinned(conversationId: Long) {
        scope.launch {
            val dao = database.conversationDao()
            dao.getById(conversationId)?.let { dao.update(it.copy(isPinned = !it.isPinned)) }
        }
    }

    /**
     * Swipe left: archive (WS10). Sets the persisted archive flag; the live
     * list drops the row on the next emission (archiving hides, never deletes).
     */
    override fun onArchive(conversationId: Long) {
        scope.launch {
            val dao = database.conversationDao()
            dao.getById(conversationId)?.let { dao.update(it.copy(isArchived = true)) }
        }
    }

    /** Swipe right: delete the conversation and its messages (WS10). */
    override fun onDelete(conversationId: Long) {
        scope.launch {
            database.messageDao().deleteForConversation(conversationId)
            database.conversationDao().deleteById(conversationId)
        }
    }

    /**
     * Call-through (WS10): opens the dialer pre-filled with the conversation's
     * first participant. ACTION_DIAL needs no permission — the user places the
     * call from the dialer.
     */
    override fun onCall(conversationId: Long) {
        scope.launch {
            val number = database.conversationDao().getById(conversationId)
                ?.participantAddresses
                ?.split(ADDRESS_DELIMITER)
                ?.firstOrNull { it.isNotBlank() }
                ?: return@launch
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
    }

    // Scheduled sending is deferred (docs/FEATURES.md open question) and is not
    // reachable from the two swipe directions the helper maps; kept as a no-op
    // so the SwipeActionCallback surface stays complete.
    override fun onSchedule(conversationId: Long) {}

    companion object {
        /** Request code for the default-SMS-handler role request. */
        const val REQUEST_ROLE_SMS = 4_001

        /** Request code for the POST_NOTIFICATIONS runtime-permission prompt. */
        const val REQUEST_POST_NOTIFICATIONS = 4_002

        /** Delimiter joining participant addresses in the conversations table. */
        private const val ADDRESS_DELIMITER = "\u0001"

        /**
         * Whether the launcher should start a default-SMS-role request. Pure
         * over its input — JVM-testable: exactly when [roleRequest] produced
         * an intent (a null means nothing to ask for — already held,
         * unavailable, or API <29 with no RoleManager path).
         */
        fun shouldRequestRole(roleRequest: Intent?): Boolean = roleRequest != null

        /**
         * Whether the launcher should raise the POST_NOTIFICATIONS prompt.
         * Pure over its inputs — JVM-testable: only on API 33+ where the
         * permission exists as a runtime grant ([granted] reports the current
         * check); below 33 it is auto-granted, so never prompt.
         */
        fun needsNotificationPermission(sdkInt: Int, granted: Boolean): Boolean =
            sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted
    }
}
