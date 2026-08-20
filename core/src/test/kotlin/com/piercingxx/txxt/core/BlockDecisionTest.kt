package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {

    private fun decision(
        keywords: Set<String> = emptySet(),
        phrases: Set<String> = emptySet(),
        knownContacts: Set<String> = emptySet(),
        starredContacts: Set<String> = emptySet(),
    ) = BlockDecision(
        filter = BlockingFilter(keywords = keywords, phrases = phrases),
        unknownSenderRule = UnknownSenderRule(knownContacts = knownContacts),
        starredBypass = StarredBypass(starredContacts = starredContacts),
    )

    // --- shouldBlock: unstarred senders keep the full posture ---

    @Test
    fun `unstarred sender is blocked when a keyword matches`() {
        val d = decision(keywords = setOf("loan"))
        assertTrue(d.shouldBlock("+1 555 2000", "Get a loan today"))
    }

    @Test
    fun `unstarred sender is blocked when a phrase matches`() {
        val d = decision(phrases = setOf("free money"))
        assertTrue(d.shouldBlock("+1 555 2000", "Get free money now"))
    }

    @Test
    fun `unstarred unknown sender is blocked`() {
        val d = decision(knownContacts = setOf("+1 555 1000"))
        assertTrue(d.shouldBlock("+1 555 2000", "Hello"))
    }

    @Test
    fun `unstarred known sender with no matching rule is not blocked`() {
        val d = decision(keywords = setOf("loan"), knownContacts = setOf("+1 555 1000"))
        assertFalse(d.shouldBlock("+1 555 1000", "Just checking in"))
    }

    // --- shouldBlock: starred contacts are never blocked ---

    @Test
    fun `starred contact is not blocked even when a keyword matches`() {
        val d = decision(keywords = setOf("loan"), starredContacts = setOf("+1 555 1000"))
        assertFalse(d.shouldBlock("+1 555 1000", "Get a loan today"))
    }

    @Test
    fun `starred contact is not blocked even when a phrase matches`() {
        val d = decision(phrases = setOf("free money"), starredContacts = setOf("+1 555 1000"))
        assertFalse(d.shouldBlock("+1 555 1000", "Get free money now"))
    }

    @Test
    fun `starred unknown sender is not blocked`() {
        val d = decision(knownContacts = setOf("+1 555 1000"), starredContacts = setOf("+1 555 2000"))
        assertFalse(d.shouldBlock("+1 555 2000", "Hello"))
    }

    // --- reason: no rule matches ---

    @Test
    fun `reason is null when no rule matches`() {
        val d = decision(keywords = setOf("loan"), knownContacts = setOf("+1 555 1000"))
        assertNull(d.reason("+1 555 1000", "Just checking in"))
    }

    // --- reason: unstarred sender surfaces the rule's reason ---

    @Test
    fun `reason is the matching term for an unstarred sender`() {
        val d = decision(keywords = setOf("loan"))
        assertEquals("Blocked term: loan", d.reason("+1 555 2000", "Get a loan today"))
    }

    @Test
    fun `reason is the unknown-sender rule for an unstarred unknown sender`() {
        val d = decision(knownContacts = setOf("+1 555 1000"))
        assertEquals("Sender is not a known contact", d.reason("+1 555 2000", "Hello"))
    }

    // --- reason: a rule matching a starred contact is surfaced, not applied ---

    @Test
    fun `reason is surfaced when a keyword would match a starred contact`() {
        val d = decision(keywords = setOf("loan"), starredContacts = setOf("+1 555 1000"))
        assertEquals(
            "Block would match starred contact: Blocked term: loan",
            d.reason("+1 555 1000", "Get a loan today"),
        )
    }

    @Test
    fun `reason is surfaced when a phrase would match a starred contact`() {
        val d = decision(phrases = setOf("free money"), starredContacts = setOf("+1 555 1000"))
        assertEquals(
            "Block would match starred contact: Blocked term: free money",
            d.reason("+1 555 1000", "Get free money now"),
        )
    }

    @Test
    fun `reason is surfaced when the unknown-sender rule would match a starred contact`() {
        val d = decision(knownContacts = setOf("+1 555 1000"), starredContacts = setOf("+1 555 2000"))
        assertEquals(
            "Block would match starred contact: Sender is not a known contact",
            d.reason("+1 555 2000", "Hello"),
        )
    }

    @Test
    fun `reason is deterministic across calls`() {
        val d = decision(keywords = setOf("loan"), starredContacts = setOf("+1 555 1000"))
        assertEquals(d.reason("+1 555 1000", "Get a loan today"), d.reason("+1 555 1000", "Get a loan today"))
    }
}