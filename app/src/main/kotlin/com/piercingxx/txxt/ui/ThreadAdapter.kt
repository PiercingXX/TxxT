package com.piercingxx.txxt.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Message

/**
 * RecyclerView adapter for the conversation thread (T3).
 *
 * Fills `activity_thread.xml`'s `message_list` (a RecyclerView) with
 * `item_message.xml` rows. Each row is produced by [ThreadMessagePresenter] —
 * direction-driven alignment (inbound left / outbound right) and emphasis
 * (sent inverted / received muted) — so the no-bubble, text-first constraint
 * holds on the live path (docs/PRIVACY.md §2).
 *
 * The row binding is injected as a lambda so the adapter's real
 * `onBindViewHolder` → `ThreadMessagePresenter.present` → bind path is drivable
 * in a plain JVM unit test without Robolectric (not in the offline cache). The
 * production binding is [defaultBindRow], which inflates `item_message.xml` and
 * applies the presenter's view state.
 */
class ThreadAdapter(
    private val bindRow: (View, ThreadRow) -> Unit = ::defaultBindRow,
    /** Invoked when a message row is tapped, so the activity can read it aloud. */
    private val onMessageTap: (Message) -> Unit = {},
    /** Invoked on a long-press, so the activity can offer copy/delete. */
    private val onMessageLongPress: (Message) -> Unit = {},
) : RecyclerView.Adapter<ThreadAdapter.RowHolder>() {

    private val messages = mutableListOf<Message>()

    // Tracks whether the adapter is attached to a live RecyclerView. The
    // mockable android.jar does not initialise RecyclerView.Adapter's observer
    // list in a JVM unit test, so notifyDataSetChanged() is only safe once the
    // adapter is attached — which is exactly when a real list needs refreshing.
    private var attached = false

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        attached = true
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        attached = false
    }

    /** Replaces the displayed messages with [msgs] and refreshes the list. */
    fun submit(msgs: List<Message>) {
        messages.clear()
        messages.addAll(msgs)
        if (attached) notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return RowHolder(itemView)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val message = messages[position]
        // The wire-in: every rendered row goes through the presenter, so the
        // direction → alignment/emphasis mapping is the single source of truth.
        val row = ThreadMessagePresenter.present(message)
        holder.itemView.setOnClickListener { onMessageTap(message) }
        holder.itemView.setOnLongClickListener {
            onMessageLongPress(message)
            true
        }
        bindRow(holder.itemView, row)
    }

    class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {

        /**
         * Production row binding: applies a presenter-produced [ThreadRow] to an
         * inflated `item_message.xml` view — body and timestamp text, alignment
         * gravity (inbound START / outbound END), and emphasis text colour from
         * the white-opacity ramp (sent signal-white, received muted).
         */
        fun defaultBindRow(itemView: View, row: ThreadRow) {
            val body = itemView.findViewById<TextView>(R.id.message_body)
            val timestamp = itemView.findViewById<TextView>(R.id.message_timestamp)
            val rowContainer = itemView.findViewById<LinearLayout>(R.id.message_row)

            body.text = row.body
            timestamp.text = formatTimestamp(row.timestampMillis)
            rowContainer.gravity = when (row.alignment) {
                ThreadAlignment.LEFT -> Gravity.START
                ThreadAlignment.RIGHT -> Gravity.END
            }
            body.setTextColor(when (row.emphasis) {
                // Sent = signal-white inverted emphasis; received = muted slate.
                ThreadEmphasis.SENT -> 0xFFE6FFFFFF.toInt()
                ThreadEmphasis.RECEIVED -> 0xFF80FFFFFF.toInt()
            })
        }

        /**
         * Shared row timestamp formatter. Hoisted out of [defaultBindRow] so
         * rows stop constructing a new SimpleDateFormat per bind. SimpleDateFormat
         * is not thread-safe, but binding is confined to the main thread, so one
         * shared instance is safe.
         */
        private val timestampFormat =
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)

        /**
         * Formats an epoch-millisecond timestamp for the row. Kept as a small
         * pure function so the row's display logic stays JVM-testable.
         */
        fun formatTimestamp(epochMillis: Long): String =
            timestampFormat.format(java.util.Date(epochMillis))
    }
}