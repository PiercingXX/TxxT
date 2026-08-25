package com.piercingxx.txxt.ui

import com.piercingxx.txxt.block.LiveInboundFilter
import com.piercingxx.txxt.block.MessageDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [BlockingRules]: the pure map transforms behind the
 * blocking editor and the launcher's block/star row actions. Every transform
 * must produce the exact string-map shape [SettingsBlockingStore] round-trips
 * through — proven by driving the REAL store + live filter over the produced
 * maps, not by inspecting map internals.
 */
class BlockingRulesTest {

    private val blockedKey = SettingsBlockingStore.KEY_BLOCKED_ADDRESSES
    private val starredKey = SettingsBlockingStore.KEY_STARRED_CONTACTS

    @Test
    fun `withEntry adds and withoutEntry removes`() {
        var map = emptyMap<String, String>()
        map = BlockingRules.withEntry(map, blockedKey, "+15550001111")
        map = BlockingRules.withEntry(map, blockedKey, "+15550002222")
        assertEquals(
            setOf("+15550001111", "+15550002222"),
            BlockingRules.entries(map, blockedKey),
        )

        map = BlockingRules.withoutEntry(map, blockedKey, "+15550001111")
        assertEquals(setOf("+15550002222"), BlockingRules.entries(map, blockedKey))
    }

    @Test
    fun `withEntry collapses pasted line breaks into one entry`() {
        // The persisted sets are newline-joined: a value carrying "\n" must
        // never re-parse as several entries.
        val map = BlockingRules.withEntry(
            emptyMap(),
            SettingsBlockingStore.KEY_PHRASES,
            "free money\nclick here",
        )
        assertEquals(
            setOf("free money click here"),
            BlockingRules.entries(map, SettingsBlockingStore.KEY_PHRASES),
        )
    }

    @Test
    fun `withEntry trims and ignores blank values`() {
        var map = emptyMap<String, String>()
        map = BlockingRules.withEntry(map, blockedKey, "  +15550001111  ")
        map = BlockingRules.withEntry(map, blockedKey, "   ")
        assertEquals(setOf("+15550001111"), BlockingRules.entries(map, blockedKey))
    }

    @Test
    fun `withStarredToggled flips the starred state both ways`() {
        var map = emptyMap<String, String>()

        val (starredMap, nowStarred) = BlockingRules.withStarredToggled(map, "+15550001111")
        assertTrue(nowStarred)
        assertTrue(BlockingRules.isStarred(starredMap, "+15550001111"))

        val (unstarredMap, stillStarred) =
            BlockingRules.withStarredToggled(starredMap, "+15550001111")
        assertFalse(stillStarred)
        assertFalse(BlockingRules.isStarred(unstarredMap, "+15550001111"))
    }

    @Test
    fun `a produced map drives the real store and live filter`() {
        val map = BlockingRules.withEntry(emptyMap(), blockedKey, "+15550001111")
        SettingsBlockingStore.fromMap(map).loadAndApply()
        try {
            val (disposition, _) =
                LiveInboundFilter.current.evaluate("+15550001111", "hello")
            assertEquals(MessageDisposition.BLOCK, disposition)
            val (okDisposition, _) =
                LiveInboundFilter.current.evaluate("+15559998888", "hello")
            assertEquals(MessageDisposition.DELIVER, okDisposition)
        } finally {
            // Reset the process-wide filter so other tests see the default.
            SettingsBlockingStore.defaults().loadAndApply()
        }
    }

    @Test
    fun `a starred contact in a produced map bypasses its own block`() {
        var map = BlockingRules.withEntry(emptyMap(), blockedKey, "+15550001111")
        map = BlockingRules.withStarredToggled(map, "+15550001111").first
        SettingsBlockingStore.fromMap(map).loadAndApply()
        try {
            val (disposition, _) =
                LiveInboundFilter.current.evaluate("+15550001111", "hello")
            assertEquals(MessageDisposition.DELIVER, disposition)
        } finally {
            SettingsBlockingStore.defaults().loadAndApply()
        }
    }
}
