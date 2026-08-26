package com.piercingxx.txxt.ui

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceCapitalizerTest {

    @Test
    fun `the first letter of the draft is capitalized`() {
        assertEquals("Hello", SentenceCapitalizer.capitalize("hello"))
        assertEquals("A", SentenceCapitalizer.capitalize("a"))
    }

    @Test
    fun `letters after sentence punctuation are capitalized`() {
        assertEquals("Hello. World", SentenceCapitalizer.capitalize("hello. world"))
        assertEquals("Wait! Why? Because", SentenceCapitalizer.capitalize("wait! why? because"))
        assertEquals("One.\nTwo", SentenceCapitalizer.capitalize("one.\ntwo"))
    }

    @Test
    fun `already-capital text is unchanged`() {
        assertEquals("Hello. World", SentenceCapitalizer.capitalize("Hello. World"))
    }

    @Test
    fun `empty and whitespace-only drafts are unchanged`() {
        assertEquals("", SentenceCapitalizer.capitalize(""))
        assertEquals("  ", SentenceCapitalizer.capitalize("  "))
    }

    @Test
    fun `the first letter after a leading emoji still capitalizes`() {
        assertEquals("😊 Hi", SentenceCapitalizer.capitalize("😊 hi"))
    }

    @Test
    fun `mid-sentence words stay lowercase`() {
        assertEquals("Hello there", SentenceCapitalizer.capitalize("hello there"))
    }

    @Test
    fun `compose inputType asks the IME for sentence capitals, not the SMS variation`() {
        val type = SentenceCapitalizer.inputType()
        assertTrue(
            "TYPE_TEXT_FLAG_CAP_SENTENCES must be set",
            type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
        )
        assertTrue(
            "TYPE_TEXT_FLAG_MULTI_LINE must stay so the compose field wraps",
            type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
        )
        assertEquals(
            "TYPE_TEXT_VARIATION_SHORT_MESSAGE makes IMEs ignore sentence caps",
            0,
            type and InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
        )
    }
}
