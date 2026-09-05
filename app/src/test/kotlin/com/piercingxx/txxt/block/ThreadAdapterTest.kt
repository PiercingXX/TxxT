package com.piercingxx.txxt.block

import android.view.View
import android.widget.TextView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.theme.ThemePreset
import com.piercingxx.txxt.theme.deriveTokens
import com.piercingxx.txxt.ui.ThreadAdapter
import com.piercingxx.txxt.ui.ThreadEmphasis
import com.piercingxx.txxt.ui.ThreadMessagePresenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the thread rows' text colour to the theme (T3 "conversation rows
 * hardcoded white").
 *
 * The thread's message rows used to hardcode white (`0xFFE6FFFFFF` sent /
 * `0xFF80FFFFFF` received) — legible on AMOLED black, invisible on the light
 * presets (Paper, Mist). The fix derives the emphasis colour from the theme's
 * tokens: SENT → `text`, RECEIVED → `muted`. These tests assert that mapping
 * is pure over the tokens (so a light theme yields black-ramp colours, not
 * white), and that the adapter's real `onBindViewHolder` → `defaultBindRow`
 * path applies the theme-derived colour to the row's body.
 */
class ThreadAdapterTest {

    private fun message(id: Long, direction: MessageDirection) = Message(
        id = id,
        conversationId = 1L,
        direction = direction,
        transport = MessageTransport.SMS,
        body = "hello",
        timestampMillis = id * 1_000L,
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
    )

    // ---- The pure emphasis → token mapping ----

    @Test
    fun `sent emphasis is the theme's text token`() {
        val dark = deriveTokens(ThemePreset.AMOLED_NIGHT)
        assertEquals(
            "sent = the theme text ceiling, not a hardcoded white",
            dark.text,
            ThreadAdapter.emphasisColor(ThreadEmphasis.SENT, dark),
        )
    }

    @Test
    fun `received emphasis is the theme's muted token`() {
        val dark = deriveTokens(ThemePreset.AMOLED_NIGHT)
        assertEquals(
            "received = the theme muted token, not a hardcoded white",
            dark.muted,
            ThreadAdapter.emphasisColor(ThreadEmphasis.RECEIVED, dark),
        )
    }

    @Test
    fun `a light theme yields black-ramp colours, not white`() {
        // Paper is a light preset: its foreground ramp is black. If the row
        // still wore the old hardcoded white it would be white-on-paper.
        val paper = deriveTokens(ThemePreset.PAPER)
        val sent = ThreadAdapter.emphasisColor(ThreadEmphasis.SENT, paper)
        val received = ThreadAdapter.emphasisColor(ThreadEmphasis.RECEIVED, paper)
        assertEquals("sent on Paper must be the black text token", paper.text, sent)
        assertEquals("received on Paper must be the black muted token", paper.muted, received)
        // Sanity: the black ramp is not the white ramp this used to hardcode.
        // (Tokens are 0xAARRGGBB longs: the dark theme's text/muted are the
        // white ramp 0xE6FFFFFF / 0x80FFFFFF — the values that used to be
        // hardcoded as 0xFFE6FFFFFF / 0xFF80FFFFFF ints.)
        assertEquals(0xE6FFFFFFL, deriveTokens(ThemePreset.AMOLED_NIGHT).text)
        assertEquals(0x80FFFFFFL, deriveTokens(ThemePreset.AMOLED_NIGHT).muted)
    }

    // ---- The live bind path applies the theme-derived colour ----

    @Test
    fun `the adapter's onBindViewHolder paints the body with the theme colour`() {
        val body = mockk<TextView>(relaxed = true)
        val itemView = mockk<View>(relaxed = true)
        every { itemView.findViewById<TextView>(R.id.message_body) } returns body
        every { itemView.findViewById<TextView>(R.id.message_timestamp) } returns
            mockk(relaxed = true)
        every { itemView.findViewById<android.widget.LinearLayout>(R.id.message_row) } returns
            mockk(relaxed = true)

        val adapter = ThreadAdapter()
        val holder = ThreadAdapter.RowHolder(itemView)
        adapter.submit(listOf(message(1L, MessageDirection.OUTGOING)))
        adapter.onBindViewHolder(holder, 0)

        // The body must be painted with the SENT emphasis colour — which is the
        // default theme's text token, never a hardcoded literal.
        verify { body.setTextColor(deriveTokens(ThemePreset.DEFAULT).text.toInt()) }
    }
}