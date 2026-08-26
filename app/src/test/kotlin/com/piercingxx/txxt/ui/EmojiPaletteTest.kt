package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPaletteTest {

    @Test
    fun `palette is nerd-font glyphs not colour emoji`() {
        assertTrue(EmojiPalette.glyphs.size >= 24)
        EmojiPalette.glyphs.forEach { glyph ->
            val cp = glyph.codePointAt(0)
            assertFalse(
                "U+$cp must not be a supplemental colour emoji",
                cp in 0x1F300..0x1FAFF,
            )
        }
        assertEquals("\uF118", EmojiPalette.PICKER_GLYPH)
        assertTrue(EmojiPalette.glyphs.contains(EmojiPalette.PICKER_GLYPH))
    }
}
