package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingFilterTest {

    private fun filter(
        keywords: Set<String> = emptySet(),
        phrases: Set<String> = emptySet(),
    ) = BlockingFilter(keywords = keywords, phrases = phrases)

    // --- keyword matching ---

    @Test
    fun `keyword matches when present as a whole word`() {
        val f = filter(keywords = setOf("loan"))
        assertTrue(f.matches("Get a loan today"))
        assertTrue(f.matches("loan"))
    }

    @Test
    fun `keyword does not match a prefix or suffix of a longer word`() {
        val f = filter(keywords = setOf("loan"))
        assertFalse(f.matches("Get loans today"))
        assertFalse(f.matches("I am a loaner"))
        assertFalse(f.matches("unloan"))
    }

    @Test
    fun `keyword matching is case-insensitive`() {
        val f = filter(keywords = setOf("LOAN"))
        assertTrue(f.matches("get a Loan today"))
        assertTrue(f.matches("LOAN"))
    }

    @Test
    fun `keyword matches at word boundaries with punctuation`() {
        val f = filter(keywords = setOf("loan"))
        assertTrue(f.matches("Get a loan!"))
        assertTrue(f.matches("(loan) today"))
        assertTrue(f.matches("loan, really"))
    }

    @Test
    fun `keyword matches when surrounded by non-alphanumeric characters`() {
        val f = filter(keywords = setOf("win"))
        assertTrue(f.matches("You WIN a prize"))
        assertTrue(f.matches("WIN!!!"))
    }

    @Test
    fun `no match when no keyword is present`() {
        val f = filter(keywords = setOf("loan", "prize"))
        assertFalse(f.matches("Hello, how are you?"))
    }

    // --- phrase matching ---

    @Test
    fun `phrase matches as a substring`() {
        val f = filter(phrases = setOf("free money"))
        assertTrue(f.matches("Get free money now"))
        assertTrue(f.matches("free money"))
    }

    @Test
    fun `phrase matching is case-insensitive`() {
        val f = filter(phrases = setOf("free money"))
        assertTrue(f.matches("GET FREE MONEY NOW"))
    }

    @Test
    fun `phrase does not match when absent`() {
        val f = filter(phrases = setOf("free money"))
        assertFalse(f.matches("Get money now"))
        assertFalse(f.matches("free"))
    }

    // --- combined keyword and phrase ---

    @Test
    fun `matches when either a keyword or a phrase hits`() {
        val f = filter(keywords = setOf("loan"), phrases = setOf("free money"))
        assertTrue(f.matches("Get a loan"))
        assertTrue(f.matches("free money today"))
    }

    @Test
    fun `no match when neither keyword nor phrase hits`() {
        val f = filter(keywords = setOf("loan"), phrases = setOf("free money"))
        assertFalse(f.matches("Just checking in"))
    }

    // --- matchedTerm (reason surfacing) ---

    @Test
    fun `matchedTerm returns the matching keyword`() {
        val f = filter(keywords = setOf("loan"))
        assertEquals("loan", f.matchedTerm("Get a loan today"))
    }

    @Test
    fun `matchedTerm returns the matching phrase`() {
        val f = filter(phrases = setOf("free money"))
        assertEquals("free money", f.matchedTerm("Get free money now"))
    }

    @Test
    fun `matchedTerm prefers a keyword over a phrase when both match`() {
        val f = filter(keywords = setOf("loan"), phrases = setOf("loan"))
        assertEquals("loan", f.matchedTerm("Get a loan"))
    }

    @Test
    fun `matchedTerm returns null when nothing matches`() {
        val f = filter(keywords = setOf("loan"), phrases = setOf("free money"))
        assertNull(f.matchedTerm("Just checking in"))
    }

    // --- empty filter ---

    @Test
    fun `empty filter matches nothing`() {
        val f = filter()
        assertFalse(f.matches("Get free money loan offer"))
        assertNull(f.matchedTerm("Get free money loan offer"))
    }
}