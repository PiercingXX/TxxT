package com.piercingxx.txxt.theme

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * In-memory [ThemeKeyValueStore] so the receiver's routing is JVM-testable
 * without Android (mirrors ThemeControllerTest's fake).
 */
private class ThemeSyncInMemoryKv : ThemeKeyValueStore {
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
 * Verifies the theme-sync receiver's two halves (T5).
 *
 * The Android broadcast dispatch itself (`onReceive`, `Intent`) is not
 * JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), but the OS only ever dispatches `xx.launcher.THEME_CHANGED`
 * to a component that is (a) declared in the manifest for that action and
 * (b) resolvable as a concrete class. This test locks both halves of that
 * contract — mirroring ReceiverManifestTest — and then drives `onReceive` with
 * a MockK-spied [Intent] (mirroring SmsReceiverBlockingTest) to prove the
 * receiver routes the carried preset name through the manual-wins precedence
 * rule: it reports the launcher theme to a ThemeController, and that controller
 * resolves the effective theme exactly as ThemeController's precedence rule
 * dictates.
 */
class ThemeSyncWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private val manifest: File =
        sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }

    private val manifestText: String
        get() = manifest.readText()

    private val context: Context = mockk(relaxed = true)

    /** A controller over an in-memory store the receiver reports into. */
    private fun controller(): ThemeController = ThemeController(ThemeStore(ThemeSyncInMemoryKv()))

    /** A receiver that reports into [into] via the real routing path. */
    private fun receiver(into: ThemeController): ThemeSyncReceiver =
        ThemeSyncReceiver(
            controllerFactory = { into },
            action = ThemeSyncReceiver.ACTION_THEME_CHANGED,
            extraThemeName = ThemeSyncReceiver.EXTRA_THEME_NAME,
        )

    /** A launcher theme-change intent carrying [name] under the real extra key. */
    private fun themeIntent(name: String): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
            every { getStringExtra(ThemeSyncReceiver.EXTRA_THEME_NAME) } returns name
        }

    // ---- wiring: the manifest declares the receiver for the launcher broadcast ----

    @Test
    fun `manifest declares the theme-sync receiver component`() {
        assertTrue(
            "AndroidManifest.xml must declare the .theme.ThemeSyncReceiver component",
            manifestText.contains(".theme.ThemeSyncReceiver"),
        )
    }

    @Test
    fun `manifest registers the receiver for the launcher theme-changed action`() {
        assertTrue(
            "AndroidManifest.xml must register ThemeSyncReceiver for ${ThemeSyncReceiver.ACTION_THEME_CHANGED}",
            manifestText.contains(ThemeSyncReceiver.ACTION_THEME_CHANGED),
        )
    }

    @Test
    fun `declared theme-sync receiver name resolves to a class`() {
        // Throws ClassNotFoundException if the declared name is not a real class.
        Class.forName("com.piercingxx.txxt.theme.ThemeSyncReceiver")
    }

    // ---- routing: the receiver reports the carried preset name to the controller ----

    @Test
    fun `receiver routes a carried preset name to the controller`() {
        val c = controller()
        c.setAutoSync(true) // follow the launcher
        receiver(c).onReceive(context, themeIntent(ThemePreset.GRAPHITE.displayName))
        // The receiver reported the launcher theme; with auto-sync on and no
        // manual pick, the controller's precedence rule makes it effective.
        assertEquals(ThemePreset.GRAPHITE, c.launcherTheme)
        assertEquals(ThemePreset.GRAPHITE, c.effectiveTheme)
    }

    @Test
    fun `a manual theme still wins over the launcher broadcast`() {
        val c = controller()
        c.setManualTheme(ThemePreset.BURGUNDY)
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent(ThemePreset.MIST.displayName))
        // Explicit beats ambient (docs/PRIVACY.md §7): the manual pick wins even
        // though the launcher broadcast a different theme.
        assertEquals(ThemePreset.BURGUNDY, c.effectiveTheme)
    }

    @Test
    fun `receiver ignores a broadcast when auto sync is off`() {
        val c = controller()
        receiver(c).onReceive(context, themeIntent(ThemePreset.MIST.displayName))
        // Auto-sync is off by default; the launcher theme is recorded but never
        // drives the effective theme.
        assertEquals(ThemePreset.MIST, c.launcherTheme)
        assertEquals(ThemePreset.DEFAULT, c.effectiveTheme)
    }

    @Test
    fun `receiver ignores a non-theme action`() {
        val c = controller()
        receiver(c).onReceive(
            context,
            spyk(Intent("some.other.action")).apply {
                every { action } returns "some.other.action"
            },
        )
        assertNull(c.launcherTheme)
    }

    @Test
    fun `receiver ignores an unknown preset name`() {
        val c = controller()
        receiver(c).onReceive(context, themeIntent("Not A Real Preset"))
        assertNull(c.launcherTheme)
    }
}