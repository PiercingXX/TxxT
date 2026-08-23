package com.piercingxx.txxt.ui

import com.piercingxx.txxt.block.MessageDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS12 T3 — blocking management and the starred-contacts list.
 *
 * Proves the settings-screen models [SettingsBlocking] and [SettingsStarred]
 * hold the blocklist and the starred list with add/remove/membership semantics,
 * and — critically — that they reach the running application: [SettingsBlocking.filter]
 * builds the app-layer [com.piercingxx.txxt.block.InboundFilter] from the
 * current rules, so a blocked address blocks, a keyword/phrase blocks, and a
 * starred contact bypasses every suppression. This fails if the settings models
 * are never wired into the inbound path.
 */
class SettingsBlockingStarredTest {

    // --- SettingsBlocking: blocking management ---

    @Test
    fun `added keyword and phrase are blocked`() {
        val b = SettingsBlocking().addKeyword("loan").addPhrase("free money")
        assertTrue(b.isBlocked("loan"))
        assertTrue(b.isBlocked("free money"))
    }

    @Test
    fun `a never-added rule is not blocked`() {
        val b = SettingsBlocking()
        assertFalse(b.isBlocked("loan"))
        assertFalse(b.isBlocked("free money"))
    }

    @Test
    fun `adding then removing a rule leaves it unblocked`() {
        val b = SettingsBlocking().addKeyword("loan").removeKeyword("loan")
        assertFalse(b.isBlocked("loan"))
    }

    @Test
    fun `keyword and phrase lists reflect the adds and removes`() {
        val b = SettingsBlocking()
            .addKeyword("loan")
            .addKeyword("prize")
            .addPhrase("free money")
            .removeKeyword("loan")
        assertEquals(setOf("prize"), b.keywords())
        assertEquals(setOf("free money"), b.phrases())
    }

    @Test
    fun `blocked address is tracked independently`() {
        val b = SettingsBlocking().blockAddress("+1 555 8888")
        assertTrue(b.isAddressBlocked("+1 555 8888"))
        assertFalse(b.isAddressBlocked("+1 555 9999"))
    }

    @Test
    fun `unblocking an address removes it from the list`() {
        val b = SettingsBlocking().blockAddress("+1 555 8888").unblockAddress("+1 555 8888")
        assertFalse(b.isAddressBlocked("+1 555 8888"))
        assertEquals(emptySet<String>(), b.blockedAddresses())
    }

    @Test
    fun `listed rules are a snapshot, not a live view`() {
        val b = SettingsBlocking().addKeyword("loan")
        val snapshot = b.keywords()
        b.addKeyword("prize")
        assertEquals(setOf("loan"), snapshot)
    }

    // --- SettingsStarred: starred-contacts list ---

    @Test
    fun `starred contact is recognised as starred`() {
        val s = SettingsStarred().star("+1 555 1000")
        assertTrue(s.isStarred("+1 555 1000"))
        assertFalse(s.isStarred("+1 555 2000"))
    }

    @Test
    fun `unstarring removes the contact from the list`() {
        val s = SettingsStarred().star("+1 555 1000").unstar("+1 555 1000")
        assertFalse(s.isStarred("+1 555 1000"))
        assertEquals(emptySet<String>(), s.contacts())
    }

    @Test
    fun `starred list reflects the stars and unstars`() {
        val s = SettingsStarred().star("+1 555 1000").star("+1 555 2000").unstar("+1 555 1000")
        assertEquals(setOf("+1 555 2000"), s.contacts())
    }

    // --- Wiring: the settings reach the running InboundFilter ---

    @Test
    fun `settings block a blocked address through the inbound filter`() {
        val blocking = SettingsBlocking().blockAddress("+1 555 8888")
        val starred = SettingsStarred()
        val (disposition, reason) = blocking.filter(starred).evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertNotNull(reason)
    }

    @Test
    fun `settings block a keyword match through the inbound filter`() {
        val blocking = SettingsBlocking().addKeyword("loan")
        val starred = SettingsStarred()
        val (disposition, _) = blocking.filter(starred).evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `settings block a phrase match through the inbound filter`() {
        val blocking = SettingsBlocking().addPhrase("free money")
        val starred = SettingsStarred()
        val (disposition, _) = blocking.filter(starred).evaluate("+1 555 1000", "Get free money now")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `a clean message from a non-blocked known sender is delivered`() {
        val blocking = SettingsBlocking().addKeyword("loan")
        val starred = SettingsStarred()
        val (disposition, reason) = blocking.filter(starred, knownContacts = setOf("+1 555 1000"))
            .evaluate("+1 555 1000", "Hey there")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNull(reason)
    }

    @Test
    fun `a starred contact bypasses a blocked address`() {
        val blocking = SettingsBlocking().blockAddress("+1 555 1000")
        val starred = SettingsStarred().star("+1 555 1000")
        val (disposition, reason) = blocking.filter(starred).evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNotNull(reason)
        assertTrue(reason!!.canOverride)
    }

    @Test
    fun `a starred contact bypasses the content filter`() {
        val blocking = SettingsBlocking().addKeyword("loan")
        val starred = SettingsStarred().star("+1 555 1000")
        val (disposition, _) = blocking.filter(starred).evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `an unstarred contact keeps the full suppression posture`() {
        val blocking = SettingsBlocking().addKeyword("loan")
        val starred = SettingsStarred().star("+1 555 1000")
        val (disposition, _) = blocking.filter(starred).evaluate("+1 555 2000", "Get a loan today")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }
}