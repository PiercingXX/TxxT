package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** In-memory [ThemeKeyValueStore] so the applier is JVM-testable without Android. */
private class ThemeApplierInMemoryKv : ThemeKeyValueStore {
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

/**
 * Verifies the theme applier (T6).
 *
 * Two halves, mirroring how ThemeSyncWiringTest locks T5's contract:
 *
 *  - **application**: `apply()` reads the controller's effective theme, derives
 *    the concrete [ThemeTokens] via [deriveTokens], and hands them to the
 *    `applyTokens` seam. A recording fake seam proves the tokens painted for an
 *    effective theme are exactly [deriveTokens] of that theme — so the running
 *    UI is driven from the chosen tokens.
 *  - **wiring**: the running thread screen must actually reach the applier. The
 *    Android Activity itself is not JVM-testable without Robolectric (not in the
 *    offline cache), so — exactly as ThemeSyncWiringTest reads the manifest to
 *    prove the receiver is registered — this test reads `ThreadActivity.kt` and
 *    asserts it constructs a [ThemeApplier] and calls `apply()`. That fails if
 *    the applier is dead code.
 */
class ThemeApplierTest {

    private val activitySource: File =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt"),
            File("app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt"),
        ).first { it.exists() }

    private val activityText: String
        get() = activitySource.readText()

    private fun controller(): ThemeController = ThemeController(ThemeStore(ThemeApplierInMemoryKv()))

    private fun applier(controller: ThemeController, applied: MutableList<ThemeTokens>): ThemeApplier =
        ThemeApplier(controller) { applied.add(it) }

    // ---- application: the applier paints the derived tokens for the effective theme ----

    @Test
    fun `applier paints the derived tokens for the default effective theme`() {
        val c = controller()
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        // Auto-sync off, no manual pick: effective theme is DEFAULT (AMOLED Night).
        assertEquals(ThemePreset.DEFAULT, c.effectiveTheme)
        assertEquals(listOf(deriveTokens(ThemePreset.DEFAULT)), applied)
    }

    @Test
    fun `applier paints the derived tokens for a manual theme`() {
        val c = controller()
        c.setManualTheme(ThemePreset.PAPER)
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        assertEquals(ThemePreset.PAPER, c.effectiveTheme)
        assertEquals(listOf(deriveTokens(ThemePreset.PAPER)), applied)
    }

    @Test
    fun `applier paints the launcher theme when auto sync wins`() {
        val c = controller()
        c.setAutoSync(true)
        c.onLauncherTheme(ThemePreset.FOREST_NIGHT)
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        assertEquals(ThemePreset.FOREST_NIGHT, c.effectiveTheme)
        assertEquals(listOf(deriveTokens(ThemePreset.FOREST_NIGHT)), applied)
    }

    @Test
    fun `a manual theme still wins when the launcher broadcast a different one`() {
        val c = controller()
        c.setManualTheme(ThemePreset.BURGUNDY)
        c.setAutoSync(true)
        c.onLauncherTheme(ThemePreset.MIST)
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        // Explicit beats ambient (docs/PRIVACY.md §7): the manual pick drives the paint.
        assertEquals(ThemePreset.BURGUNDY, c.effectiveTheme)
        assertEquals(listOf(deriveTokens(ThemePreset.BURGUNDY)), applied)
    }

    @Test
    fun `applier paints a Custom launcher ground, not the default`() {
        // The applier reads the GROUND, not the preset-shaped view: Custom has
        // no ThemePreset entry, so painting from `effectiveTheme` would
        // silently repaint AMOLED Night over the colour the user picked.
        val c = controller()
        c.setAutoSync(true)
        c.onLauncherGround(customGround(0xFFEEDDCCL))
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        assertEquals(listOf(deriveTokens(customGround(0xFFEEDDCCL))), applied)
        assertEquals(0xFFEEDDCCL, applied.single().background)
    }

    @Test
    fun `a manual theme still wins over a Custom launcher ground`() {
        val c = controller()
        c.setManualTheme(ThemePreset.BURGUNDY)
        c.setAutoSync(true)
        c.onLauncherGround(customGround(0xFFEEDDCCL))
        val applied = mutableListOf<ThemeTokens>()
        applier(c, applied).apply()
        assertEquals(listOf(deriveTokens(ThemePreset.BURGUNDY)), applied)
    }

    // ---- wiring: the running thread screen reaches the applier ----

    @Test
    fun `thread activity constructs the theme applier`() {
        assertTrue(
            "ThreadActivity.kt must construct a ThemeApplier (T6 wire-in)",
            activityText.contains("ThemeApplier("),
        )
    }

    @Test
    fun `thread activity calls apply on the theme applier`() {
        assertTrue(
            "ThreadActivity.kt must call ThemeApplier.apply() so the running UI is painted",
            activityText.contains("ThemeApplier(") && activityText.contains(".apply()"),
        )
    }
}