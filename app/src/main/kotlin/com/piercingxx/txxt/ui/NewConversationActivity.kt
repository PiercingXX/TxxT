package com.piercingxx.txxt.ui

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.contacts.ContactDirectory
import com.piercingxx.txxt.contacts.ContactEntry
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeApplier
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The NEW-conversation recipient picker.
 *
 * Replaces the phone-pad dialog the launcher used to show: a text search
 * field (name *or* number) over the system contacts book, with a typed
 * number still opening a thread when it is not in the book. Tapping a row
 * (or submitting the IME) finds or creates the conversation through
 * [InboundStore] — the same collision-safe path inbound delivery and the
 * SENDTO hand-off use — and opens [ThreadActivity].
 *
 * Declining `READ_CONTACTS` is a supported end state: the list is empty
 * and the field still accepts a number.
 */
class NewConversationActivity : Activity() {

    private val database: TxxTDatabase by lazy { TxxTDatabase.instance(this) }

    private val directory: ContactDirectory by lazy { ContactDirectory(this) }

    private val themeController: ThemeController by lazy {
        ThemeController(
            ThemeStore(
                SharedPreferencesThemeKeyValueStore(
                    getSharedPreferences("txxt_theme", MODE_PRIVATE)
                )
            )
        )
    }

    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> }
    )

    private lateinit var emptyState: TextView
    private lateinit var searchView: SearchView

    private var contacts: List<ContactEntry> = emptyList()
    private var currentQuery: String = ""
    private var opening: Boolean = false

    private val adapter = RecipientAdapter(
        onRecipientTap = { row ->
            when (row) {
                is RecipientRow.UseNumber -> openAddress(row.number)
                is RecipientRow.Contact -> openAddress(row.entry.number)
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_conversation)

        emptyState = findViewById(R.id.empty_state)
        searchView = findViewById(R.id.recipient_search)
        val recyclerView = findViewById<RecyclerView>(R.id.recipient_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchView.isIconified = false
        // Load-bearing: a phone inputType is what made NEW number-only.
        searchView.inputType = InputType.TYPE_CLASS_TEXT
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applyQuery(query.orEmpty())
                RecipientPicker.addressOnSubmit(query.orEmpty(), contacts)?.let { openAddress(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                applyQuery(newText.orEmpty())
                return true
            }
        })
        searchView.requestFocus()

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * Reloads the contacts book off the main thread and re-applies the
     * current query. Called from [onResume] so a grant that arrives while
     * the picker is open (or after a trip through system settings) paints
     * names without a recreate.
     */
    private fun loadContacts() {
        scope.launch {
            contacts = withContext(Dispatchers.IO) { directory.all() }
            applyQuery(currentQuery)
        }
    }

    /**
     * Filters [contacts] through [RecipientPicker.rows] and submits the
     * result. A blank, non-matching query with no typed number shows the
     * empty line; a typed number is itself a row, so the empty line hides.
     */
    internal fun applyQuery(query: String) {
        currentQuery = query
        val rows = RecipientPicker.rows(contacts, query)
        adapter.submit(rows)
        emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Finds or creates the conversation for [address] and opens its thread. */
    private fun openAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty() || opening) return
        opening = true
        scope.launch {
            try {
                val conversationId =
                    InboundStore.findOrCreateConversation(database.conversationDao(), trimmed)
                startActivity(
                    ThreadActivity.launchIntent(this@NewConversationActivity, conversationId)
                )
                finish()
            } catch (_: Throwable) {
                opening = false
            }
        }
    }

    /**
     * Paints this screen's chrome from the current effective theme (T6) —
     * the same applier path the launcher and the thread screen use.
     */
    private fun applyTheme() {
        val root = findViewById<View>(R.id.new_conversation_root)
        val title = findViewById<TextView>(R.id.new_conversation_title)
        ThemeApplier(themeController) { tokens ->
            root.setBackgroundColor(tokens.background.toInt())
            title.setTextColor(tokens.text.toInt())
            emptyState.setTextColor(tokens.muted.toInt())
        }.apply()
    }
}
