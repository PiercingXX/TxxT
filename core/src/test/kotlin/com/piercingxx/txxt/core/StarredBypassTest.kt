package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarredBypassTest {

    private fun bypass(
        starredContacts: Set<String> = emptySet(),
    ) = StarredBypass(starredContacts = starredContacts)

    // --- isStarred ---

    @Test
    fun `starred contact is recognised as starred`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertTrue(b.isStarred("+1 555 1000"))
    }

    @Test
    fun `unstarred sender is not starred`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertFalse(b.isStarred("+1 555 2000"))
    }

    @Test
    fun `no sender is starred when there are no starred contacts`() {
        val b = bypass()
        assertFalse(b.isStarred("+1 555 1000"))
        assertFalse(b.isStarred("alice@example.com"))
    }

    // --- bypasses (call-through) ---

    @Test
    fun `starred contact bypasses every suppression`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertTrue(b.bypasses("+1 555 1000"))
    }

    @Test
    fun `unstarred sender does not bypass suppressions`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertFalse(b.bypasses("+1 555 2000"))
    }

    @Test
    fun `bypasses agrees with isStarred`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertEquals(b.isStarred("+1 555 1000"), b.bypasses("+1 555 1000"))
        assertEquals(b.isStarred("+1 555 2000"), b.bypasses("+1 555 2000"))
    }

    // --- normalisation ---

    @Test
    fun `matching is case-insensitive`() {
        val b = bypass(starredContacts = setOf("ALICE@EXAMPLE.COM"))
        assertTrue(b.isStarred("alice@example.com"))
        assertTrue(b.bypasses("alice@example.com"))
    }

    @Test
    fun `matching trims surrounding whitespace`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertTrue(b.isStarred("  +1 555 1000  "))
        assertTrue(b.bypasses("  +1 555 1000  "))
    }

    @Test
    fun `starred contact with surrounding whitespace still matches`() {
        val b = bypass(starredContacts = setOf("  +1 555 1000  "))
        assertTrue(b.isStarred("+1 555 1000"))
    }

    // --- format tolerance (F5): the carrier delivering another formatting of
    // a starred number must not cost the contact their bypass ---

    @Test
    fun `formatting variance still matches a starred contact`() {
        val b = bypass(starredContacts = setOf("+15551234567"))
        assertTrue(b.isStarred("555-123-4567"))
        assertTrue(b.isStarred("(555) 123 4567"))
        assertTrue(b.bypasses("+1 555 1234567"))
    }

    // --- reason surfacing ---

    @Test
    fun `reason is present for a starred contact`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertEquals("Starred contacts bypass every suppression", b.reason("+1 555 1000"))
    }

    @Test
    fun `reason is null for an unstarred sender`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertNull(b.reason("+1 555 2000"))
    }

    @Test
    fun `reason is deterministic across calls`() {
        val b = bypass(starredContacts = setOf("+1 555 1000"))
        assertEquals(b.reason("+1 555 1000"), b.reason("+1 555 1000"))
    }
}