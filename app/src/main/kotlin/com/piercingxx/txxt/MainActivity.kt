package com.piercingxx.txxt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.contacts.ContactNameResolver
import com.piercingxx.txxt.contacts.PhoneLookupIdentity
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.MuteUntil
import com.piercingxx.txxt.core.RoleSwitchCopy
import com.piercingxx.txxt.data.BackupJson
import com.piercingxx.txxt.data.ConversationExporter
import com.piercingxx.txxt.data.ConversationMute
import com.piercingxx.txxt.data.ConversationPin as PinStore
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.RoomRestoreService
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.service.DefaultHandlerMonitor
import com.piercingxx.txxt.service.DialerBusinessTier
import com.piercingxx.txxt.service.DialerGroups
import com.piercingxx.txxt.service.EXTRA_SENDER
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import com.piercingxx.txxt.ui.BlockingRules
import com.piercingxx.txxt.ui.ConversationListAdapter
import com.piercingxx.txxt.ui.GroupGlyphs
import com.piercingxx.txxt.ui.ConversationListLoader
import com.piercingxx.txxt.ui.ConversationSearchFilter
import com.piercingxx.txxt.ui.ConversationVisibility
import com.piercingxx.txxt.ui.ConversationSwipeHelper
import com.piercingxx.txxt.ui.NewConversationActivity
import com.piercingxx.txxt.ui.QuarantineActivity
import com.piercingxx.txxt.ui.SettingsBackup
import com.piercingxx.txxt.ui.SettingsBlockingStore
import com.piercingxx.txxt.ui.SettingsStore
import com.piercingxx.txxt.ui.SwipeActionCallback
import com.piercingxx.txxt.ui.ThreadActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Launcher activity for TxxT: the conversation list.
 *
 * Declared in the manifest with the MAIN/LAUNCHER intent-filter.
 *
 * The list is live: [ConversationListLoader] combines the conversations and
 * messages tables into sorted, archive-filtered `core` [Conversation]s, and
 * every emission re-applies the current search query before submitting to
 * [ConversationListAdapter]. Tapping a row opens its [ThreadActivity]; the NEW
 * affordance opens [NewConversationActivity] so a thread can start from a
 * contact name or a typed number; swiping right archives, swiping left
 * deletes (WS10).
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
     * Resolves participant numbers to the names saved in the system contacts
     * provider — the store the dialer reads — so a saved contact shows as a
     * name instead of a raw number. Held on the instance (not rebuilt per
     * submit) because the resolver OWNS the LRU cache that keeps the provider
     * off the list-refresh path; a fresh instance per refresh would be a cold
     * cache and a query storm. `by lazy` because a `Context` is only valid
     * after `onCreate`.
     */
    private val contactNames: ContactNameResolver by lazy { ContactNameResolver(this) }

    /**
     * The conversation-list adapter. Stable-id rows (the swipe helper reads
     * `viewHolder.itemId` as the conversation id); a tapped row opens its
     * thread. The `displayName` seam is a lambda, not `contactNames::labelFor`,
     * so this field initialiser does not force the lazy resolver (and its
     * `Context`) before `onCreate` has run.
     */
    private val adapter = ConversationListAdapter(
        onConversationTap = { row -> openThread(row.conversationId) },
        onConversationLongPress = { row -> promptRowActions(row.conversationId) },
        displayName = { address -> contactNames.labelFor(address) },
        titleMarks = { addresses -> conversationMarks(addresses) },
    )

    private var marksSnap: MarksSnap = MarksSnap()

    private data class MarksSnap(
        val biz: Set<String> = emptySet(),
        val family: Set<String> = emptySet(),
        val blocked: Set<String> = emptySet(),
        val map: Map<String, String> = emptyMap(),
    )

    private lateinit var emptyState: TextView
    private lateinit var newMessageButton: Button
    private lateinit var settingsButton: Button
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var roleBanner: TextView
    private lateinit var quarantineBanner: TextView
    private var runtimePermissionsAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        settingsButton = findViewById(R.id.settings_button)
        exportButton = findViewById(R.id.export_button)
        importButton = findViewById(R.id.import_button)
        roleBanner = findViewById(R.id.role_banner)
        quarantineBanner = findViewById(R.id.quarantine_banner)

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
        settingsButton.setOnClickListener {
            startActivity(Intent(this, com.piercingxx.txxt.ui.SettingsActivity::class.java))
        }
        exportButton.setOnClickListener { launchExport() }
        importButton.setOnClickListener { launchImport() }
        roleBanner.setOnClickListener {
            val roleIntent = DefaultHandlerMonitor().roleRequest(this)
            if (shouldRequestRole(roleIntent)) {
                explainThenRequestRole(roleIntent!!)
            }
        }
        quarantineBanner.setOnClickListener {
            startActivity(Intent(this, QuarantineActivity::class.java))
        }

        observeConversations()
        observeQuarantineBanner()
        applyTheme()
        requestDefaultHandlerGrants()
        refreshRoleBanner()

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

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshRoleBanner()
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

    private fun observeQuarantineBanner() {
        scope.launch {
            ConversationListLoader(database.conversationDao(), database.messageDao())
                .quarantined()
                .collect { held ->
                    quarantineBanner.visibility = if (held.isEmpty()) View.GONE else View.VISIBLE
                    quarantineBanner.text = if (held.size == 1) {
                        "1 held message. Tap to review."
                    } else {
                        "${held.size} held messages. Tap to review."
                    }
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
            AppCompatDelegate.setDefaultNightMode(ThemeApplier.nightModeFor(tokens.isDark))
            root.setBackgroundColor(tokens.background.toInt())
            emptyState.setTextColor(tokens.muted.toInt())
            newMessageButton.setTextColor(tokens.accent.toInt())
            settingsButton.setTextColor(tokens.accent.toInt())
            exportButton.setTextColor(tokens.accent.toInt())
            importButton.setTextColor(tokens.accent.toInt())
            roleBanner.setTextColor(tokens.accent.toInt())
            quarantineBanner.setTextColor(tokens.accent.toInt())
            adapter.applyTheme(tokens)
        }.apply()
    }

    /** Opens the thread screen for [conversationId]. */
    private fun openThread(conversationId: Long) {
        startActivity(ThreadActivity.launchIntent(this, conversationId))
    }

    /**
     * The NEW affordance: opens the recipient picker so a thread can start
     * from a saved contact or a typed number. The phone-pad dialog this
     * used to be could not type letters, so a contact was unreachable from
     * NEW.
     */
    private fun promptNewConversation() {
        startActivity(Intent(this, NewConversationActivity::class.java))
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
     * Asks for the grants the manifest cannot self-grant (the manifest
     * comment at `app/src/main/AndroidManifest.xml:14-16` promises them):
     * the default-SMS-handler role, the API 33+ POST_NOTIFICATIONS runtime
     * permission, and READ_CONTACTS.
     *
     * READ_CONTACTS rides along here rather than getting its own moment
     * because it is asked for the same reason as the others — the launcher is
     * the first surface the operator sees, and the conversation list it is
     * about to paint is exactly what the permission improves. Declining is a
     * supported end state, not an error: [ContactNameResolver] falls back to
     * the number, so the list stays fully usable and no row goes blank.
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
            explainThenRequestRole(roleIntent!!)
            return
        }
        requestRuntimePermissions()
    }

    /**
     * First-run / in-app role request (todo.md T3). The system picker is
     * out of our hands; this dialog is the honest copy: the archive lives
     * only in this app and dies on uninstall unless exported.
     */
    private fun explainThenRequestRole(roleIntent: Intent) {
        AlertDialog.Builder(this)
            .setTitle("Default SMS app")
            .setMessage(RoleSwitchCopy.FIRST_RUN)
            .setPositiveButton("Set as default") { _, _ ->
                startActivityForResult(roleIntent, REQUEST_ROLE_SMS)
            }
            .setNegativeButton("Not now") { _, _ ->
                requestRuntimePermissions()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * One `requestPermissions` call for every missing runtime grant. Two
     * overlapping prompts cancel each other on Android.
     */
    private fun requestRuntimePermissions() {
        if (runtimePermissionsAsked) return
        val needed = neededRuntimePermissions(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED,
            contactsGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED,
        )
        if (needed.isEmpty()) return
        runtimePermissionsAsked = true
        ActivityCompat.requestPermissions(
            this,
            needed.toTypedArray(),
            REQUEST_RUNTIME_PERMISSIONS,
        )
    }

    private fun refreshRoleBanner() {
        val held = DefaultHandlerMonitor().isHeld(this)
        roleBanner.visibility = if (held) View.GONE else View.VISIBLE
    }

    @Deprecated("startActivityForResult: this screen is a plain Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ROLE_SMS) {
            refreshRoleBanner()
            requestRuntimePermissions()
        } else if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_EXPORT -> data?.data?.let(::performExport)
                REQUEST_IMPORT -> data?.data?.let(::performImport)
            }
        }
    }

    /**
     * Repaints the list once the operator answers the READ_CONTACTS prompt.
     *
     * Both answers need this, not just the grant: every label the resolver
     * cached before the prompt was computed under the OLD answer, so the cache
     * is dropped and the visible list is re-submitted through the presenter.
     * Without it a grant would show names only after the next Room emission —
     * which, on a quiet phone, could be hours.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RUNTIME_PERMISSIONS ||
            requestCode == REQUEST_READ_CONTACTS
        ) {
            contactNames.clearCache()
            applySearchQuery(currentQuery)
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
        marksSnap = MarksSnap(
            biz = DialerBusinessTier.load(this)?.keys.orEmpty(),
            family = DialerGroups.keysNamed(this, "Family"),
            blocked = DialerGroups.keysNamed(this, DialerGroups.BLOCKED),
            map = blockingMap(),
        )
        val filtered = searchFilter.filterConversations(conversations, query) { address ->
            contactNames.labelFor(address)
        }.filter { conversation ->
            ConversationVisibility.showOnList(
                isBlockedConversation(conversation.participantAddresses),
                query,
            )
        }
        adapter.submit(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Long-press actions for a conversation row: pin/unpin (feeds the
     * PINNED_FIRST ordering), star/unstar (the persisted starred-contacts set
     * that bypasses every blocking rule, docs/PRIVACY.md §6), call, block the
     * sender (adds them to the persisted blocked-addresses set and re-applies
     * the live filter), and archive (the swipe-right action, offered here too
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
            val muteLabel = "Mute…"
            val starLabel = if (
                address != null && BlockingRules.isStarred(blockingMap(), address)
            ) "${GroupGlyphs.STAR} Unstar" else "${GroupGlyphs.STAR} Star"
            AlertDialog.Builder(this@MainActivity)
                // Same title the row shows: resolved through the contacts
                // provider, so the dialog does not regress to a bare number
                // for a contact the list just named.
                .setTitle(
                    com.piercingxx.txxt.ui.ConversationListPresenter.title(
                        entity.participantAddresses
                            .split(ADDRESS_DELIMITER)
                            .filter { it.isNotBlank() },
                    ) { address -> contactNames.labelFor(address) }
                )
                .setItems(
                    arrayOf(
                        pinLabel,
                        muteLabel,
                        starLabel,
                        "Copy number",
                        "Call",
                        "${GroupGlyphs.BLOCK} Block sender",
                        "Archive",
                    )
                ) { _, which ->
                    when (which) {
                        0 -> togglePinned(conversationId)
                        1 -> promptMute(conversationId, entity)
                        2 -> address?.let { toggleStarred(it) }
                        3 -> address?.let { copyNumber(it) }
                        4 -> onCall(conversationId)
                        5 -> address?.let { confirmBlockSender(it) }
                        6 -> onArchive(conversationId)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun conversationMarks(addresses: Collection<String>): String {
        val address = addresses.firstOrNull { it.isNotBlank() } ?: return ""
        val hit = PhoneLookupIdentity.lookup(this, address)
        val key = hit.lookupKey
        return GroupGlyphs.marks(
            starred = hit.starred || BlockingRules.isStarred(marksSnap.map, address),
            business = key != null && key in marksSnap.biz,
            family = key != null && key in marksSnap.family,
            blocked = isBlockedConversation(listOf(address)),
        )
    }

    private fun isBlockedConversation(addresses: Collection<String>): Boolean {
        val address = addresses.firstOrNull { it.isNotBlank() } ?: return false
        if (BlockingRules.isBlocked(marksSnap.map, address)) return true
        val key = PhoneLookupIdentity.lookup(this, address).lookupKey
        return key != null && key in marksSnap.blocked
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

    /**
     * Mute-until presets plus forever-mute (todo.md T5). Notifications stay
     * off until the wall time; the thread still receives. Unmute is one tap
     * when a mute is already active.
     */
    private fun promptMute(conversationId: Long, entity: com.piercingxx.txxt.data.ConversationEntity) {
        val now = System.currentTimeMillis()
        val items = mutableListOf<String>()
        if (ConversationMute.isActive(entity, now)) items += "Unmute"
        items += listOf(
            "Mute 1 hour",
            "Mute 8 hours",
            "Mute until tonight",
            "Mute until Monday",
            "Mute forever",
        )
        AlertDialog.Builder(this)
            .setTitle("Mute")
            .setItems(items.toTypedArray()) { _, which ->
                applyMuteChoice(conversationId, items[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyMuteChoice(conversationId: Long, choice: String) {
        scope.launch {
            val dao = database.conversationDao()
            val entity = dao.getById(conversationId) ?: return@launch
            val now = System.currentTimeMillis()
            val updated = when (choice) {
                "Unmute" -> entity.copy(isMuted = false, mutedUntilMillis = 0L)
                "Mute 1 hour" -> entity.copy(
                    isMuted = false,
                    mutedUntilMillis = MuteUntil.expiryMillis(MuteUntil.Preset.ONE_HOUR, now),
                )
                "Mute 8 hours" -> entity.copy(
                    isMuted = false,
                    mutedUntilMillis = MuteUntil.expiryMillis(MuteUntil.Preset.EIGHT_HOURS, now),
                )
                "Mute until tonight" -> entity.copy(
                    isMuted = false,
                    mutedUntilMillis = MuteUntil.expiryMillis(MuteUntil.Preset.TONIGHT, now),
                )
                "Mute until Monday" -> entity.copy(
                    isMuted = false,
                    mutedUntilMillis = MuteUntil.expiryMillis(MuteUntil.Preset.MONDAY, now),
                )
                "Mute forever" -> entity.copy(isMuted = true, mutedUntilMillis = 0L)
                else -> return@launch
            }
            dao.update(updated)
            Toast.makeText(
                this@MainActivity,
                if (choice == "Unmute") "Unmuted" else choice,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun copyNumber(address: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("number", address))
        Toast.makeText(this, "Copied $address", Toast.LENGTH_SHORT).show()
    }

    /** Flips a conversation's persisted pinned flag, capped at five. */
    private fun togglePinned(conversationId: Long) {
        scope.launch {
            when (PinStore.toggle(database.conversationDao(), conversationId)) {
                PinStore.Result.PINNED ->
                    Toast.makeText(this@MainActivity, "Pinned", Toast.LENGTH_SHORT).show()
                PinStore.Result.UNPINNED ->
                    Toast.makeText(this@MainActivity, "Unpinned", Toast.LENGTH_SHORT).show()
                PinStore.Result.CAP_REACHED ->
                    Toast.makeText(
                        this@MainActivity,
                        "Pin limit is ${PinStore.MAX_PINNED}",
                        Toast.LENGTH_SHORT,
                    ).show()
                PinStore.Result.MISSING -> Unit
            }
        }
    }

    /**
     * Swipe right: archive (WS10). No confirm — archiving hides, never deletes.
     * The live list drops the row on the next emission.
     */
    override fun onArchive(conversationId: Long) {
        scope.launch {
            val dao = database.conversationDao()
            dao.getById(conversationId)?.let { dao.update(it.copy(isArchived = true)) }
        }
    }

    /**
     * Swipe left: confirm before deleting. The swipe helper has already
     * snapped the row back, so Cancel is a no-op.
     */
    override fun onDelete(conversationId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete this conversation?")
            .setMessage("All messages in this thread will be removed. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch {
                    database.messageDao().deleteForConversation(conversationId)
                    database.conversationDao().deleteById(conversationId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    /**
     * User-visible conversation export (todo.md T1). Opens the SAF
     * create-document picker; the chosen URI is written in [onActivityResult]
     * with the serialized backup of every conversation and message.
     */
    private fun launchExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "txxt-backup.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    /**
     * User-visible conversation import (todo.md T1). Opens the SAF
     * open-document picker; the chosen JSON is parsed and restored through
     * [RoomRestoreService] in [onActivityResult].
     */
    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    /**
     * Builds and writes the backup JSON to [uri]. Every conversation and
     * message is mapped onto the backup format by [ConversationExporter], then
     * serialized by [BackupJson] and written through the content resolver. The
     * honest photo note is surfaced: photos are never in the JSON, so the user
     * is told how many were left behind rather than left to assume they
     * travelled with the export.
     */
    private fun performExport(uri: Uri) {
        scope.launch {
            try {
                val export = ConversationExporter.buildBackup(
                    conversations = database.conversationDao().getAll(),
                    messages = database.messageDao().getAll(),
                    settings = SettingsBackup.toSettingsMap(currentSettingsStore()),
                    blocklist = SettingsBlockingStore.fromMap(blockingMap()).blockedAddresses().toList(),
                    starred = SettingsBlockingStore.fromMap(blockingMap()).starredContacts().toList(),
                )
                val json = BackupJson.serialize(export.backup)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: throw IOException("could not open export destination")
                val note = if (export.photoCount > 0) {
                    " / $export.photoCount photos not in this JSON"
                } else {
                    ""
                }
                Toast.makeText(
                    this@MainActivity,
                    "Exported ${export.backup.messages.size} messages$note",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Reads [uri], parses and validates it through [BackupJson], and restores it
     * through [RoomRestoreService] — the idempotent REPLACE path that overwrites
     * the same primary keys instead of duplicating, so a re-import never doubles
     * a message and live ids are never destroyed.
     */
    private fun performImport(uri: Uri) {
        scope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
                    ?: throw IOException("could not open import source")
                val data = BackupJson.deserialize(json)
                val plan = RoomRestoreService(database).restore(data)
                Toast.makeText(
                    this@MainActivity,
                    "Restored ${plan.messages.size} messages",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** The settings store the backup format expects (the settings-screen shape). */
    private fun currentSettingsStore(): SettingsStore =
        SettingsBackup.fromSettingsMap(
            SettingsBackup.KEY_NAMES.associateWith { key ->
                getSharedPreferences("txxt_settings", MODE_PRIVATE).getString(key, null)
            }.filterValues { it != null }.mapValues { it.value!! },
        )

    companion object {
        /** Request code for the default-SMS-handler role request. */
        const val REQUEST_ROLE_SMS = 4_001

        /** Request code for the combined runtime-permission prompt. */
        const val REQUEST_RUNTIME_PERMISSIONS = 4_002

        /** Request code for the READ_CONTACTS runtime-permission prompt (legacy). */
        const val REQUEST_READ_CONTACTS = 4_003

        /** Request code for the SAF create-document conversation export. */
        const val REQUEST_EXPORT = 4_004

        /** Request code for the SAF open-document conversation import. */
        const val REQUEST_IMPORT = 4_005

        const val REQUEST_POST_NOTIFICATIONS = REQUEST_RUNTIME_PERMISSIONS

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

        /**
         * Whether the launcher should raise the READ_CONTACTS prompt. Pure over
         * its input — JVM-testable, and stated as its own named decision (the
         * [needsNotificationPermission] precedent) so the ask/no-ask rule is
         * testable without an Activity.
         *
         * Unlike POST_NOTIFICATIONS there is no API floor: READ_CONTACTS has
         * been a runtime permission since API 23 and `minSdk` is 24, so the
         * only question is whether it is already held. Re-prompting after a
         * denial is accepted for a sideloaded single-user app (the same
         * decision the SMS-role request makes); the platform stops showing the
         * dialog after the operator picks "don't ask again", and the app
         * remains fully usable on numbers.
         */
        fun needsContactsPermission(granted: Boolean): Boolean = !granted

        /**
         * Runtime permissions to ask in a single prompt. Empty when nothing
         * is missing. Pure — JVM-testable.
         */
        fun neededRuntimePermissions(
            sdkInt: Int,
            notificationsGranted: Boolean,
            contactsGranted: Boolean,
        ): List<String> {
            val needed = mutableListOf<String>()
            if (needsNotificationPermission(sdkInt, notificationsGranted)) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
            if (needsContactsPermission(contactsGranted)) {
                needed += Manifest.permission.READ_CONTACTS
            }
            return needed
        }
    }
}
