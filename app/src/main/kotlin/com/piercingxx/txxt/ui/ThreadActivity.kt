package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.service.SendPipeline
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

    private val database: TxxTDatabase by lazy { TxxTDatabase.build(this) }

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

        adapter = ThreadAdapter()
        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = adapter

        sendButton.setOnClickListener { sendComposed() }
        observeMessages()
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