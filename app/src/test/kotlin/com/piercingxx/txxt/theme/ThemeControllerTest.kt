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
}