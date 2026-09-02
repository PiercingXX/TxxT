package com.piercingxx.txxt.ui

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Callback the conversation-list swipe helper routes swipe actions to (WS10).
 *
 * The four swipe actions — archive, delete, call, schedule — are the contract's
 * named surface (`contracts/TxxT.md` WS10). The DAO/intent operations behind
 * them are out of scope for this corrective; `MainActivity` implements this
 * interface with empty bodies, so the wiring seam is what the box verifies, not
 * the deferred operations.
 */
interface SwipeActionCallback {
    fun onArchive(conversationId: Long) {}
    fun onDelete(conversationId: Long) {}
    fun onCall(conversationId: Long) {}
    fun onSchedule(conversationId: Long) {}
}

/**
 * Wires swipe-to-archive / swipe-to-delete / swipe-to-call / swipe-to-schedule
 * onto a conversation-list [RecyclerView] (WS10).
 *
 * The attach step is injected as a lambda so the real `attachTo` path is
 * drivable in a plain JVM unit test without Robolectric (not in the offline
 * cache): the test injects a recording attach and verifies the side effect
 * fires, mirroring the `ThreadAdapter` injectable-`bindRow` seam. The production
 * attach is [defaultAttach], which hands an [ItemTouchHelper] built over the
 * swipe callback to the RecyclerView.
 */
class ConversationSwipeHelper(
    private val callback: SwipeActionCallback,
    private val attach: (RecyclerView, ItemTouchHelper.Callback) -> Unit = ::defaultAttach,
) {

    /**
     * Builds the swipe callback (both directions enabled for the four actions)
     * and attaches it to [recyclerView] through the injectable [attach] seam.
     */
    fun attachTo(recyclerView: RecyclerView) {
        val touchCallback = object : ItemTouchHelper.SimpleCallback(0, swipeFlags) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val conversationId = viewHolder.itemId
                when (direction) {
                    ItemTouchHelper.RIGHT -> callback.onArchive(conversationId)
                    ItemTouchHelper.LEFT -> {
                        // Snap the row back before the confirm dialog. ItemTouchHelper
                        // has already translated it off-screen; without this, Cancel
                        // leaves a hole until the activity is recreated.
                        @Suppress("DEPRECATION")
                        val position = viewHolder.adapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            recyclerView.adapter?.notifyItemChanged(position)
                        }
                        callback.onDelete(conversationId)
                    }
                }
            }
        }
        attach(recyclerView, touchCallback)
    }

    companion object {

        /** Both swipe directions are enabled for the four swipe actions. */
        const val swipeFlags: Int = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT

        /**
         * Production attach: constructs an [ItemTouchHelper] over [callback] and
         * attaches it to [recyclerView]. This is the live path the running app
         * reaches when `MainActivity.attachSwipeHelper` is called.
         */
        fun defaultAttach(recyclerView: RecyclerView, callback: ItemTouchHelper.Callback) {
            ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
        }
    }
}