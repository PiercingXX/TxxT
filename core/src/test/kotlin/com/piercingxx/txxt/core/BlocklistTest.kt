package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {

    private fun list(
        keywords: Set<String> = emptySet(),
        phrases: Set<String> = emptySet(),
    ) = Blocklist(keywords = keywords, phrases = phrases)

    // --- add / membership ---

    @Test
    fun `added keyword is blocked`() {
        val l = list().addKeyword("loan")
        assertTrue(l.isBlocked("loan"))
    }

    @Test
    fun `added phrase is blocked`() {
        val l = list().addPhrase("free money")
        assertTrue(l.isBlocked("free money"))
    }

    @Test
    fun `a never-added rule is not blocked`() {
        val l = list()
        assertFalse(l.isBlocked("loan"))
        assertFalse(l.isBlocked("free money"))
    }

    @Test
    fun `keyword and phrase rules are tracked independently`() {
        val l = list().addKeyword("loan").addPhrase("free money")
        assertTrue(l.isBlocked("loan"))
        assertTrue(l.isBlocked("free money"))
    }

    @Test
    fun `adding the same rule twice is idempotent`() {
        val l = list().addKeyword("loan").addKeyword("loan")
        assertTrue(l.isBlocked("loan"))
        assertEquals(setOf("loan"), l.keywords())
    }

    // --- remove ---

    @Test
    fun `adding then removing a keyword leaves it unblocked`() {
        val l = list().addKeyword("loan").removeKeyword("loan")
        assertFalse(l.isBlocked("loan"))
    }

    @Test
    fun `adding then removing a phrase leaves it unblocked`() {
        val l = list().addPhrase("free money").removePhrase("free money")
        assertFalse(l.isBlocked("free money"))
    }

    @Test
    fun `removing a never-added rule is a no-op`() {
        val l = list().removeKeyword("loan").removePhrase("free money")
        assertFalse(l.isBlocked("loan"))
        assertFalse(l.isBlocked("free money"))
    }

    @Test
    fun `removing a keyword does not affect other rules`() {
        val l = list().addKeyword("loan").addKeyword("prize").removeKeyword("loan")
        assertFalse(l.isBlocked("loan"))
        assertTrue(l.isBlocked("prize"))
    }

    // --- listing ---

    @Test
    fun `rule list reflects the adds`() {
        val l = list().addKeyword("loan").addKeyword("prize").addPhrase("free money")
        assertEquals(setOf("loan", "prize"), l.keywords())
        assertEquals(setOf("free money"), l.phrases())
    }

    @Test
    fun `rule list reflects the removes`() {
        val l = list().addKeyword("loan").addKeyword("prize").removeKeyword("loan")
        assertEquals(setOf("prize"), l.keywords())
    }

    @Test
    fun `empty list has no rules`() {
        val l = list()
        assertEquals(emptySet<String>(), l.keywords())
        assertEquals(emptySet<String>(), l.phrases())
    }

    @Test
    fun `initial rules are present from construction`() {
        val l = list(keywords = setOf("loan"), phrases = setOf("free money"))
        assertTrue(l.isBlocked("loan"))
        assertTrue(l.isBlocked("free money"))
    }

    // --- snapshot isolation ---

    @Test
    fun `listed rules are a snapshot, not a live view`() {
        val l = list().addKeyword("loan")
        val snapshot = l.keywords()
        l.addKeyword("prize")
        assertEquals(setOf("loan"), snapshot)
    }
}