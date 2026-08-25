package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [ThemeKeyValueStore] so the store logic is JVM-testable without Android. */
private class InMemoryKeyValueStore : ThemeKeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()

    override fun getString(key: String): String? = strings[key]
    override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
    override fun putString(key: String, value: String) {
        strings[key] = value
    }
    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }
}

class ThemeStoreTest {

    // ---- Defaults: privacy by default, brand default ground ----

    @Test
    fun `manual theme defaults to AMOLED Night`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        assertEquals(ThemePreset.DEFAULT, store.manualTheme)
        assertEquals(ThemePreset.AMOLED_NIGHT, store.manualTheme)
    }

    @Test
    fun `auto sync defaults to off`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        assertFalse(store.autoSyncEnabled)
    }

    // ---- Persistence round-trips through the key-value store ----

    @Test
    fun `setting a manual theme persists it across store instances`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).manualTheme = ThemePreset.FOREST_NIGHT
        // A fresh store over the same backing reads the persisted value.
        assertEquals(ThemePreset.FOREST_NIGHT, ThemeStore(kv).manualTheme)
    }

    @Test
    fun `setting auto sync on persists it across store instances`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).autoSyncEnabled = true
        assertTrue(ThemeStore(kv).autoSyncEnabled)
    }

    @Test
    fun `manual theme is stored under the preset's stable key`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).manualTheme = ThemePreset.PAPER
        assertEquals(ThemePreset.PAPER.key, kv.getString(ThemeStore.KEY_MANUAL_THEME))
    }

    @Test
    fun `auto sync is stored as a boolean under its stable key`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).autoSyncEnabled = true
        assertTrue(kv.getBoolean(ThemeStore.KEY_AUTO_SYNC, false))
    }

    // ---- Last launcher theme: the durable record of the receiver's report ----

    @Test
    fun `last launcher theme defaults to null`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        assertNull(store.lastLauncherTheme)
    }

    @Test
    fun `last launcher theme persists across store instances`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherTheme = ThemePreset.GRAPHITE
        // A fresh store over the same backing reads the persisted value.
        assertEquals(ThemePreset.GRAPHITE, ThemeStore(kv).lastLauncherTheme)
    }

    @Test
    fun `last launcher theme is stored under the preset's stable key`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherTheme = ThemePreset.OCEAN_DRIFT
        assertEquals(ThemePreset.OCEAN_DRIFT.key, kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
    }

    @Test
    fun `assigning null to last launcher theme leaves the persisted value untouched`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherTheme = ThemePreset.MIST
        ThemeStore(kv).lastLauncherTheme = null
        assertEquals(ThemePreset.MIST, ThemeStore(kv).lastLauncherTheme)
    }

    // ---- Effective theme: manual wins over auto-sync ----

    @Test
    fun `effective theme is the default when nothing is set`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        assertEquals(ThemePreset.DEFAULT, store.effectiveTheme(null))
        assertEquals(ThemePreset.DEFAULT, store.effectiveTheme(ThemePreset.MIST))
    }

    @Test
    fun `effective theme follows the launcher when auto sync is on`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        store.autoSyncEnabled = true
        assertEquals(ThemePreset.MIST, store.effectiveTheme(ThemePreset.MIST))
        assertEquals(ThemePreset.GRAPHITE, store.effectiveTheme(ThemePreset.GRAPHITE))
    }

    @Test
    fun `effective theme ignores the launcher when auto sync is off`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        store.autoSyncEnabled = false
        assertEquals(ThemePreset.DEFAULT, store.effectiveTheme(ThemePreset.MIST))
    }

    @Test
    fun `a manual theme wins over auto sync`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        store.manualTheme = ThemePreset.BURGUNDY
        store.autoSyncEnabled = true
        assertEquals(ThemePreset.BURGUNDY, store.effectiveTheme(ThemePreset.MIST))
    }

    // ---- Last launcher GROUND: what makes the family's Custom theme durable ----

    @Test
    fun `last launcher ground defaults to null`() {
        assertNull(ThemeStore(InMemoryKeyValueStore()).lastLauncherGround)
    }

    @Test
    fun `a named preset ground persists and reads back as that preset`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherGround = ThemePreset.MIST.ground
        assertEquals(ThemePreset.MIST.ground, ThemeStore(kv).lastLauncherGround)
        assertEquals(ThemePreset.MIST, ThemeStore(kv).lastLauncherTheme)
    }

    @Test
    fun `a custom ground survives process death, colour and all`() {
        // The point of storing a ground rather than only a key: a custom
        // colour exists nowhere but in the broadcast that carried it, so
        // dropping it would silently reset the app to its default ground on
        // the next cold start.
        val kv = InMemoryKeyValueStore()
        val custom = customGround(0xFFEEDDCCL)
        ThemeStore(kv).lastLauncherGround = custom
        assertEquals(custom, ThemeStore(kv).lastLauncherGround)
    }

    @Test
    fun `a custom ground is stored under the custom key with its colour and flag`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherGround = customGround(0xFFEEDDCCL)
        assertEquals(CUSTOM_PRESET_KEY, kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
        assertEquals("${0xFFEEDDCCL}", kv.getString(ThemeStore.KEY_LAST_LAUNCHER_BACKGROUND))
        assertFalse(kv.getBoolean(ThemeStore.KEY_LAST_LAUNCHER_DARK, true)) // light ground
    }

    @Test
    fun `last launcher theme reads null for a custom ground rather than inventing a preset`() {
        // Custom has no ThemePreset entry; the preset-shaped view must say so
        // instead of pretending a named preset was broadcast.
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherGround = customGround(0xFFEEDDCCL)
        assertNull(ThemeStore(kv).lastLauncherTheme)
    }

    @Test
    fun `a named preset broadcast after a custom one leaves no stale colour behind`() {
        val kv = InMemoryKeyValueStore()
        ThemeStore(kv).lastLauncherGround = customGround(0xFFEEDDCCL)
        ThemeStore(kv).lastLauncherTheme = ThemePreset.FOREST_NIGHT
        assertEquals(ThemePreset.FOREST_NIGHT.ground, ThemeStore(kv).lastLauncherGround)
    }

    @Test
    fun `assigning null to last launcher ground leaves the persisted value untouched`() {
        val kv = InMemoryKeyValueStore()
        val custom = customGround(0xFF203040L)
        ThemeStore(kv).lastLauncherGround = custom
        ThemeStore(kv).lastLauncherGround = null
        assertEquals(custom, ThemeStore(kv).lastLauncherGround)
    }

    @Test
    fun `a legacy record holding only a preset key still resolves to a ground`() {
        // Installs that predate the ground columns persisted the key alone.
        val kv = InMemoryKeyValueStore()
        kv.putString(ThemeStore.KEY_LAST_LAUNCHER_THEME, ThemePreset.BURGUNDY.key)
        assertEquals(ThemePreset.BURGUNDY.ground, ThemeStore(kv).lastLauncherGround)
    }

    @Test
    fun `a custom record missing its colour resolves to nothing`() {
        val kv = InMemoryKeyValueStore()
        kv.putString(ThemeStore.KEY_LAST_LAUNCHER_THEME, CUSTOM_PRESET_KEY)
        assertNull(ThemeStore(kv).lastLauncherGround)
    }

    // ---- Effective ground: the same manual-wins precedence, Custom included ----

    @Test
    fun `effective ground is the default ground when nothing is set`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        assertEquals(ThemePreset.DEFAULT.ground, store.effectiveGround(null))
        assertEquals(ThemePreset.DEFAULT.ground, store.effectiveGround(customGround(0xFFEEDDCCL)))
    }

    @Test
    fun `effective ground follows a custom launcher ground when auto sync is on`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        store.autoSyncEnabled = true
        val custom = customGround(0xFFEEDDCCL)
        assertEquals(custom, store.effectiveGround(custom))
    }

    @Test
    fun `effective ground ignores the launcher when auto sync is off`() {
        val store = ThemeStore(InMemoryKeyValueStore())
        store.autoSyncEnabled = false
        assertEquals(ThemePreset.DEFAULT.ground, store.effectiveGround(customGround(0xFFEEDDCCL)))
    }

    @Test
    fun `a manual theme wins over a custom launcher ground`() {
        // Explicit beats ambient (PRIVACY.md §7) — unchanged by Custom support.
        val store = ThemeStore(InMemoryKeyValueStore())
        store.manualTheme = ThemePreset.BURGUNDY
        store.autoSyncEnabled = true
        assertEquals(ThemePreset.BURGUNDY.ground, store.effectiveGround(customGround(0xFFEEDDCCL)))
    }
}