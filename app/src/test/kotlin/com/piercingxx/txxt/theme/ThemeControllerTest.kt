package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [ThemeKeyValueStore] so the controller logic is JVM-testable without Android. */
private class InMemoryKv : ThemeKeyValueStore {
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

class ThemeControllerTest {

    @Test
    fun `a manual theme set through the controller wins over auto sync`() {
        val controller = ThemeController(ThemeStore(InMemoryKv()))
        controller.setManualTheme(ThemePreset.BURGUNDY)
        controller.setAutoSync(true)
        controller.onLauncherTheme(ThemePreset.MIST)
        assertEquals(ThemePreset.BURGUNDY, controller.effectiveTheme)
    }

    @Test
    fun `controller follows the launcher when auto sync is on and no manual pick`() {
        val controller = ThemeController(ThemeStore(InMemoryKv()))
        controller.setAutoSync(true)
        controller.onLauncherTheme(ThemePreset.GRAPHITE)
        assertEquals(ThemePreset.GRAPHITE, controller.effectiveTheme)
    }

    @Test
    fun `controller ignores the launcher when auto sync is off`() {
        val controller = ThemeController(ThemeStore(InMemoryKv()))
        controller.onLauncherTheme(ThemePreset.MIST)
        assertEquals(ThemePreset.DEFAULT, controller.effectiveTheme)
    }

    @Test
    fun `controller defaults to the brand default ground`() {
        val controller = ThemeController(ThemeStore(InMemoryKv()))
        assertNull(controller.launcherTheme)
        assertFalse(controller.autoSyncEnabled)
        assertEquals(ThemePreset.DEFAULT, controller.manualTheme)
        assertEquals(ThemePreset.DEFAULT, controller.effectiveTheme)
    }

    @Test
    fun `a manual pick persists through the store and survives a new controller`() {
        val kv = InMemoryKv()
        ThemeController(ThemeStore(kv)).setManualTheme(ThemePreset.PAPER)
        // A fresh controller over the same backing reads the persisted manual pick.
        assertEquals(ThemePreset.PAPER, ThemeController(ThemeStore(kv)).manualTheme)
    }

    // ---- Launcher-theme persistence (H3): the broadcast must outlive the receiver ----

    @Test
    fun `onLauncherTheme writes the report into the store under its stable key`() {
        val kv = InMemoryKv()
        ThemeController(ThemeStore(kv)).onLauncherTheme(ThemePreset.MIST)
        assertEquals(ThemePreset.MIST.key, kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
    }

    @Test
    fun `a launcher report survives process death via the persisted store`() {
        // The receiver's controller is short-lived; the durable record is what
        // makes its broadcast functional. Simulate death: controller #1 reports
        // and is dropped, then a FRESH controller over the same backing resolves.
        val kv = InMemoryKv()
        val dying = ThemeController(ThemeStore(kv))
        dying.setAutoSync(true)
        dying.onLauncherTheme(ThemePreset.GRAPHITE)

        val reborn = ThemeController(ThemeStore(kv))
        assertNull(reborn.launcherTheme) // in-memory copy starts empty...
        assertEquals(ThemePreset.GRAPHITE, reborn.effectiveTheme) // ...store carries it
    }

    @Test
    fun `a persisted launcher report does not apply when auto sync is off`() {
        val kv = InMemoryKv()
        val first = ThemeController(ThemeStore(kv))
        first.onLauncherTheme(ThemePreset.MIST)
        assertFalse(first.autoSyncEnabled)

        val second = ThemeController(ThemeStore(kv))
        assertEquals(ThemePreset.DEFAULT, second.effectiveTheme)
    }

    @Test
    fun `an in-memory report wins over an older persisted one`() {
        val kv = InMemoryKv()
        ThemeController(ThemeStore(kv)).onLauncherTheme(ThemePreset.BURGUNDY)
        val later = ThemeController(ThemeStore(kv))
        later.setAutoSync(true)
        later.onLauncherTheme(ThemePreset.PAPER)
        assertEquals(ThemePreset.PAPER, later.effectiveTheme)
    }

    @Test
    fun `a manual pick still beats a persisted launcher report`() {
        val kv = InMemoryKv()
        ThemeController(ThemeStore(kv)).onLauncherTheme(ThemePreset.PAPER)

        val fresh = ThemeController(ThemeStore(kv))
        fresh.setManualTheme(ThemePreset.FOREST_NIGHT)
        fresh.setAutoSync(true)
        assertEquals(ThemePreset.FOREST_NIGHT, fresh.effectiveTheme)
    }
}