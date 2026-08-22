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
}