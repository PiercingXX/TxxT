package com.piercingxx.txxt.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R

/**
 * RecyclerView adapter for the NEW-conversation recipient picker.
 *
 * Fills `activity_new_conversation.xml`'s list with `item_recipient.xml`
 * rows produced by [RecipientPicker] — a text-first name + number line,
 * never a contact card (docs/DESIGN.md §"Conversation list").
 *
 * The row binding is injected as a lambda so the adapter's real
 * `onBindViewHolder` → bind path is drivable in a plain JVM unit test
 * without Robolectric (the [ConversationListAdapter] / [ThreadAdapter]
 * precedent). Production binding is [defaultBindRow].
 */
class RecipientAdapter(
    private val bindRow: (View, RecipientRow) -> Unit = ::defaultBindRow,
    /** Invoked when a row is tapped, so the picker can open that address. */
    private val onRecipientTap: (RecipientRow) -> Unit = {},
) : RecyclerView.Adapter<RecipientAdapter.RowHolder>() {

    private val rows = mutableListOf<RecipientRow>()

    // Attached-guard (ThreadAdapter precedent): the mockable android.jar does
    // not initialise RecyclerView.Adapter's observer list in a JVM unit test,
    // so notifyDataSetChanged() is only safe once attached to a real list.
    private var attached = false

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        attached = true
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        attached = false
    }

    /** Replaces the displayed rows with [next] and refreshes. */
    fun submit(next: List<RecipientRow>) {
        rows.clear()
        rows.addAll(next)
        if (attached) notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipient, parent, false)
        return RowHolder(itemView)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val row = rows[position]
        holder.itemView.setOnClickListener { onRecipientTap(row) }
        bindRow(holder.itemView, row)
    }

    class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {

        /**
         * Production row binding: a contact shows as name over number; a
         * typed-number row shows the number over "use this number".
         */
        fun defaultBindRow(itemView: View, row: RecipientRow) {
            val title = itemView.findViewById<TextView>(R.id.recipient_title)
            val subtitle = itemView.findViewById<TextView>(R.id.recipient_subtitle)
            when (row) {
                is RecipientRow.UseNumber -> {
                    title.text = row.number
                    subtitle.text = "use this number"
                }
                is RecipientRow.Contact -> {
                    title.text = row.entry.displayName
                    subtitle.text = row.entry.number
                }
            }
        }
    }
}
