package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {

    // ---- The seven presets exist with the brand's names and backgrounds ----

    @Test
    fun `all seven brand presets are present with correct backgrounds`() {
        val expected = mapOf(
            "AMOLED Night" to 0xFF000000L,
            "Graphite" to 0xFF131316L,
            "Forest Night" to 0xFF10261BL,
            "Ocean Drift" to 0xFF0F1C2EL,
            "Burgundy" to 0xFF2A1018L,
            "Paper" to 0xFFF3EEE2L,
            "Mist" to 0xFFE6EDF5L,
        )
        assertEquals(expected.size, ThemePreset.entries.size)
        for ((name, bg) in expected) {
            val preset = ThemePreset.fromDisplayName(name)
            assertTrue("preset $name should exist", preset != null)
            assertEquals("background of $name", bg, preset!!.background)
        }
    }

    @Test
    fun `dark presets are flagged dark and light presets light`() {
        assertTrue(ThemePreset.AMOLED_NIGHT.isDark)
        assertTrue(ThemePreset.GRAPHITE.isDark)
        assertTrue(ThemePreset.FOREST_NIGHT.isDark)
        assertTrue(ThemePreset.OCEAN_DRIFT.isDark)
        assertTrue(ThemePreset.BURGUNDY.isDark)
        assertFalse(ThemePreset.PAPER.isDark)
        assertFalse(ThemePreset.MIST.isDark)
    }

    // ---- Lookup by key (the launcher broadcast / persisted channel) ----

    @Test
    fun `fromKey resolves every preset by its stable key`() {
        for (preset in ThemePreset.entries) {
            assertEquals(preset, ThemePreset.fromKey(preset.key))
        }
    }

    @Test
    fun `fromKey returns null for an unknown key`() {
        assertNull(ThemePreset.fromKey("no-such-theme"))
        assertNull(ThemePreset.fromKey(null))
    }

    @Test
    fun `default preset is AMOLED Night`() {
        assertEquals(ThemePreset.AMOLED_NIGHT, ThemePreset.DEFAULT)
    }

    // ---- Token derivation: dark presets ramp white over the background ----

    @Test
    fun `AMOLED Night derives white-on-black tokens`() {
        val t = deriveTokens(ThemePreset.AMOLED_NIGHT)
        assertEquals(0xFF000000L, t.background)
        assertEquals(0xFF0A0A0AL, t.surface) // black nudged 4% toward white
        assertEquals(0xE6FFFFFFL, t.text) // 90% white
        assertEquals(0x80FFFFFFL, t.muted) // 50% white
        assertEquals(0x1AFFFFFFL, t.line) // 10% white
        assertEquals(0xFFFFFFFFL, t.accent) // signal
        assertEquals(0xFF000000L, t.accentOn) // ink on signal
        assertTrue(t.isDark)
    }

    @Test
    fun `Graphite derives white-on-graphite tokens`() {
        val t = deriveTokens(ThemePreset.GRAPHITE)
        assertEquals(0xFF131316L, t.background)
        assertEquals(0xFF1C1C1FL, t.surface)
        assertEquals(0xE6FFFFFFL, t.text)
        assertEquals(0x80FFFFFFL, t.muted)
        assertEquals(0x1AFFFFFFL, t.line)
        assertTrue(t.isDark)
    }

    @Test
    fun `Forest Night derives white-on-forest tokens`() {
        val t = deriveTokens(ThemePreset.FOREST_NIGHT)
        assertEquals(0xFF10261BL, t.background)
        assertEquals(0xFF1A2F24L, t.surface)
        assertEquals(0xE6FFFFFFL, t.text)
        assertTrue(t.isDark)
    }

    @Test
    fun `Ocean Drift derives white-on-ocean tokens`() {
        val t = deriveTokens(ThemePreset.OCEAN_DRIFT)
        assertEquals(0xFF0F1C2EL, t.background)
        assertEquals(0xFF192536L, t.surface)
        assertEquals(0xE6FFFFFFL, t.text)
        assertTrue(t.isDark)
    }

    @Test
    fun `Burgundy derives white-on-burgundy tokens`() {
        val t = deriveTokens(ThemePreset.BURGUNDY)
        assertEquals(0xFF2A1018L, t.background)
        assertEquals(0xFF331A21L, t.surface)
        assertEquals(0xE6FFFFFFL, t.text)
        assertTrue(t.isDark)
    }

    // ---- Token derivation: light presets ramp black over the background ----

    @Test
    fun `Paper derives black-on-paper tokens`() {
        val t = deriveTokens(ThemePreset.PAPER)
        assertEquals(0xFFF3EEE2L, t.background)
        assertEquals(0xFFE9E4D9L, t.surface) // paper darkened 4% toward black
        assertEquals(0xE6000000L, t.text) // 90% black
        assertEquals(0x80000000L, t.muted) // 50% black
        assertEquals(0x1A000000L, t.line) // 10% black
        assertEquals(0xFFFFFFFFL, t.accent) // signal stays white
        assertEquals(0xFF000000L, t.accentOn) // ink on signal
        assertFalse(t.isDark)
    }

    @Test
    fun `Mist derives black-on-mist tokens`() {
        val t = deriveTokens(ThemePreset.MIST)
        assertEquals(0xFFE6EDF5L, t.background)
        assertEquals(0xFFDDE4EBL, t.surface)
        assertEquals(0xE6000000L, t.text)
        assertEquals(0x80000000L, t.muted)
        assertEquals(0x1A000000L, t.line)
        assertFalse(t.isDark)
    }

    // ---- Derivation invariants across every preset ----

    @Test
    fun `every preset derives a full token set with the signal accent`() {
        for (preset in ThemePreset.entries) {
            val t = deriveTokens(preset)
            assertEquals("background of $preset", preset.background, t.background)
            assertEquals("isDark of $preset", preset.isDark, t.isDark)
            assertEquals("signal accent of $preset", ThemeTokens.SIGNAL, t.accent)
            assertEquals("ink on signal of $preset", ThemeTokens.INK, t.accentOn)
            // The surface must differ from the background (it is a raised plane).
            assertTrue("surface of $preset should differ from its background", t.surface != t.background)
        }
    }

    // ---- The family's eighth theme: Custom (no enum entry, resolved by colour) ----

    @Test
    fun `Custom is not a preset — it has no colour of its own`() {
        // The whole point of the design: Custom must NOT join the enum, or the
        // seven brand presets would gain an eighth with no background to name.
        assertNull(ThemePreset.fromDisplayName(CUSTOM_THEME_NAME))
        assertNull(ThemePreset.fromKey(CUSTOM_PRESET_KEY))
        assertEquals(7, ThemePreset.entries.size)
    }

    @Test
    fun `every preset's ground carries its own colour, flag and key`() {
        for (preset in ThemePreset.entries) {
            val ground = preset.ground
            assertEquals("background of $preset", preset.background, ground.background)
            assertEquals("isDark of $preset", preset.isDark, ground.isDark)
            assertEquals("key of $preset", preset.key, ground.presetKey)
        }
    }

    @Test
    fun `the contrast rule cuts at luminance 182 — the family-wide threshold`() {
        // Every sibling app and the launcher use this exact cut-off; a
        // different one would put two family apps on opposite sides of the
        // decision for the same mid-tone ground.
        assertFalse(prefersDarkForeground(0xFF000000L))
        assertFalse(prefersDarkForeground(ThemePreset.GRAPHITE.background))
        assertTrue(prefersDarkForeground(0xFFFFFFFFL))
        assertTrue(prefersDarkForeground(ThemePreset.PAPER.background))
        assertTrue(prefersDarkForeground(ThemePreset.MIST.background))
        // 0xFFB6B6B6 has luminance 182.0 exactly — strictly above only.
        assertFalse(prefersDarkForeground(0xFFB6B6B6L))
        assertTrue(prefersDarkForeground(0xFFB7B7B7L))
    }

    @Test
    fun `a light custom ground derives legible black-on-light tokens`() {
        // The bug this guards: a pale custom ground painted with the dark
        // theme's white ramp would be white-on-white — illegible on the very
        // first broadcast, with no second broadcast coming to fix it.
        val t = deriveTokens(customGround(0xFFEEDDCCL))
        assertEquals(0xFFEEDDCCL, t.background)
        assertEquals(0xE6000000L, t.text) // 90% black
        assertEquals(0x80000000L, t.muted)
        assertEquals(0x1A000000L, t.line)
        assertFalse(t.isDark)
    }

    @Test
    fun `a dark custom ground derives white-on-dark tokens`() {
        val t = deriveTokens(customGround(0xFF203040L))
        assertEquals(0xFF203040L, t.background)
        assertEquals(0xE6FFFFFFL, t.text) // 90% white
        assertTrue(t.isDark)
    }

    @Test
    fun `a custom ground is tagged with the custom key, never a preset key`() {
        val ground = customGround(0xFF123456L)
        assertEquals(CUSTOM_PRESET_KEY, ground.presetKey)
        assertNull(ThemePreset.fromKey(ground.presetKey))
    }

    // ---- resolveSyncedTheme: the broadcast payload → ground decision ----

    @Test
    fun `resolveSyncedTheme resolves every named preset by display name`() {
        for (preset in ThemePreset.entries) {
            // The background extra is present on every real broadcast; a named
            // preset must still resolve to its OWN colour, not the carried one.
            assertEquals(preset.ground, resolveSyncedTheme(preset.displayName, 0xFF00FF00L))
            assertEquals(preset.ground, resolveSyncedTheme(preset.displayName, null))
        }
    }

    @Test
    fun `resolveSyncedTheme resolves Custom through the carried background`() {
        assertEquals(customGround(0xFFEEDDCCL), resolveSyncedTheme("Custom", 0xFFEEDDCCL))
        // Case- and whitespace-tolerant: the name crossed a process boundary.
        assertEquals(customGround(0xFF203040L), resolveSyncedTheme("custom", 0xFF203040L))
        assertEquals(customGround(0xFF203040L), resolveSyncedTheme("  CUSTOM  ", 0xFF203040L))
    }

    @Test
    fun `resolveSyncedTheme ignores a Custom broadcast with no background`() {
        // Nothing sensible to paint — keeping the ground the user already has
        // beats guessing a colour the launcher never sent (sibling-wide rule).
        assertNull(resolveSyncedTheme("Custom", null))
    }

    @Test
    fun `resolveSyncedTheme ignores an unknown or empty name`() {
        assertNull(resolveSyncedTheme("Not A Real Preset", 0xFF112233L))
        assertNull(resolveSyncedTheme(null, 0xFF112233L))
        assertNull(resolveSyncedTheme("", 0xFF112233L))
        assertNull(resolveSyncedTheme("   ", 0xFF112233L))
    }
}