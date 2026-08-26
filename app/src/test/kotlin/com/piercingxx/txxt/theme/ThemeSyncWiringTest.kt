package com.piercingxx.txxt.theme

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `only piercingxx sibling packages are trusted theme senders`() {
        assertTrue(ThemeSyncReceiver.isFamilyLauncher("com.piercingxx.launcher"))
        assertFalse(ThemeSyncReceiver.isFamilyLauncher("com.piercingxx.txxt"))
        assertFalse(ThemeSyncReceiver.isFamilyLauncher("com.evil.theme"))
    }

    private val context: Context = mockk(relaxed = true)

    /** A controller over an in-memory store the receiver reports into. */
    private fun controller(): ThemeController = ThemeController(ThemeStore(ThemeSyncInMemoryKv()))

    /** A receiver that reports into [into] via the real routing path. */
    private fun receiver(into: ThemeController): ThemeSyncReceiver =
        ThemeSyncReceiver(
            controllerFactory = { into },
            action = ThemeSyncReceiver.ACTION_THEME_CHANGED,
            extraThemeName = ThemeSyncReceiver.EXTRA_THEME_NAME,
            acceptBroadcast = { _, _ -> true },
        )

    /**
     * A launcher theme-change intent carrying [name] under the real extra key,
     * and — when [background] is given — the resolved ground ARGB under the
     * real background extra key.
     *
     * [background] is an `Int` on purpose: that is the signed form the launcher
     * actually writes (0xFFEEDDCC does not fit a positive Int), so tests that
     * pass one exercise the receiver's unmasking rather than a convenient
     * pre-widened value.
     */
    private fun themeIntent(name: String, background: Int? = null): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
            every { getStringExtra(ThemeSyncReceiver.EXTRA_THEME_NAME) } returns name
            every { hasExtra(ThemeSyncReceiver.EXTRA_BACKGROUND) } returns (background != null)
            if (background != null) {
                every { getIntExtra(ThemeSyncReceiver.EXTRA_BACKGROUND, 0) } returns background
            }
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

    // ---- durability: the routed report persists and reaches a fresh controller ----

    @Test
    fun `a routed broadcast persists into the store a fresh controller reads`() {
        // H3 regression lock: the receiver builds its own (short-lived)
        // controller in production, so the report only matters if it is
        // durable. Drive the REAL factory path shape: one controller reports,
        // then a brand-new controller over the same backing store must resolve
        // the launcher theme without ever having been told directly.
        val kv = ThemeSyncInMemoryKv()
        val reporting = ThemeController(ThemeStore(kv))
        reporting.setAutoSync(true)

        receiver(reporting).onReceive(context, themeIntent(ThemePreset.OCEAN_DRIFT.displayName))

        assertEquals(ThemePreset.OCEAN_DRIFT.key, kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
        assertEquals(ThemePreset.OCEAN_DRIFT, ThemeController(ThemeStore(kv)).effectiveTheme)
    }

    @Test
    fun `an unknown preset name persists nothing`() {
        val kv = ThemeSyncInMemoryKv()
        val c = ThemeController(ThemeStore(kv))
        receiver(c).onReceive(context, themeIntent("Not A Real Preset"))
        assertNull(kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
    }

    // ---- Custom: the family's eighth theme, carried by the background extra ----

    @Test
    fun `receiver honours a Custom broadcast through the background extra`() {
        // The regression this locks: resolving on the theme name alone made
        // every Custom broadcast unresolvable, so TxxT dropped it and stayed
        // on its previous preset while every sibling app followed.
        val c = controller()
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent("Custom", 0xFFEEDDCC.toInt()))
        assertEquals(customGround(0xFFEEDDCCL), c.launcherGround)
        assertEquals(customGround(0xFFEEDDCCL), c.effectiveGround)
    }

    @Test
    fun `a Custom ground unmasks the launcher's signed int`() {
        // The launcher writes a signed Int; without the mask a custom ground
        // would arrive as a huge negative number and derive nonsense colours.
        val c = controller()
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent("Custom", 0xFF203040.toInt()))
        assertEquals(0xFF203040L, c.effectiveGround.background)
    }

    @Test
    fun `a light Custom ground gets legible dark text on the first broadcast`() {
        // No second broadcast is coming to fix contrast: the receiver derives
        // the dark/light flag from the carried colour then and there.
        val c = controller()
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent("Custom", 0xFFEEDDCC.toInt()))
        val tokens = deriveTokens(c.effectiveGround)
        assertFalse("a pale custom ground must not wear the dark look", tokens.isDark)
        assertEquals(0xE6000000L, tokens.text) // 90% black, not white-on-white
    }

    @Test
    fun `a Custom broadcast carrying no background persists nothing`() {
        // Sibling-wide rule: keeping the ground the user already has beats
        // guessing at a colour the launcher never sent.
        val kv = ThemeSyncInMemoryKv()
        val c = ThemeController(ThemeStore(kv))
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent("Custom"))
        assertNull(c.launcherGround)
        assertNull(kv.getString(ThemeStore.KEY_LAST_LAUNCHER_THEME))
    }

    @Test
    fun `a routed Custom broadcast survives process death`() {
        val kv = ThemeSyncInMemoryKv()
        val reporting = ThemeController(ThemeStore(kv))
        reporting.setAutoSync(true)

        receiver(reporting).onReceive(context, themeIntent("Custom", 0xFFEEDDCC.toInt()))

        // A brand-new controller over the same backing store repaints the
        // custom colour without ever having been told directly.
        assertEquals(
            customGround(0xFFEEDDCCL),
            ThemeController(ThemeStore(kv)).effectiveGround,
        )
    }

    @Test
    fun `a manual theme still wins over a Custom broadcast`() {
        val c = controller()
        c.setManualTheme(ThemePreset.BURGUNDY)
        c.setAutoSync(true)
        receiver(c).onReceive(context, themeIntent("Custom", 0xFFEEDDCC.toInt()))
        assertEquals(ThemePreset.BURGUNDY.ground, c.effectiveGround)
    }

    @Test
    fun `a named preset broadcast still resolves to its own colour, not the carried one`() {
        val c = controller()
        c.setAutoSync(true)
        // Real broadcasts carry a background for named presets too; the enum
        // stays the source of truth for the seven brand colours.
        receiver(c).onReceive(
            context,
            themeIntent(ThemePreset.PAPER.displayName, 0xFF00FF00.toInt()),
        )
        assertEquals(ThemePreset.PAPER.ground, c.effectiveGround)
        assertEquals(ThemePreset.PAPER, c.launcherTheme)
    }
}