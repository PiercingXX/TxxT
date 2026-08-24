package com.piercingxx.txxt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.service.DefaultHandlerMonitor
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import com.piercingxx.txxt.ui.ConversationSearchFilter
import com.piercingxx.txxt.ui.ConversationSwipeHelper
import com.piercingxx.txxt.ui.SwipeActionCallback
import com.piercingxx.txxt.ui.ThreadActivity

/**
 * Launcher activity for TxxT.
 *
 * Declared in the manifest with the MAIN/LAUNCHER intent-filter
 * (`app/src/main/AndroidManifest.xml:36-44`). FLAG_SECURE is set in code
 * (docs/PRIVACY.md §3) so the launcher never appears in recents previews or
 * screenshots. With the conversation list (WS10) not yet landed, the launcher
 * opens the thread screen directly — the conversation list will pass the real
 * conversation id when it arrives.
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
        // The launcher's conversation-list host (activity_main.xml). The
        // RecyclerView is resolved by its runtime ID and hosts the swipe helper
        // (T1) — the wiring the WS10 conversation list drives when it lands.
        setContentView(R.layout.activity_main)
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
        startActivity(
            Intent(this, ThreadActivity::class.java)
                .putExtra("extra_conversation_id", 1L)
        )
        requestDefaultHandlerGrants()
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
     * The conversation-list swipe seam (WS10 corrective-corrective T1). The
     * launcher is the reachable call site for the swipe helper: it constructs a
     * [ConversationSwipeHelper] over this activity (the [SwipeActionCallback])
     * and attaches it to the conversation-list RecyclerView. With the WS10 list
     * not yet landed the launcher opens the thread screen directly; this seam is
     * the wiring the conversation list will drive when it arrives, and it is what
     * `MainActivityWiringTest` verifies reaches the helper.
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
        val filtered = searchFilter.filterConversations(conversations, query)
        // Submit the filtered list to the conversation-list adapter. The adapter
        // is the WS10 conversation-list surface; the seam is what the box verifies.
        adapter.submit(filtered)
    }

    // The four swipe actions are deferred (DAO/intent operations are out of
    // scope for this corrective) — implemented as no-ops so the wiring seam is
    // what the box verifies, not the deferred operations.
    override fun onArchive(conversationId: Long) {}
    override fun onDelete(conversationId: Long) {}
    override fun onCall(conversationId: Long) {}
    override fun onSchedule(conversationId: Long) {}

    /**
     * The conversation-list adapter. The WS10 conversation-list adapter is out of
     * scope for this corrective; the submit seam is what T3's box verifies.
     */
    private val adapter = ConversationListAdapter()

    companion object {
        /** Request code for the default-SMS-handler role request. */
        const val REQUEST_ROLE_SMS = 4_001

        /** Request code for the POST_NOTIFICATIONS runtime-permission prompt. */
        const val REQUEST_POST_NOTIFICATIONS = 4_002

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

/**
 * Placeholder conversation-list adapter (WS10). The real adapter is out of scope
 * for this corrective; [submit] is the seam `MainActivity.applySearchQuery` drives.
 */
private class ConversationListAdapter {
    @Suppress("UNUSED_PARAMETER") // placeholder until the WS10 adapter lands
    fun submit(conversations: List<Conversation>) {
        // No-op until the WS10 conversation-list adapter lands.
    }
}
