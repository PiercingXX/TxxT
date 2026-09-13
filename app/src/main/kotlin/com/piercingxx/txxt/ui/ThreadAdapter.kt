package com.piercingxx.txxt.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.service.MmsRetrieve
import com.piercingxx.txxt.theme.ThemePreset
import com.piercingxx.txxt.theme.ThemeTokens
import com.piercingxx.txxt.theme.deriveTokens

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
    private val bindRow: (View, ThreadRow, ThemeTokens) -> Unit = ::defaultBindRow,
    /** Invoked when a message row is tapped, so the activity can read it aloud. */
    private val onMessageTap: (Message) -> Unit = {},
    /** Invoked on a long-press, so the activity can offer copy/delete. */
    private val onMessageLongPress: (Message) -> Unit = {},
    /** Long-press on a photo: fullscreen until the finger lifts. */
    private val onPhotoPeek: (Message) -> Unit = {},
    private val onPhotoUnpeek: () -> Unit = {},
    /** Double-tap on a photo: offer to save it. */
    private val onPhotoDoubleTap: (Message) -> Unit = {},
) : RecyclerView.Adapter<ThreadAdapter.RowHolder>() {

    private val messages = mutableListOf<Message>()

    /**
     * Photo ids revealed in this visit. Session-only: a tap adds, another
     * tap removes, [hideAllPhotos] clears the set when the thread leaves
     * the screen. The Room body stays `[Photo]` so a reopen starts hidden.
     */
    private val revealedPhotoIds = mutableSetOf<Long>()

    /**
     * The theme tokens the rows are painted from. Defaults to the brand's
     * default preset (AMOLED black) so the adapter stays constructible — and
     * JVM-testable — without a `Context`; the thread screen pushes the live
     * theme here via [applyTheme] so the message rows follow the chosen theme
     * instead of the hardcoded white they used to wear.
     */
    private var theme: ThemeTokens = deriveTokens(ThemePreset.DEFAULT)

    /**
     * Re-paints the rows from [tokens]: stores the new theme and refreshes the
     * list so every visible row re-binds through the presenter with the new
     * emphasis colours. The theme seam the thread screen's applier drives.
     */
    fun applyTheme(tokens: ThemeTokens) {
        theme = tokens
        if (attached) notifyDataSetChanged()
    }

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

    fun revealPhoto(messageId: Long) {
        if (!revealedPhotoIds.add(messageId)) return
        if (attached) notifyDataSetChanged()
    }

    fun collapsePhoto(messageId: Long) {
        if (!revealedPhotoIds.remove(messageId)) return
        if (attached) notifyDataSetChanged()
    }

    fun togglePhoto(messageId: Long) {
        if (!revealedPhotoIds.add(messageId)) revealedPhotoIds.remove(messageId)
        if (attached) notifyDataSetChanged()
    }

    /** Auto-hide: every revealed photo goes back to `[Photo]`. */
    fun hideAllPhotos() {
        if (revealedPhotoIds.isEmpty()) return
        revealedPhotoIds.clear()
        if (attached) notifyDataSetChanged()
    }

    fun isPhotoRevealed(messageId: Long): Boolean = messageId in revealedPhotoIds

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
        val row = ThreadMessagePresenter.present(
            message,
            revealed = message.id in revealedPhotoIds,
        )
        bindPhotoGestures(holder.itemView, message)
        bindRow(holder.itemView, row, theme)
    }

    private fun bindPhotoGestures(itemView: View, message: Message) {
        if (!isPhotoRow(message)) {
            itemView.setOnTouchListener(null)
            itemView.setOnClickListener { onMessageTap(message) }
            itemView.setOnLongClickListener {
                onMessageLongPress(message)
                true
            }
            return
        }
        itemView.setOnClickListener(null)
        itemView.setOnLongClickListener(null)
        val detector = GestureDetector(
            itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onMessageTap(message)
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onPhotoDoubleTap(message)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    itemView.parent?.requestDisallowInterceptTouchEvent(true)
                    onPhotoPeek(message)
                }
            },
        )
        itemView.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onPhotoUnpeek()
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            detector.onTouchEvent(ev)
            true
        }
    }

    class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {

        /**
         * Production row binding: applies a presenter-produced [ThreadRow] to an
         * inflated `item_message.xml` view — body and timestamp text, alignment
         * gravity (inbound START / outbound END), and emphasis text colour from
         * the white-opacity ramp (sent signal-white, received muted).
         *
         * **The alignment bug this closes.** This used to set
         * `rowContainer.gravity` and nothing else, so every message rendered on
         * the same side. `View.setGravity` on a `LinearLayout` positions that
         * layout's CHILDREN inside its own box; `message_row` is
         * `wrap_content`, so its box is already exactly as wide as its widest
         * child and the setting had nothing to move. What decides which SIDE of
         * the row the column sits on is `layout_gravity` — a property of the
         * child's `LayoutParams` in its parent (`item_message.xml`'s
         * `FrameLayout`), which only [applyAlignment] below writes.
         */
        fun defaultBindRow(
            itemView: View,
            row: ThreadRow,
            tokens: ThemeTokens = deriveTokens(ThemePreset.DEFAULT),
        ) {
            val body = itemView.findViewById<TextView>(R.id.message_body)
            val timestamp = itemView.findViewById<TextView>(R.id.message_timestamp)
            val rowContainer = itemView.findViewById<LinearLayout>(R.id.message_row)

            body.text = EmojiNerdFont.display(row.body)
            timestamp.text = formatTimestamp(row.timestampMillis)
            EmojiTypeface.apply(body)
            val photo = itemView.findViewById<View>(R.id.message_photo) as? ImageView
            val path = row.mediaPath
            val fileReady = row.showPhoto &&
                !path.isNullOrBlank() &&
                java.io.File(path).isFile
            val bitmap = if (fileReady) decodePhoto(path!!) else null
            if (photo != null) {
                if (bitmap != null) {
                    photo.visibility = View.VISIBLE
                    photo.setImageBitmap(bitmap)
                } else {
                    photo.visibility = View.GONE
                    photo.setImageDrawable(null)
                }
            }
            val hideMarker = bitmap != null && isPhotoMarker(row.body)
            body.visibility = if (hideMarker) View.GONE else View.VISIBLE
            applyAlignment(rowContainer, body, timestamp, row.alignment)
            body.setTextColor(emphasisColor(row.emphasis, tokens).toInt())
        }

        /**
         * The text colour a row's emphasis maps to, derived from the theme's
         * tokens — never a hardcoded white.
         *
         * Sent (the operator's own messages) takes the theme's `text` token
         * (the ceiling, 90% of the foreground); received takes `muted` (half
         * the foreground). On the brand's dark presets these are the white
         * ramp (`0xFFE6FFFFFF` / `0xFF80FFFFFF` — the exact values this used
         * to hardcode); on a light preset (Paper, Mist) they are the black
         * ramp, so a message row stays legible instead of wearing white-on-
         * white. Pure over its inputs — JVM-testable.
         */
        fun emphasisColor(emphasis: ThreadEmphasis, tokens: ThemeTokens): Long =
            when (emphasis) {
                ThreadEmphasis.SENT -> tokens.text
                ThreadEmphasis.RECEIVED -> tokens.muted
            }

        /**
         * Puts the message column on its side of the row: outbound (the
         * operator's own sent messages) right, inbound (the sender) left.
         *
         * Three writes, at three different scopes, all of them needed:
         *  1. **`layoutParams.gravity`** — WHERE THE COLUMN SITS in the parent
         *     `FrameLayout`. This is the one that actually moves the message,
         *     and it is what the old binding was missing. `LayoutParams` are
         *     read by the PARENT during layout, so the mutated object is
         *     re-assigned through the setter, which is what marks the view
         *     dirty (`requestLayout`); mutating the field alone can leave the
         *     row painted at its stale position until something else forces a
         *     pass.
         *  2. **`rowContainer.gravity`** — where the body/timestamp sit inside
         *     that column, so an outbound timestamp hugs the right edge of the
         *     text rather than its left.
         *  3. **`body.gravity` / `timestamp.gravity`** — where the wrapped
         *     lines of a multi-line body sit inside the TextView, whose
         *     `wrap_content` width is the longest line.
         *
         * **Every write happens on BOTH branches, unconditionally.** Rows are
         * recycled: a holder that last showed an outbound message is handed
         * straight to an inbound one, so an alignment applied only in the
         * outbound case would leave the inbound message wearing the previous
         * item's side. The `when` maps every [ThreadAlignment] to a gravity and
         * the same three writes run either way — there is no "leave it as it
         * was" path.
         */
        fun applyAlignment(
            rowContainer: LinearLayout,
            body: TextView,
            timestamp: TextView,
            alignment: ThreadAlignment,
        ) {
            val gravity = layoutGravityFor(alignment)

            // (1) The load-bearing one: the column's side of the row.
            val params = rowContainer.layoutParams as? FrameLayout.LayoutParams
            if (params != null && params.gravity != gravity) {
                params.gravity = gravity
                rowContainer.layoutParams = params
            }
            // (2) and (3): alignment WITHIN the column and within each line box.
            rowContainer.gravity = gravity
            body.gravity = gravity
            timestamp.gravity = gravity
        }

        /**
         * The `layout_gravity` value for a row alignment. START/END rather than
         * LEFT/RIGHT so the thread mirrors correctly under an RTL locale (the
         * manifest sets `supportsRtl`); pure over its input, so the
         * direction → side mapping is JVM-testable without inflating a view.
         */
        fun isPhotoRow(message: Message): Boolean {
            if (!message.mediaPath.isNullOrBlank()) return true
            if (message.transport != MessageTransport.MMS) return false
            val trimmed = message.body.trim()
            return trimmed == MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER ||
                trimmed == MmsRetrievedContent.PHOTO_PLACEHOLDER ||
                MmsRetrieve.needsRetrieve(message)
        }

        fun isPhotoMarker(body: String): Boolean {
            val trimmed = body.trim()
            return trimmed == MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER ||
                trimmed == MmsRetrievedContent.PHOTO_PLACEHOLDER
        }

        fun layoutGravityFor(alignment: ThreadAlignment): Int = when (alignment) {
            ThreadAlignment.LEFT -> Gravity.START
            ThreadAlignment.RIGHT -> Gravity.END
        }

        /** JPEG via BitmapFactory; HEIC/HEIF via ImageDecoder on API 28+. */
        fun decodePhoto(path: String): Bitmap? {
            BitmapFactory.decodeFile(path)?.let { return it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.io.File(path)))
                        ?.let { return it }
                } catch (_: Exception) {
                }
            }
            val lower = path.lowercase()
            if (lower.endsWith(".3gp") || lower.endsWith(".mp4") || lower.endsWith(".video")) {
                return try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val frame = retriever.frameAtTime
                    retriever.release()
                    frame
                } catch (_: Exception) {
                    null
                }
            }
            return null
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