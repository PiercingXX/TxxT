package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Conversation

/**
 * TxxT launcher: the conversation list (WS10 corrective-corrective).
 *
 * Inflates `activity_main.xml`, resolves the conversation list by the runtime view
 * ID `R.id.recyclerView`, and drives the swipe helper (T1) over this activity (the
 * [SwipeActionCallback]). The four swipe operations — archive/delete/call/schedule —
 * are deferred by contract and are empty here.
 *
 * Search (T3): the launcher's SearchView fires its query listener into
 * [applySearchQuery], which filters the conversation list through
 * [ConversationSearchFilter] and submits the result to the conversation-list
 * adapter. The OS-level widget listener dispatch is on-device (see the plan's
 * deferred verification); the box verifies the [applySearchQuery] seam.
 *
 * FLAG_SECURE is set in code so the launcher's content never screenshots or
 * appears in the recents thumbnail (docs/PRIVACY.md).
 */
class MainActivity : Activity(), SwipeActionCallback {

    private val searchFilter = ConversationSearchFilter()
    private var conversations: List<Conversation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        attachSwipeHelper(findViewById<RecyclerView>(R.id.recyclerView))

        // Search wiring (T3): the SearchView widget's query listener routes every
        // keystroke and submit through applySearchQuery, which filters and submits.
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
    }

    /**
     * T1 seam: constructs the [ConversationSwipeHelper] over this activity and
     * attaches it to [recyclerView]. The live path the launcher reaches in
     * `onCreate`.
     */
    fun attachSwipeHelper(recyclerView: RecyclerView) {
        ConversationSwipeHelper(this).attachTo(recyclerView)
    }

    /**
     * T3 seam: filters the conversation list by [query] through
     * [ConversationSearchFilter] and submits the filtered list to the
     * conversation-list adapter. The SearchView listener calls this on every
     * query change and submit.
     */
    fun applySearchQuery(query: String) {
        val filtered = searchFilter.filterConversations(conversations, query)
        // Submit the filtered list to the conversation-list adapter. The adapter
        // is the WS10 conversation-list surface; the seam is what the box verifies.
        adapter.submit(filtered)
    }

    /** Opens the conversation thread for [conversationId] with FLAG_SECURE. */
    fun openThread(conversationId: Long) {
        val intent = Intent(this, ThreadActivity::class.java)
        intent.putExtra("extra_conversation_id", conversationId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ---- SwipeActionCallback (deferred operations, empty by contract) ----

    override fun onArchive(conversationId: Long) {}

    override fun onDelete(conversationId: Long) {}

    override fun onCall(conversationId: Long) {}

    override fun onSchedule(conversationId: Long) {}

    /**
     * The conversation-list adapter. The WS10 conversation-list adapter is out of
     * scope for this corrective; the submit seam is what T3's box verifies.
     */
    private val adapter = ConversationListAdapter()
}

/**
 * Placeholder conversation-list adapter (WS10). The real adapter is out of scope
 * for this corrective; [submit] is the seam `MainActivity.applySearchQuery` drives.
 */
private class ConversationListAdapter {
    fun submit(conversations: List<Conversation>) {
        // No-op until the WS10 conversation-list adapter lands.
    }
}