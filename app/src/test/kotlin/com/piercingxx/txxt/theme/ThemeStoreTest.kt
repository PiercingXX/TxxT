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
}