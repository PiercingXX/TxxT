package com.piercingxx.txxt.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [BlockOverrideKeyValueStore] so the store logic is JVM-testable without Android. */
private class InMemoryOverrideKeyValueStore : BlockOverrideKeyValueStore {
    private val sets = mutableMapOf<String, Set<String>>()
    override fun getStringSet(key: String): Set<String> = sets[key] ?: emptySet()
    override fun putStringSet(key: String, value: Set<String>) {
        sets[key] = value
    }
}

class BlockOverrideTest {

    // ---- Store persistence ----

    @Test
    fun `no override by default`() {
        val store = BlockOverrideStore(InMemoryOverrideKeyValueStore())
        assertFalse(store.hasOverride("+1 555 1000"))
    }

    @Test
    fun `add override persists across store instances`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+1 555 1000")
        assertTrue(BlockOverrideStore(kv).hasOverride("+1 555 1000"))
    }

    @Test
    fun `remove override clears it`() {
        val kv = InMemoryOverrideKeyValueStore()
        val store = BlockOverrideStore(kv)
        store.addOverride("+1 555 1000")
        store.removeOverride("+1 555 1000")
        assertFalse(store.hasOverride("+1 555 1000"))
    }

    @Test
    fun `override matching is case-insensitive and trimmed`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("  +1 555 1000  ")
        assertTrue(BlockOverrideStore(kv).hasOverride("+1 555 1000"))
    }

    @Test
    fun `override matching survives formatting variance`() {
        // F5: the sender reaching the filter in another format of the same
        // number must still hit the stored override.
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+15551234567")
        val store = BlockOverrideStore(kv)
        assertTrue(store.hasOverride("555-123-4567"))
        assertTrue(store.hasOverride("(555) 123 4567"))
        assertTrue(store.hasOverride("+1 555 1234567"))
        assertFalse(store.hasOverride("+1 555 9999"))
    }

    @Test
    fun `remove override clears every format variant`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+15551234567")
        BlockOverrideStore(kv).removeOverride("555-123-4567")
        assertFalse(BlockOverrideStore(kv).hasOverride("+15551234567"))
    }

    // ---- InboundFilter integration: an override delivers the sender ----
    // These exercise the real call path: InboundFilter consults the store before
    // applying any suppression, so an overridden sender is delivered even when a
    // rule would otherwise block or quarantine them. If InboundFilter stopped
    // consulting the store these tests would fail.

    @Test
    fun `overridden blocked address is delivered`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+1 555 8888")
        val f = InboundFilter(
            blockedAddresses = setOf("+1 555 8888"),
            blockOverrideStore = BlockOverrideStore(kv),
        )
        val (disposition, reason) = f.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertNull(reason)
    }

    @Test
    fun `overridden unknown sender is delivered`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+1 555 9999")
        val f = InboundFilter(
            knownContacts = setOf("+1 555 1000"),
            blockOverrideStore = BlockOverrideStore(kv),
        )
        val (disposition, _) = f.evaluate("+1 555 9999", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `overridden content-filter match is delivered`() {
        val kv = InMemoryOverrideKeyValueStore()
        BlockOverrideStore(kv).addOverride("+1 555 1000")
        val f = InboundFilter(
            knownContacts = setOf("+1 555 1000"),
            contentKeywords = setOf("loan"),
            blockOverrideStore = BlockOverrideStore(kv),
        )
        val (disposition, _) = f.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.DELIVER, disposition)
    }

    @Test
    fun `without an override the sender is still blocked`() {
        val f = InboundFilter(
            blockedAddresses = setOf("+1 555 8888"),
            blockOverrideStore = BlockOverrideStore(InMemoryOverrideKeyValueStore()),
        )
        val (disposition, _) = f.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }
}