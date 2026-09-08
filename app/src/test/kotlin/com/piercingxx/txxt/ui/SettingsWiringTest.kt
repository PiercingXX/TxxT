package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the settings screen wiring (WS12 T5): the manifest registers
 * SettingsActivity, ThreadActivity's settings affordance launches it (the real
 * call site — the running app reaches the screen), and SettingsActivity sets
 * persists the SettingsStore through SharedPreferences.
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
    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
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
        assertTrue(
            "the conversation list must also launch SettingsActivity",
            mainActivity.contains("SettingsActivity::class.java"),
        )
    }

    // ---- SettingsActivity wiring ----

    @Test
    fun `SettingsActivity offers a notification-sound picker`() {
        val layout = sequenceOf(
            File("src/main/res/layout/activity_settings.xml"),
            File("app/src/main/res/layout/activity_settings.xml"),
        ).first { it.exists() }.readText()
        assertTrue(
            "settings layout must expose a notification sound button",
            layout.contains("@+id/notification_sound_button"),
        )
        assertTrue(
            "SettingsActivity must launch the system ringtone picker",
            settingsActivity.contains("ACTION_RINGTONE_PICKER"),
        )
        assertTrue(
            "a picked ringtone must bump the sound channel via NotificationPrefs.setSound",
            settingsActivity.contains("NotificationPrefs.setSound"),
        )
    }

    @Test
    fun `SettingsActivity persists the store through SharedPreferences`() {
        assertFalse(
            "FLAG_SECURE blacks screenshots; the operator asked to capture the app",
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

    // ---- Theme render-path wiring (H4): the picker drives the real renderer ----

    @Test
    fun `the preset spinner shows display names and maps back through the render enum`() {
        assertTrue(
            "The preset spinner must show each preset's displayName",
            settingsActivity.contains("ThemePreset.entries.map { it.displayName }"),
        )
        assertTrue(
            "Selections must map positions back through ThemePreset.entries",
            settingsActivity.contains("ThemePreset.entries[themePreset.selectedItemPosition]"),
        )
    }

    @Test
    fun `persist drives the real theme controller over txxt_theme`() {
        // The render path reads txxt_theme; a pick that only wrote
        // txxt_settings would be decorative.
        assertTrue(
            "SettingsActivity must build its ThemeController over txxt_theme",
            settingsActivity.contains("THEME_PREFS_NAME = \"txxt_theme\""),
        )
        assertTrue(
            "SettingsActivity must report the picked preset to the render controller",
            settingsActivity.contains("themeController.setManualTheme(store.themePreset)"),
        )
        assertTrue(
            "SettingsActivity must report the auto-sync toggle to the render controller",
            settingsActivity.contains("themeController.setAutoSync(store.autoSyncTheme)"),
        )
    }

    @Test
    fun `initial selection events do not persist a partially-loaded store`() {
        assertTrue(
            "Listeners must be gated on the init guard",
            settingsActivity.contains("controlsReady = false") &&
                settingsActivity.contains("controlsReady = true"),
        )
        val guard = Regex("""if \(!controlsReady\) return""")
        assertEquals(2, guard.findAll(settingsActivity).count())
    }

    @Test
    fun `backup and restore are honest file-backed operations`() {
        assertTrue(
            "Backup must write through an injectable seam",
            settingsActivity.contains("internal var writeBackupText"),
        )
        assertTrue(
            "Restore must read through an injectable seam",
            settingsActivity.contains("internal var readBackupText"),
        )
        assertTrue(
            "Backup must serialise both stores through SettingsBackupFile",
            settingsActivity.contains("SettingsBackupFile.encode"),
        )
        assertTrue(
            "Restore must apply parsed keys into prefs",
            settingsActivity.contains("putString(key, value)"),
        )
        assertFalse(
            "Restore must not fake success without reading a backup",
            settingsActivity.contains("\"Settings backed up\"") ||
                settingsActivity.contains("\"Settings restored\""),
        )
    }

    @Test
    fun `persist writes asynchronously`() {
        assertFalse(
            "persist() must use apply(), not commit(), on the main thread",
            settingsActivity.contains("}.commit()"),
        )
        assertTrue(settingsActivity.contains(".apply()"))
    }

    @Test
    fun `SettingsActivity paints chrome through ThemeApplier on resume`() {
        assertTrue(
            "SettingsActivity must construct a ThemeApplier",
            settingsActivity.contains("ThemeApplier("),
        )
        assertTrue(
            "SettingsActivity must override onResume",
            settingsActivity.contains("override fun onResume"),
        )
        assertTrue(
            "SettingsActivity must pin night mode from the ground",
            settingsActivity.contains("setDefaultNightMode"),
        )
    }

    @Test
    fun `SettingsActivity exposes on-phone logs copy and share`() {
        val layout = sequenceOf(
            File("src/main/res/layout/activity_settings.xml"),
            File("app/src/main/res/layout/activity_settings.xml"),
        ).first { it.exists() }.readText()
        assertTrue(layout.contains("@+id/logs_button"))
        assertTrue(settingsActivity.contains("AppLog.shareText()"))
        assertTrue(settingsActivity.contains("ACTION_SEND"))
    }
}