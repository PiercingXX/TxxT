package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownSenderRuleTest {

    private fun rule(
        knownContacts: Set<String> = emptySet(),
    ) = UnknownSenderRule(knownContacts = knownContacts)

    // --- unknown detection ---

    @Test
    fun `sender not in known contacts is unknown`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertTrue(r.isUnknown("+1 555 2000"))
    }

    @Test
    fun `sender in known contacts is not unknown`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertFalse(r.isUnknown("+1 555 1000"))
    }

    @Test
    fun `every sender is unknown when there are no known contacts`() {
        val r = rule()
        assertTrue(r.isUnknown("+1 555 1000"))
        assertTrue(r.isUnknown("someone@example.com"))
    }

    // --- isKnown ---

    @Test
    fun `isKnown true for a known contact`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertTrue(r.isKnown("+1 555 1000"))
    }

    @Test
    fun `isKnown false for an unknown sender`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertFalse(r.isKnown("+1 555 2000"))
    }

    // --- normalisation ---

    @Test
    fun `matching is case-insensitive`() {
        val r = rule(knownContacts = setOf("ALICE@EXAMPLE.COM"))
        assertFalse(r.isUnknown("alice@example.com"))
        assertTrue(r.isKnown("alice@example.com"))
    }

    @Test
    fun `matching trims surrounding whitespace`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertFalse(r.isUnknown("  +1 555 1000  "))
        assertTrue(r.isKnown("  +1 555 1000  "))
    }

    @Test
    fun `known contact with surrounding whitespace still matches`() {
        val r = rule(knownContacts = setOf("  +1 555 1000  "))
        assertFalse(r.isUnknown("+1 555 1000"))
    }

    // --- format tolerance (F5): formatting variance cannot make a known
    // contact look unknown ---

    @Test
    fun `formatting variance still matches a known contact`() {
        val r = rule(knownContacts = setOf("+15551234567"))
        assertFalse(r.isUnknown("555-123-4567"))
        assertFalse(r.isUnknown("(555) 123 4567"))
        assertFalse(r.isUnknown("+1 555 1234567"))
        assertTrue(r.isKnown("+1 (555) 123-4567"))
    }

    // --- reason surfacing ---

    @Test
    fun `reason is present for an unknown sender`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertEquals("Sender is not a known contact", r.reason("+1 555 2000"))
    }

    @Test
    fun `reason is null for a known sender`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertNull(r.reason("+1 555 1000"))
    }

    @Test
    fun `reason is deterministic across calls`() {
        val r = rule(knownContacts = setOf("+1 555 1000"))
        assertEquals(r.reason("+1 555 2000"), r.reason("+1 555 2000"))
    }
}