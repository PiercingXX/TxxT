package com.piercingxx.txxt.ui

import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS12-corrective T1 — the persisted blocking/starred settings store.
 *
 * Proves [SettingsBlockingStore] holds the persisted keyword/phrase/blocked-address
 * and starred-contact sets, builds the [SettingsBlocking]/[SettingsStarred]
 * models the settings screen edits, round-trips through the backup string-map
 * format, and — critically — that the load-and-apply seam reaches the running
 * application: [SettingsBlockingStore.loadAndApply] rebuilds the process-wide
 * [com.piercingxx.txxt.block.InboundFilter] through [LiveInboundFilter.apply]
 * from the *persisted* sets, so a blocked address blocks and a starred contact
 * bypasses.
 *
 * This fails if the store never applies its persisted settings to the live
 * filter: `loadAndApply` must call `LiveInboundFilter.apply`, and the stored
 * filter's behaviour must reflect the store's contents.
 */
class SettingsBlockingStoreTest {

    // ---- Persistence: the store holds the blocking/starred sets ----

    @Test
    fun `store holds the persisted keyword phrase address and starred sets`() {
        val store = SettingsBlockingStore(
            keywords = setOf("loan", "prize"),
            phrases = setOf("free money"),
            blockedAddresses = setOf("+1 555 8888"),
            starredContacts = setOf("+1 555 1000"),
        )
        assertEquals(setOf("loan", "prize"), store.keywords())
        assertEquals(setOf("free money"), store.phrases())
        assertEquals(setOf("+1 555 8888"), store.blockedAddresses())
        assertEquals(setOf("+1 555 1000"), store.starredContacts())
    }

    @Test
    fun `defaults are empty sets`() {
        val store = SettingsBlockingStore.defaults()
        assertTrue(store.keywords().isEmpty())
        assertTrue(store.phrases().isEmpty())
        assertTrue(store.blockedAddresses().isEmpty())
        assertTrue(store.starredContacts().isEmpty())
        assertFalse(store.quarantineUnknownSenders())
    }

    @Test
    fun `buildBlocking carries the persisted rules into the settings model`() {
        val store = SettingsBlockingStore(
            keywords = setOf("loan"),
            phrases = setOf("free money"),
            blockedAddresses = setOf("+1 555 8888"),
        )
        val blocking = store.buildBlocking()
        assertTrue(blocking.isBlocked("loan"))
        assertTrue(blocking.isBlocked("free money"))
        assertTrue(blocking.isAddressBlocked("+1 555 8888"))
    }

    @Test
    fun `buildStarred carries the persisted contacts into the settings model`() {
        val store = SettingsBlockingStore(starredContacts = setOf("+1 555 1000"))
        val starred = store.buildStarred()
        assertTrue(starred.isStarred("+1 555 1000"))
    }

    // ---- Backup round-trip: the store survives the string-map format ----

    @Test
    fun `non-default store round-trips through the backup map unchanged`() {
        val store = SettingsBlockingStore(
            keywords = setOf("loan", "prize"),
            phrases = setOf("free money"),
            blockedAddresses = setOf("+1 555 8888"),
            starredContacts = setOf("+1 555 1000"),
        )
        val restored = SettingsBlockingStore.fromMap(SettingsBlockingStore.toMap(store))
        assertEquals(store.keywords(), restored.keywords())
        assertEquals(store.phrases(), restored.phrases())
        assertEquals(store.blockedAddresses(), restored.blockedAddresses())
        assertEquals(store.starredContacts(), restored.starredContacts())
        assertEquals(store.quarantineUnknownSenders(), restored.quarantineUnknownSenders())
    }

    @Test
    fun `default store round-trips through the backup map unchanged`() {
        val restored = SettingsBlockingStore.fromMap(SettingsBlockingStore.toMap(SettingsBlockingStore.defaults()))
        assertTrue(restored.keywords().isEmpty())
        assertTrue(restored.starredContacts().isEmpty())
    }

    @Test
    fun `partial backup map falls back to empty sets for missing fields`() {
        val store = SettingsBlockingStore.fromMap(emptyMap())
        assertTrue(store.keywords().isEmpty())
        assertTrue(store.phrases().isEmpty())
        assertTrue(store.blockedAddresses().isEmpty())
        assertTrue(store.starredContacts().isEmpty())
    }

    // ---- Load-and-apply seam: the persisted settings reach the live filter ----

    @Test
    fun `loadAndApply blocks a persisted blocked address`() {
        SettingsBlockingStore(
            blockedAddresses = setOf("+1 555 8888"),
        ).loadAndApply()
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertEquals(com.piercingxx.txxt.block.BlockReason.Type.BLOCKED_LIST, reason!!.type)
    }

    @Test
    fun `loadAndApply blocks a persisted keyword match`() {
        SettingsBlockingStore(
            keywords = setOf("loan"),
        ).loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `loadAndApply lets a persisted starred contact bypass a blocked address`() {
        SettingsBlockingStore(
            blockedAddresses = setOf("+1 555 1000"),
            starredContacts = setOf("+1 555 1000"),
        ).loadAndApply()
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertEquals(com.piercingxx.txxt.block.BlockReason.Type.STARRED_CONTACT_RULE, reason!!.type)
        assertTrue(reason.canOverride)
    }

    @Test
    fun `loadAndApply replaces the previously applied filter`() {
        SettingsBlockingStore(blockedAddresses = setOf("+1 555 8888")).loadAndApply()
        SettingsBlockingStore.defaults().loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        // No longer blocked after an empty store is applied. The empty default
        // store DELIVERs: the unknown-sender hold is opt-in (default off).
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `loadAndApply with quarantine on holds an unknown sender`() {
        SettingsBlockingStore(quarantineUnknownSenders = true).loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 0000", "Hello")
        assertEquals(MessageDisposition.QUARANTINE, disposition)
    }

    @Test
    fun `loadAndApply with quarantine on still delivers a starred sender`() {
        SettingsBlockingStore(
            starredContacts = setOf("+1 555 0000"),
            quarantineUnknownSenders = true,
        ).loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 0000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }
}