package com.piercingxx.txxt.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundFilterTest {

    private fun filter(
        knownContacts: Set<String> = emptySet(),
        blockedAddresses: Set<String> = emptySet(),
        starredContacts: Set<String> = emptySet(),
        contentKeywords: Set<String> = emptySet(),
        contentPhrases: Set<String> = emptySet(),
        quarantineUnknownSenders: Boolean = false,
    ) = InboundFilter(
        knownContacts = knownContacts,
        blockedAddresses = blockedAddresses,
        starredContacts = starredContacts,
        contentKeywords = contentKeywords,
        contentPhrases = contentPhrases,
        quarantineUnknownSenders = quarantineUnknownSenders,
    )

    // ---- DELIVER: known sender, no rules match ----

    @Test
    fun `known sender with clean message is delivered`() {
        val f = filter(knownContacts = setOf("+1 555 1000"))
        val (disposition, reason) = f.evaluate("+1 555 1000", "Hey there")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNull(reason)
    }

    @Test
    fun `known sender with no rules is delivered`() {
        val f = filter(knownContacts = setOf("+1 555 1000"))
        val (disposition, _) = f.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    // ---- QUARANTINE: unknown sender (explicit opt-in; see the F6 test below) ----

    @Test
    fun `unknown sender is quarantined when quarantine is opted in`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            quarantineUnknownSenders = true,
        )
        val (disposition, reason) = f.evaluate("+1 555 9999", "Hello")
        assertEquals(MessageDisposition.QUARANTINE, disposition)
        assertNotNull(reason)
        assertEquals(BlockReason.Type.UNKNOWN_SENDER, reason!!.type)
    }

    @Test
    fun `unknown sender reason has correct message`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            quarantineUnknownSenders = true,
        )
        val (_, reason) = f.evaluate("+1 555 9999", "Hello")
        assertEquals("Sender is not in your contacts", reason!!.message)
        assertFalse(reason.canOverride)
    }

    // ---- BLOCK: blocked address list ----

    @Test
    fun `blocked address is blocked`() {
        val f = filter(blockedAddresses = setOf("+1 555 8888"))
        val (disposition, reason) = f.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertNotNull(reason)
        assertEquals(BlockReason.Type.BLOCKED_LIST, reason!!.type)
    }

    @Test
    fun `blocked address reason has correct message`() {
        val f = filter(blockedAddresses = setOf("+1 555 8888"))
        val (_, reason) = f.evaluate("+1 555 8888", "Hello")
        assertEquals("Sender is on your block list", reason!!.message)
        assertFalse(reason.canOverride)
    }

    // ---- BLOCK: content filter keyword match ----

    @Test
    fun `keyword match blocks the message`() {
        val f = filter(knownContacts = setOf("+1 555 1000"), contentKeywords = setOf("loan"))
        val (disposition, reason) = f.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.message.contains("loan"))
    }

    @Test
    fun `phrase match blocks the message`() {
        val f = filter(knownContacts = setOf("+1 555 1000"), contentPhrases = setOf("free money"))
        val (disposition, reason) = f.evaluate("+1 555 1000", "Get free money now")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.message.contains("free money"))
    }

    // ---- Starred contact bypass ----

    @Test
    fun `starred contact bypasses blocked address`() {
        val f = filter(
            blockedAddresses = setOf("+1 555 1000"),
            starredContacts = setOf("+1 555 1000"),
        )
        val (disposition, reason) = f.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.canOverride)
        assertEquals(BlockReason.Type.STARRED_CONTACT_RULE, reason.type)
    }

    @Test
    fun `starred contact bypasses content filter`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            contentKeywords = setOf("loan"),
            starredContacts = setOf("+1 555 1000"),
        )
        val (disposition, reason) = f.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.canOverride)
    }

    @Test
    fun `starred unknown sender bypasses quarantine`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            starredContacts = setOf("+1 555 2000"),
            quarantineUnknownSenders = true,
        )
        val (disposition, reason) = f.evaluate("+1 555 2000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.canOverride)
        assertEquals(BlockReason.Type.STARRED_CONTACT_RULE, reason.type)
    }

    // ---- Case-insensitive and trimmed matching ----

    @Test
    fun `blocked address matching is case-insensitive and trimmed`() {
        val f = filter(blockedAddresses = setOf("  +1 555 8888  "))
        val (disposition, _) = f.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `unknown sender matching is case-insensitive and trimmed`() {
        val f = filter(knownContacts = setOf("  +1 555 1000  "))
        val (disposition, _) = f.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `starred contact matching is case-insensitive and trimmed`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            starredContacts = setOf("  +1 555 2000  "),
        )
        val (disposition, _) = f.evaluate("+1 555 2000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `blocked address matching survives formatting variance`() {
        val f = filter(blockedAddresses = setOf("+1 555 8888"))
        for (variant in listOf("+15558888", "555-8888", "(555) 8888", "+1 (555) 8888")) {
            val (disposition, _) = f.evaluate(variant, "Hello")
            assertEquals(variant, MessageDisposition.BLOCK, disposition)
        }
    }

    // ---- Decision priority: blocked address before unknown sender ----

    @Test
    fun `blocked address takes priority over unknown sender`() {
        val f = filter(
            knownContacts = setOf("+1 555 1000"),
            blockedAddresses = setOf("+1 555 9999"),
        )
        val (disposition, reason) = f.evaluate("+1 555 9999", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertEquals(BlockReason.Type.BLOCKED_LIST, reason!!.type)
    }

    // ---- Empty filter defaults ----

    @Test
    fun `empty filter quarantines unknown sender when quarantine is opted in`() {
        val f = filter(quarantineUnknownSenders = true)
        val (disposition, reason) = f.evaluate("+1 555 0000", "Hello")
        assertEquals(MessageDisposition.QUARANTINE, disposition)
        assertNotNull(reason)
        assertEquals(BlockReason.Type.UNKNOWN_SENDER, reason!!.type)
    }

    // ---- F6 regression lock: factory default is fail-open delivery ----
    // The hold is opt-in even though QuarantineStore now persists. Default
    // construction must DELIVER unknown senders, never QUARANTINE.

    @Test
    fun `default construction delivers an unknown sender - factory default is fail-open`() {
        val f = InboundFilter()
        assertFalse(f.quarantineUnknownSenders)
        val (disposition, reason) = f.evaluate("+1 555 0000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNull(reason)
    }

    @Test
    fun `empty filter with known contact delivers`() {
        val f = filter(knownContacts = setOf("+1 555 0000"))
        val (disposition, reason) = f.evaluate("+1 555 0000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNull(reason)
    }
}
