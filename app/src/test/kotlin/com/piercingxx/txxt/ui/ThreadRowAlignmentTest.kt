package com.piercingxx.txxt.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Locks the thread's message alignment: outbound (the operator's own sent
 * messages) on the RIGHT, inbound (the sender) on the LEFT.
 *
 * **The bug this box exists for.** `ThreadMessagePresenter` mapped direction to
 * a [ThreadAlignment] correctly and had always been tested — but the binding
 * threw the answer away: it called `LinearLayout.setGravity` on the message
 * column, which positions that column's CHILDREN inside its own
 * `wrap_content` box (a box already exactly as wide as its widest child, so
 * nothing moved) and never touched the `layout_gravity` that decides which side
 * of the parent `FrameLayout` the column sits on. Every message rendered on the
 * same side while the presenter's mapping stayed green. So a presenter test
 * cannot cover this: the assertion has to reach the `LayoutParams` the parent
 * actually lays out from.
 *
 * Inflation is not JVM-testable without Robolectric (not in the offline cache),
 * so the binding is driven over mock views holding a REAL
 * [FrameLayout.LayoutParams] — the object the production code mutates — and the
 * gravity field is read back off it. The [ThreadWiringTest] adapter-seam
 * precedent (drive the real bind path, assert on what it produced).
 */
class ThreadRowAlignmentTest {

    private fun message(
        id: Long,
        direction: MessageDirection,
        body: String = "hello",
    ) = Message(
        id = id,
        conversationId = 1L,
        direction = direction,
        transport = MessageTransport.SMS,
        body = body,
        timestampMillis = id * 1_000L,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
    )

    /**
     * A stand-in for one inflated `item_message.xml` row: mock views wired to
     * the ids the binding looks up, with the message column carrying a real
     * [FrameLayout.LayoutParams] so `layout_gravity` writes are observable.
     */
    private class Row {
        val params = FrameLayout.LayoutParams(0, 0)
        val body = mockk<TextView>(relaxed = true)
        val timestamp = mockk<TextView>(relaxed = true)
        val container = mockk<LinearLayout>(relaxed = true)
        val itemView = mockk<View>(relaxed = true)

        init {
            every { container.layoutParams } returns params
            every { itemView.findViewById<TextView>(R.id.message_body) } returns body
            every { itemView.findViewById<TextView>(R.id.message_timestamp) } returns timestamp
            every { itemView.findViewById<LinearLayout>(R.id.message_row) } returns container
        }

        fun bind(row: ThreadRow) = ThreadAdapter.defaultBindRow(itemView, row)
    }

    // ---- The pure direction → side mapping ----

    @Test
    fun `inbound maps to START and outbound to END`() {
        assertEquals(
            "inbound (received) messages sit on the left",
            Gravity.START,
            ThreadAdapter.layoutGravityFor(ThreadAlignment.LEFT),
        )
        assertEquals(
            "outbound (sent) messages sit on the right",
            Gravity.END,
            ThreadAdapter.layoutGravityFor(ThreadAlignment.RIGHT),
        )
    }

    @Test
    fun `the two sides are actually different gravities`() {
        // The reported symptom was "every message on the same side"; a mapping
        // that collapsed both alignments onto one value would reproduce it
        // exactly, so the difference itself is worth pinning.
        assertNotEquals(
            ThreadAdapter.layoutGravityFor(ThreadAlignment.LEFT),
            ThreadAdapter.layoutGravityFor(ThreadAlignment.RIGHT),
        )
    }

    // ---- The binding writes layout_gravity, not just gravity ----

    @Test
    fun `binding an outbound row puts the column on the right`() {
        val row = Row()
        row.bind(ThreadMessagePresenter.present(message(1L, MessageDirection.OUTGOING)))

        assertEquals(
            "an outbound row must set layout_gravity END on the message column",
            Gravity.END,
            row.params.gravity,
        )
    }

    @Test
    fun `binding an inbound row puts the column on the left`() {
        val row = Row()
        row.bind(ThreadMessagePresenter.present(message(2L, MessageDirection.INCOMING)))

        assertEquals(
            "an inbound row must set layout_gravity START on the message column",
            Gravity.START,
            row.params.gravity,
        )
    }

    @Test
    fun `a recycled row does not keep the previous message's side`() {
        // RecyclerView hands a holder that just showed an outbound message
        // straight to an inbound one. An alignment applied only in the outbound
        // branch would leave this row wearing the wrong side — the classic
        // shape of this bug's second half.
        val row = Row()
        row.bind(ThreadMessagePresenter.present(message(1L, MessageDirection.OUTGOING)))
        assertEquals(Gravity.END, row.params.gravity)

        row.bind(ThreadMessagePresenter.present(message(2L, MessageDirection.INCOMING)))
        assertEquals(
            "a recycled row must be re-aligned for the message it now shows",
            Gravity.START,
            row.params.gravity,
        )

        // ...and back again, so neither direction is the privileged branch.
        row.bind(ThreadMessagePresenter.present(message(3L, MessageDirection.OUTGOING)))
        assertEquals(Gravity.END, row.params.gravity)
    }

    @Test
    fun `the whole adapter bind path reaches the alignment write`() {
        // End-to-end over the production binding: submit a message, run the
        // real onBindViewHolder (which goes through ThreadMessagePresenter and
        // ThreadAdapter.defaultBindRow), and read the side off the LayoutParams.
        // This is what fails if the presenter's answer is ever computed and
        // then dropped again.
        val row = Row()
        val adapter = ThreadAdapter()
        val holder = ThreadAdapter.RowHolder(row.itemView)

        adapter.submit(listOf(message(4L, MessageDirection.OUTGOING, body = "mine")))
        adapter.onBindViewHolder(holder, 0)

        assertEquals(
            "the adapter's own bind path must right-align an outbound message",
            Gravity.END,
            row.params.gravity,
        )
    }
}
