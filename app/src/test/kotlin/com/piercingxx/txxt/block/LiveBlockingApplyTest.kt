package com.piercingxx.txxt.block

import com.piercingxx.txxt.ui.SettingsBlockingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WS12-corrective T2 — the settings screen's blocking button load-and-applies
 * the *persisted* blocking/starred settings.
 *
 * The button's real call path is: rebuild a [SettingsBlockingStore] from the
 * persisted backup string-map (the shape `SettingsActivity` reads from
 * SharedPreferences) and call [SettingsBlockingStore.loadAndApply], which
 * drives the [LiveInboundFilter] seam that rebuilds the process-wide
 * [InboundFilter] the inbound receivers read. This test drives that exact path
 * **by name** — build the store from a persisted map, `loadAndApply()`, then
 * assert the live filter's behaviour reflects the persisted settings — so it
 * fails if the button's load-and-apply path never reaches the running filter.
 *
 * This would have failed before the fix: the button applied empty
 * `SettingsBlocking()`/`SettingsStarred()` models directly, so persisted
 * blocking/starred settings never reached the live filter.
 */
class LiveBlockingApplyTest {

    // ---- Behavioural: loadAndApply stores a filter built from the persisted store ----

    @Test
    fun `loadAndApply blocks a persisted blocked address`() {
        val store = SettingsBlockingStore(
            blockedAddresses = setOf("+1 555 8888"),
        )
        store.loadAndApply()
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertEquals(BlockReason.Type.BLOCKED_LIST, reason!!.type)
    }

    @Test
    fun `loadAndApply blocks a persisted keyword match`() {
        val store = SettingsBlockingStore(
            keywords = setOf("loan"),
        )
        store.loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `loadAndApply lets a persisted starred contact bypass a blocked address`() {
        val store = SettingsBlockingStore(
            blockedAddresses = setOf("+1 555 1000"),
            starredContacts = setOf("+1 555 1000"),
        )
        store.loadAndApply()
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertEquals(BlockReason.Type.STARRED_CONTACT_RULE, reason!!.type)
        assertTrue(reason.canOverride)
    }

    @Test
    fun `loadAndApply applies a store rebuilt from the persisted backup map`() {
        // The button rebuilds the store from the persisted string-map shape
        // (the same keys SettingsActivity reads from SharedPreferences); prove
        // that round-trip path reaches the live filter.
        val persisted = SettingsBlockingStore.toMap(
            SettingsBlockingStore(
                keywords = setOf("loan"),
                blockedAddresses = setOf("+1 555 8888"),
                starredContacts = setOf("+1 555 1000"),
            ),
        )
        val store = SettingsBlockingStore.fromMap(persisted)
        store.loadAndApply()
        val (blocked, _) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, blocked)
        val (starred, _) = LiveInboundFilter.current.evaluate("+1 555 1000", "Get a loan today")
        assertEquals(MessageDisposition.DELIVER, starred)
    }

    @Test
    fun `loadAndApply replaces the previously applied filter`() {
        SettingsBlockingStore(blockedAddresses = setOf("+1 555 8888")).loadAndApply()
        SettingsBlockingStore.defaults().loadAndApply()
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        // No longer blocked after an empty store is applied.
        assertEquals(MessageDisposition.QUARANTINE, disposition)
    }

    // ---- Wiring: the settings screen routes through the load-and-apply seam ----

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    @Test
    fun `SettingsActivity blocking button routes through SettingsBlockingStore loadAndApply`() {
        val settingsActivity = sourceText("ui/SettingsActivity.kt")
        assertTrue(
            "SettingsActivity must call loadAndApply() from the blocking button",
            settingsActivity.contains("loadBlockingStore().loadAndApply()"),
        )
        assertTrue(
            "SettingsActivity must no longer apply empty models directly from the blocking button",
            !settingsActivity.contains("LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())"),
        )
    }
}