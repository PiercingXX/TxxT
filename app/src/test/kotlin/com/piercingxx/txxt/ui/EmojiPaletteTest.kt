package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPaletteTest {

    @Test
    fun `palette is unicode emoji that can go over SMS`() {
        assertTrue(EmojiPalette.glyphs.size >= 24)
        EmojiPalette.glyphs.forEach { glyph ->
            val cp = glyph.codePointAt(0)
            assertFalse(
                "U+${cp.toString(16)} must not be a Nerd Font private-use icon",
                cp in 0xE000..0xF8FF || cp in 0xF0000..0xFFFFD,
            )
        }
        assertEquals("😊", EmojiPalette.PICKER_GLYPH)
        assertTrue(EmojiPalette.glyphs.contains(EmojiPalette.PICKER_GLYPH))
    }
}
