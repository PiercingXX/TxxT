package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the settings screen wiring (WS12 T5): the manifest registers
 * SettingsActivity, ThreadActivity's settings affordance launches it (the real
 * call site — the running app reaches the screen), and SettingsActivity sets
 * FLAG_SECURE and persists the SettingsStore through SharedPreferences.
 *
 * Following the established manifest/source-reading pattern (ThreadWiringTest,
 * ReceiverManifestTest), the Android intent/activity dispatch itself is not
 * JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), so the manifest-declared name must resolve via
 * [Class.forName] and the launch/persistence wiring is locked by reading the
 * source.
 */
class SettingsWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }
    private val settingsActivity: String by lazy { sourceText("ui/SettingsActivity.kt") }

    // ---- Manifest registration ----

    @Test
    fun `the manifest declares the settings activity`() {
        assertTrue(
            "AndroidManifest.xml must declare the .ui.SettingsActivity component",
            manifestText.contains(".ui.SettingsActivity"),
        )
    }

    @Test
    fun `the declared settings activity name resolves to a class`() {
        Class.forName("com.piercingxx.txxt.ui.SettingsActivity")
    }

    // ---- Launch wiring (the running app reaches the screen) ----

    @Test
    fun `ThreadActivity launches SettingsActivity from its settings affordance`() {
        assertTrue(
            "ThreadActivity must start SettingsActivity",
            threadActivity.contains("SettingsActivity::class.java"),
        )
        assertTrue(
            "ThreadActivity must find the settings button",
            threadActivity.contains("settings_button"),
        )
    }

    // ---- SettingsActivity wiring ----

    @Test
    fun `SettingsActivity sets FLAG_SECURE and persists the store through SharedPreferences`() {
        assertTrue(
            "SettingsActivity must set FLAG_SECURE in code",
            settingsActivity.contains("FLAG_SECURE"),
        )
        assertTrue(
            "SettingsActivity must load the layout",
            settingsActivity.contains("R.layout.activity_settings"),
        )
        assertTrue(
            "SettingsActivity must write the store through SharedPreferences",
            settingsActivity.contains("SharedPreferences"),
        )
        assertTrue(
            "SettingsActivity must round-trip the store through SettingsBackup",
            settingsActivity.contains("SettingsBackup"),
        )
    }
}