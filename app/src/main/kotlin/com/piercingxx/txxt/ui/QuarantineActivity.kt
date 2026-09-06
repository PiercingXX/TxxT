package com.piercingxx.txxt.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.contacts.ContactNameResolver
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.data.QuarantineStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Review surface for quarantined inbound SMS/MMS (todo.md T2).
 *
 * Held threads are unread and hidden from the main list. Per sender the
 * operator can deliver (release to inbox + override so future messages
 * land there), block (add to the blocked set and delete), or delete.
 */
class QuarantineActivity : Activity() {

    private val database: TxxTDatabase by lazy { TxxTDatabase.instance(this) }
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val contactNames: ContactNameResolver by lazy { ContactNameResolver(this) }

    private val themeController: ThemeController by lazy {
        ThemeController(
            ThemeStore(
                SharedPreferencesThemeKeyValueStore(
                    getSharedPreferences(SettingsActivity.THEME_PREFS_NAME, MODE_PRIVATE)
                )
            )
        )
    }

    private val adapter = ConversationListAdapter(
        onConversationTap = { row -> promptDisposition(row.conversationId) },
        displayName = { address -> contactNames.labelFor(address) },
    )

    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quarantine)
        emptyState = findViewById(R.id.quarantine_empty)
        val list = findViewById<RecyclerView>(R.id.quarantine_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        observeHeld()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun applyTheme() {
        val root = findViewById<View>(R.id.quarantine_root)
        ThemeApplier(themeController) { tokens ->
            AppCompatDelegate.setDefaultNightMode(ThemeApplier.nightModeFor(tokens.isDark))
            ThemeApplier.paintChrome(root, tokens)
            adapter.applyTheme(tokens)
        }.apply()
    }

    private fun observeHeld() {
        scope.launch {
            ConversationListLoader(database.conversationDao(), database.messageDao())
                .quarantined()
                .collect { held ->
                    submit(held)
                }
        }
    }

    private fun submit(held: List<Conversation>) {
        adapter.submit(held)
        emptyState.visibility = if (held.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun promptDisposition(conversationId: Long) {
        scope.launch {
            val entity = database.conversationDao().getById(conversationId) ?: return@launch
            val address = QuarantineStore.senderAddress(entity) ?: return@launch
            val title = ConversationListPresenter.title(listOf(address)) { contactNames.labelFor(it) }
            AlertDialog.Builder(this@QuarantineActivity)
                .setTitle(title)
                .setItems(arrayOf("Deliver", "Block", "Delete")) { _, which ->
                    when (which) {
                        0 -> deliverSender(conversationId, address)
                        1 -> blockSender(conversationId, address)
                        2 -> deleteSender(conversationId)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deliverSender(conversationId: Long, address: String) {
        scope.launch {
            QuarantineStore.deliver(database.conversationDao(), conversationId)
            LiveInboundFilter.overrideStore(this@QuarantineActivity).addOverride(address)
            Toast.makeText(this@QuarantineActivity, "Delivered $address", Toast.LENGTH_SHORT).show()
        }
    }

    private fun blockSender(conversationId: Long, address: String) {
        scope.launch {
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            val map = SettingsBlockingStore.KEY_NAMES
                .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
                .toMap()
            val next = BlockingRules.withEntry(
                map,
                SettingsBlockingStore.KEY_BLOCKED_ADDRESSES,
                address,
            )
            prefs.edit().apply {
                next.forEach { (key, value) -> putString(key, value) }
            }.apply()
            SettingsBlockingStore.fromMap(next).loadAndApply()
            QuarantineStore.delete(
                database.conversationDao(),
                database.messageDao(),
                conversationId,
            )
            Toast.makeText(this@QuarantineActivity, "Blocked $address", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSender(conversationId: Long) {
        scope.launch {
            QuarantineStore.delete(
                database.conversationDao(),
                database.messageDao(),
                conversationId,
            )
            Toast.makeText(this@QuarantineActivity, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }
}
