package com.piercingxx.txxt.theme

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the theme-lifecycle P0: apply in onResume, and pin night mode from
 * the effective ground so SearchView/dialogs do not follow the OS clock.
 *
 * The Android Activity itself is not JVM-testable without Robolectric (not
 * in the offline cache), so — like [ThemeApplierTest] — this reads the
 * activity sources for the named deliverables and drives the pure
 * [ThemeApplier.nightModeFor] mapping over presets and custom grounds.
 */
class ThreadThemeLifecycleTest {

    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }
    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
    private val settingsActivity: String by lazy { sourceText("ui/SettingsActivity.kt") }
    private val blockingActivity: String by lazy { sourceText("ui/BlockingActivity.kt") }

    private fun blockAfter(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("missing $marker", start >= 0)
        val next = source.indexOf("override fun", start + marker.length)
        return if (next < 0) source.substring(start) else source.substring(start, next)
    }

    @Test
    fun `thread activity onResume reapplies the theme`() {
        assertTrue(
            "ThreadActivity.kt must override onResume",
            threadActivity.contains("override fun onResume"),
        )
        assertTrue(
            "ThreadActivity.onResume must call applyTheme()",
            blockAfter(threadActivity, "override fun onResume").contains("applyTheme()"),
        )
    }

    @Test
    fun `thread activity pins night mode from the effective ground`() {
        assertTrue(
            "ThreadActivity.kt must call AppCompatDelegate.setDefaultNightMode",
            threadActivity.contains("setDefaultNightMode"),
        )
        assertTrue(
            "night mode must come from ThemeApplier.nightModeFor (ground-from-preset)",
            threadActivity.contains("ThemeApplier.nightModeFor"),
        )
    }

    @Test
    fun `main activity onResume reapplies the theme`() {
        assertTrue(mainActivity.contains("override fun onResume"))
        assertTrue(
            "MainActivity.onResume must call applyTheme()",
            blockAfter(mainActivity, "override fun onResume").contains("applyTheme()"),
        )
        assertTrue(
            "MainActivity must pin night mode so SearchView follows the ground",
            mainActivity.contains("setDefaultNightMode"),
        )
        assertTrue(
            "MainActivity must push tokens into the conversation-list adapter",
            mainActivity.contains("adapter.applyTheme(tokens)"),
        )
    }

    @Test
    fun `settings and blocking paint chrome through ThemeApplier`() {
        assertTrue(
            "SettingsActivity must construct a ThemeApplier",
            settingsActivity.contains("ThemeApplier("),
        )
        assertTrue(
            "SettingsActivity.onResume must call applyTheme()",
            blockAfter(settingsActivity, "override fun onResume").contains("applyTheme()"),
        )
        assertTrue(
            "BlockingActivity must construct a ThemeApplier",
            blockingActivity.contains("ThemeApplier("),
        )
        assertTrue(
            "BlockingActivity.onResume must call applyTheme()",
            blockAfter(blockingActivity, "override fun onResume").contains("applyTheme()"),
        )
    }

    @Test
    fun `nightModeFor is YES on dark presets and NO on Paper Mist and light custom`() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            ThemeApplier.nightModeFor(ThemePreset.AMOLED_NIGHT.isDark),
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            ThemeApplier.nightModeFor(ThemePreset.BURGUNDY.ground),
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            ThemeApplier.nightModeFor(ThemePreset.PAPER.isDark),
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            ThemeApplier.nightModeFor(ThemePreset.MIST.ground),
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            ThemeApplier.nightModeFor(customGround(0xFFEEDDCCL)),
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            ThemeApplier.nightModeFor(customGround(0xFF102030L)),
        )
    }

    @Test
    fun `nightModeFor never follows the system clock`() {
        for (preset in ThemePreset.entries) {
            val mode = ThemeApplier.nightModeFor(preset.ground)
            assertTrue(
                "${preset.displayName} must pin YES or NO, never FOLLOW_SYSTEM",
                mode == AppCompatDelegate.MODE_NIGHT_YES ||
                    mode == AppCompatDelegate.MODE_NIGHT_NO,
            )
            assertFalse(mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        assertFalse(
            ThemeApplier.nightModeFor(customGround(0xFF808080L)) ==
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
    }
}
