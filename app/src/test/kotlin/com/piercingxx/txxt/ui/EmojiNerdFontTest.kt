package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiNerdFontTest {

    @Test
    fun `colour smiles become the nerd-font smile`() {
        assertEquals("\uF118", EmojiNerdFont.display("😊"))
        assertEquals("hello \uF118", EmojiNerdFont.display("hello 😊"))
    }

    @Test
    fun `hearts become the nerd-font heart and keep surrounding text`() {
        assertEquals("\uF004", EmojiNerdFont.display("❤"))
        assertEquals("\uF004", EmojiNerdFont.display("❤️"))
        assertEquals("I \uF004 you", EmojiNerdFont.display("I ❤️ you"))
    }

    @Test
    fun `skin-tone thumbs-up still maps to fa-thumbs-up`() {
        assertEquals("\uF164", EmojiNerdFont.display("👍"))
        assertEquals("\uF164", EmojiNerdFont.display("👍🏽"))
    }

    @Test
    fun `unmapped colour emoji still become a nerd glyph, never left as-is`() {
        val out = EmojiNerdFont.display("🫠") // melting face, U+1FAE0
        assertEquals(EmojiNerdFont.FALLBACK, out)
        assertFalse(out.contains("🫠"))
    }

    @Test
    fun `plain ASCII is untouched`() {
        assertEquals("hello 12", EmojiNerdFont.display("hello 12"))
    }

    @Test
    fun `glyph spans keep original offsets so compose can paint without rewriting send text`() {
        val text = "hi 😊"
        val spans = EmojiNerdFont.glyphSpans(text)
        assertEquals(1, spans.size)
        val smileStart = text.indexOf("😊")
        assertEquals(smileStart, spans[0].start)
        assertEquals(smileStart + "😊".length, spans[0].end)
        assertEquals("\uF118", spans[0].glyph)
        assertEquals("😊", text.substring(spans[0].start, spans[0].end))
    }

    @Test
    fun `display glyphs are not supplemental colour emoji`() {
        val painted = listOf("😊", "😂", "❤️", "👍", "🔥", "🙏").map { EmojiNerdFont.display(it) }
        painted.forEach { glyph ->
            val cp = glyph.codePointAt(0)
            assertFalse(
                "U+${cp.toString(16)} must not be a supplemental colour emoji",
                cp in 0x1F300..0x1FAFF,
            )
            assertTrue(glyph.isNotEmpty())
        }
    }
}
