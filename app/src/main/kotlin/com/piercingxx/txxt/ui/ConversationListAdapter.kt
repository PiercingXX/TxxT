package com.piercingxx.txxt.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.theme.ThemePreset
import com.piercingxx.txxt.theme.ThemeTokens
import com.piercingxx.txxt.theme.deriveTokens

/**
 * RecyclerView adapter for the launcher's conversation list (WS10).
 *
 * Fills `activity_main.xml`'s `recyclerView` with `item_conversation.xml`
 * rows. Each row is produced by [ConversationListPresenter] — title, one-line
 * snippet, latest-activity timestamp, unread badge — as a text-first line on
 * the effective theme's ground (docs/DESIGN.md §"Conversation list").
 *
 * **Stable ids are load-bearing:** [ConversationSwipeHelper] reads
 * `viewHolder.itemId` as the conversation id when a row is swiped, so
 * [getItemId] MUST return the conversation id (`setHasStableIds(true)` in
 * `init`). Without it every swipe would archive/delete `NO_ID`.
 *
 * The row binding is injected as a lambda so the adapter's real
 * `onBindViewHolder` → `ConversationListPresenter.present` → bind path is
 * drivable in a plain JVM unit test without Robolectric (the [ThreadAdapter]
 * precedent). The production binding is [defaultBindRow], which applies the
 * presenter's view state to an inflated `item_conversation.xml` and paints
 * title/unread from `tokens.text` and snippet/timestamp from `tokens.muted`
 * — the same mapping [ThreadAdapter.emphasisColor] uses, never a hardcoded
 * white.
 */
class ConversationListAdapter(
    private val bindRow: (View, ConversationRow, ThemeTokens) -> Unit = ::defaultBindRow,
    /** Invoked when a row is tapped, so the launcher can open its thread. */
    private val onConversationTap: (ConversationRow) -> Unit = {},
    /** Invoked on a long-press, so the launcher can offer pin/archive. */
    private val onConversationLongPress: (ConversationRow) -> Unit = {},
    /**
     * Resolves a participant address to the name saved for it in the system
     * contacts provider, falling back to the address itself
     * (`contacts/ContactNameResolver.labelFor`). Defaults to identity so the
     * adapter stays constructible — and JVM-testable — without a `Context`.
     *
     * **Applied at [submit] time, not at bind time.** Rows are built once per
     * Room emission and reused across binds, so a scroll never touches the
     * contacts provider at all; combined with the resolver's own LRU, a full
     * list costs at most one provider query per distinct address, ever.
     */
    private val displayName: (String) -> String = { it },
) : RecyclerView.Adapter<ConversationListAdapter.RowHolder>() {

    private val rows = mutableListOf<ConversationRow>()

    /**
     * The theme tokens the rows are painted from. Defaults to the brand's
     * default preset (AMOLED black) so the adapter stays constructible — and
     * JVM-testable — without a `Context`; the launcher pushes the live theme
     * here via [applyTheme] so the inbox rows follow the chosen theme instead
     * of the hardcoded white they used to wear.
     */
    private var theme: ThemeTokens = deriveTokens(ThemePreset.DEFAULT)

    /**
     * Re-paints the rows from [tokens]: stores the new theme and refreshes the
     * list so every visible row re-binds with the new text/muted colours.
     */
    fun applyTheme(tokens: ThemeTokens) {
        theme = tokens
        if (attached) notifyDataSetChanged()
    }

    // Attached-guard (ThreadAdapter precedent): the mockable android.jar does
    // not initialise RecyclerView.Adapter's observer list in a JVM unit test,
    // so notifyDataSetChanged() is only safe once attached to a real list.
    private var attached = false

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        attached = true
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        attached = false
    }

    /** Replaces the displayed conversations with [conversations] and refreshes. */
    fun submit(conversations: List<Conversation>) {
        rows.clear()
        conversations.mapTo(rows) { ConversationListPresenter.present(it, displayName) }
        if (attached) notifyDataSetChanged()
    }

    /** The conversation id — what [ConversationSwipeHelper] reads on a swipe. */
    override fun getItemId(position: Int): Long = rows[position].conversationId

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return RowHolder(itemView)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val row = rows[position]
        holder.itemView.setOnClickListener { onConversationTap(row) }
        holder.itemView.setOnLongClickListener {
            onConversationLongPress(row)
            true
        }
        bindRow(holder.itemView, row, theme)
    }

    class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {

        /**
         * Production row binding: applies a presenter-produced
         * [ConversationRow] to an inflated `item_conversation.xml` — title,
         * snippet, formatted timestamp, and the unread badge (hidden at zero,
         * `"n new"` otherwise). Colours come from [tokens], never a hardcoded
         * white: title and unread take `text`, snippet and timestamp take
         * `muted` — the same mapping [ThreadAdapter.emphasisColor] uses.
         */
        fun defaultBindRow(
            itemView: View,
            row: ConversationRow,
            tokens: ThemeTokens = deriveTokens(ThemePreset.DEFAULT),
        ) {
            val title = itemView.findViewById<TextView>(R.id.conversation_title)
            val snippet = itemView.findViewById<TextView>(R.id.conversation_snippet)
            val timestamp = itemView.findViewById<TextView>(R.id.conversation_timestamp)
            val unread = itemView.findViewById<TextView>(R.id.conversation_unread)
            title.text = row.title
            title.setTextColor(titleColor(tokens).toInt())
            snippet.text = EmojiNerdFont.display(row.snippet)
            snippet.setTextColor(mutedColor(tokens).toInt())
            timestamp.text =
                row.timestampMillis?.let { formatTimestamp(System.currentTimeMillis(), it) }.orEmpty()
            timestamp.setTextColor(mutedColor(tokens).toInt())
            unread.setTextColor(titleColor(tokens).toInt())
            if (row.unreadCount > 0) {
                unread.visibility = View.VISIBLE
                unread.text = unreadLabel(row.unreadCount)
            } else {
                unread.visibility = View.GONE
            }
        }

        /**
         * Title / unread colour: the theme's `text` token (SENT emphasis).
         * Pure over its input — JVM-testable.
         */
        fun titleColor(tokens: ThemeTokens): Long = tokens.text

        /**
         * Snippet / timestamp colour: the theme's `muted` token (RECEIVED
         * emphasis). Pure over its input — JVM-testable.
         */
        fun mutedColor(tokens: ThemeTokens): Long = tokens.muted

        /** The unread badge's text — text-first, no icon (`"3 new"`). */
        fun unreadLabel(count: Int): String = "$count new"

        // One shared formatter per pattern (ThreadAdapter precedent):
        // SimpleDateFormat is not thread-safe, but binding is main-thread-only.
        private val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
        private val dayFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.ROOT)

        /**
         * Formats a row timestamp relative to [nowMillis]: today's activity
         * shows the clock time (`HH:mm`), older activity the calendar day
         * (`MMM d`). Pure over its two inputs — JVM-testable.
         */
        fun formatTimestamp(nowMillis: Long, epochMillis: Long): String {
            val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
            val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
            return if (sameDay) {
                timeFormat.format(java.util.Date(epochMillis))
            } else {
                dayFormat.format(java.util.Date(epochMillis))
            }
        }
    }
}
