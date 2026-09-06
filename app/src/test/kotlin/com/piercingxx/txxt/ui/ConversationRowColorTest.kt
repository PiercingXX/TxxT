package com.piercingxx.txxt.ui

import android.view.View
import android.widget.TextView
import com.piercingxx.txxt.R
import com.piercingxx.txxt.theme.ThemePreset
import com.piercingxx.txxt.theme.customGround
import com.piercingxx.txxt.theme.deriveTokens
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks inbox conversation-row colours to the theme tokens (P0: hardcoded
 * white on Paper/Mist).
 *
 * `item_conversation.xml` used to ship `#E6FFFFFF` / `#FFFFFFFF` / `#80FFFFFF`
 * literals, and [ConversationListAdapter.defaultBindRow] never overrode them
 * — so a light ground painted white-on-cream. The row now references
 * `?attr/txxtText` / `?attr/txxtMuted`, and the adapter binds
 * [ConversationListAdapter.titleColor] / [ConversationListAdapter.mutedColor]
 * from [com.piercingxx.txxt.theme.ThemeTokens] — the same mapping
 * [ThreadAdapter.emphasisColor] uses (SENT → `text`, RECEIVED → `muted`).
 */
class ConversationRowColorTest {

    private fun layoutText(name: String): String {
        val file = sequenceOf(
            File("src/main/res/layout/$name"),
            File("app/src/main/res/layout/$name"),
        ).first { it.exists() }
        return file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val itemConversation: String by lazy { layoutText("item_conversation.xml") }

    private fun row(
        title: String = "+15550001111",
        snippet: String = "hello",
        unread: Int = 0,
    ) = ConversationRow(
        conversationId = 1L,
        title = title,
        snippet = snippet,
        timestampMillis = 1_700_000_000_000L,
        unreadCount = unread,
    )

    // ---- layout: no hardcoded white, theme attributes instead ----

    @Test
    fun `conversation row layout has no hardcoded white literals`() {
        assertFalse(
            "item_conversation.xml must not hardcode #E6FFFFFF",
            itemConversation.contains("#E6FFFFFF"),
        )
        assertFalse(
            "item_conversation.xml must not hardcode #FFFFFFFF",
            itemConversation.contains("#FFFFFFFF"),
        )
        assertFalse(
            "item_conversation.xml must not hardcode #80FFFFFF",
            itemConversation.contains("#80FFFFFF"),
        )
        assertFalse(
            "item_conversation.xml must not hardcode #FFFFFF",
            itemConversation.contains("#FFFFFF"),
        )
    }

    @Test
    fun `conversation row text references theme attributes`() {
        assertTrue(
            "title must reference the txxtText theme attribute",
            itemConversation.contains("?attr/txxtText"),
        )
        assertTrue(
            "snippet/timestamp must reference the txxtMuted theme attribute",
            itemConversation.contains("?attr/txxtMuted"),
        )
    }

    // ---- the pure token mapping, shared with ThreadAdapter.emphasisColor ----

    @Test
    fun `title colour is the theme text token, muted colour is the muted token`() {
        val dark = deriveTokens(ThemePreset.AMOLED_NIGHT)
        assertEquals(dark.text, ConversationListAdapter.titleColor(dark))
        assertEquals(dark.muted, ConversationListAdapter.mutedColor(dark))
        assertEquals(
            ThreadAdapter.emphasisColor(ThreadEmphasis.SENT, dark),
            ConversationListAdapter.titleColor(dark),
        )
        assertEquals(
            ThreadAdapter.emphasisColor(ThreadEmphasis.RECEIVED, dark),
            ConversationListAdapter.mutedColor(dark),
        )
    }

    @Test
    fun `a light theme yields black-ramp row colours, not white`() {
        val paper = deriveTokens(ThemePreset.PAPER)
        val mist = deriveTokens(ThemePreset.MIST)
        assertEquals(paper.text, ConversationListAdapter.titleColor(paper))
        assertEquals(paper.muted, ConversationListAdapter.mutedColor(paper))
        assertEquals(mist.text, ConversationListAdapter.titleColor(mist))
        assertEquals(mist.muted, ConversationListAdapter.mutedColor(mist))
        assertFalse(
            "Paper text must not be the old hardcoded white",
            ConversationListAdapter.titleColor(paper) == 0xE6FFFFFFL,
        )
        assertFalse(
            "Paper muted must not be the old hardcoded white-50",
            ConversationListAdapter.mutedColor(paper) == 0x80FFFFFFL,
        )
    }

    @Test
    fun `a light custom ground yields black-ramp row colours`() {
        val light = deriveTokens(customGround(0xFFEEDDCCL))
        assertEquals(light.text, ConversationListAdapter.titleColor(light))
        assertEquals(light.muted, ConversationListAdapter.mutedColor(light))
        assertFalse(light.isDark)
    }

    // ---- the live bind path applies the theme-derived colour ----

    @Test
    fun `defaultBindRow paints title and snippet from tokens, not hardcoded white`() {
        val paper = deriveTokens(ThemePreset.PAPER)
        val title = mockk<TextView>(relaxed = true)
        val snippet = mockk<TextView>(relaxed = true)
        val timestamp = mockk<TextView>(relaxed = true)
        val unread = mockk<TextView>(relaxed = true)
        val itemView = mockk<View>(relaxed = true)
        every { itemView.findViewById<TextView>(R.id.conversation_title) } returns title
        every { itemView.findViewById<TextView>(R.id.conversation_snippet) } returns snippet
        every { itemView.findViewById<TextView>(R.id.conversation_timestamp) } returns timestamp
        every { itemView.findViewById<TextView>(R.id.conversation_unread) } returns unread

        ConversationListAdapter.defaultBindRow(itemView, row(unread = 2), paper)

        verify { title.setTextColor(paper.text.toInt()) }
        verify { unread.setTextColor(paper.text.toInt()) }
        verify { snippet.setTextColor(paper.muted.toInt()) }
        verify { timestamp.setTextColor(paper.muted.toInt()) }
    }

    @Test
    fun `onBindViewHolder passes the applied theme tokens into the bind seam`() {
        // ConversationListAdapter.setHasStableIds is not JVM-safe (mObservers
        // is null on the unit-test RecyclerView.Adapter), so the live bind
        // path is locked by reading the production call site: onBindViewHolder
        // must hand `theme` to bindRow, and applyTheme must store tokens.
        val source = sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/ui/ConversationListAdapter.kt"),
            File("app/src/main/kotlin/com/piercingxx/txxt/ui/ConversationListAdapter.kt"),
        ).first { it.exists() }.readText()
        assertTrue(
            "onBindViewHolder must bind with the stored theme tokens",
            source.contains("bindRow(holder.itemView, row, theme)"),
        )
        assertTrue(
            "applyTheme must store the tokens the bind path reads",
            source.contains("fun applyTheme(tokens: ThemeTokens)"),
        )
    }
}
