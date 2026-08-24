package com.piercingxx.txxt.ui

import com.piercingxx.txxt.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun `defaults use sender-only lock-screen privacy`() {
        // PRIVACY.md §3: lock-screen privacy defaults to sender-only.
        assertEquals(LockScreenPrivacy.SENDER_ONLY, SettingsStore.defaults().lockScreenPrivacy)
    }

    @Test
    fun `defaults use sound alert style`() {
        // PRIVACY.md §8: the global alert style is sound; content redaction is a
        // separate dimension handled by lock-screen privacy.
        assertEquals(AlertStyle.SOUND, SettingsStore.defaults().alertStyle)
    }

    @Test
    fun `defaults disable theme auto-sync`() {
        // PRIVACY.md "defaults with a spine": ambient behaviour is opt-in. The
        // default matches the render store (ThemeStore.autoSyncEnabled = false)
        // so the settings screen can never silently enable launcher-following.
        assertFalse(SettingsStore.defaults().autoSyncTheme)
    }

    @Test
    fun `defaults use the AMOLED Night preset and Space Mono font`() {
        // DESIGN.md:27 AMOLED black is the brand default; DESIGN.md:29 Space
        // Mono is the lead chrome font. WS12 T2 exposes and persists both.
        // The preset is the RENDER path's enum (theme package), so its default
        // matches ThemeStore/ThemeController's ground.
        assertEquals(ThemePreset.AMOLED_NIGHT, SettingsStore.defaults().themePreset)
        assertEquals(ThemePreset.DEFAULT, SettingsStore.defaults().themePreset)
        assertEquals(FontMode.SPACE_MONO, SettingsStore.defaults().fontMode)
    }

    @Test
    fun `the store's theme preset IS the render path's enum`() {
        // H4 regression lock: there must be exactly one ThemePreset. The
        // settings store holds the rich theme-package enum (key/displayName/
        // background/isDark), not a shadow copy — otherwise the picker would be
        // decorative and the renderer would never see it.
        val store = SettingsStore(themePreset = ThemePreset.BURGUNDY)
        assertEquals("burgundy", store.themePreset.key)
        assertEquals("Burgundy", store.themePreset.displayName)
        assertTrue(store.themePreset.isDark)
    }

    @Test
    fun `store holds the chosen settings`() {
        val store = SettingsStore(
            lockScreenPrivacy = LockScreenPrivacy.CONTENT,
            alertStyle = AlertStyle.SILENT,
            autoSyncTheme = false,
            themePreset = ThemePreset.BURGUNDY,
            fontMode = FontMode.JETBRAINS_MONO,
        )
        assertEquals(LockScreenPrivacy.CONTENT, store.lockScreenPrivacy)
        assertEquals(AlertStyle.SILENT, store.alertStyle)
        assertFalse(store.autoSyncTheme)
        assertEquals(ThemePreset.BURGUNDY, store.themePreset)
        assertEquals(FontMode.JETBRAINS_MONO, store.fontMode)
    }

    @Test
    fun `data class equality round-trips the stored values`() {
        // The store is a plain data class so it round-trips through the backup
        // format (WS12 T4) unchanged: serialise/deserialise reproduces the same
        // value object.
        val store = SettingsStore(
            lockScreenPrivacy = LockScreenPrivacy.NOTHING,
            alertStyle = AlertStyle.VIBRATE,
            autoSyncTheme = false,
            themePreset = ThemePreset.PAPER,
            fontMode = FontMode.JETBRAINS_MONO,
        )
        assertEquals(
            store,
            SettingsStore(
                store.lockScreenPrivacy,
                store.alertStyle,
                store.autoSyncTheme,
                store.themePreset,
                store.fontMode,
            ),
        )
    }

    // ---- Font mode (carried over from the deleted shadow ui/ThemePresetTest) ----

    @Test
    fun `font mode defaults to Space Mono`() {
        // DESIGN.md:29 Space Mono is the lead chrome font.
        assertEquals(FontMode.SPACE_MONO, FontMode.defaults())
    }

    @Test
    fun `font mode toggles between the two mono fonts`() {
        assertEquals(
            setOf(FontMode.SPACE_MONO, FontMode.JETBRAINS_MONO),
            FontMode.values().toSet(),
        )
        assertEquals(2, FontMode.values().size)
    }
}
