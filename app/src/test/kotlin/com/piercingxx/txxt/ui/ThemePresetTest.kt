package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePresetTest {

    @Test
    fun `defaults to AMOLED Night`() {
        // DESIGN.md:27 AMOLED black is the brand default aesthetic.
        assertEquals(ThemePreset.AMOLED_NIGHT, ThemePreset.defaults())
    }

    @Test
    fun `exposes all seven named presets`() {
        // The brand guide §3.3 names exactly seven (docs/FEATURES.md:58-59).
        assertEquals(
            setOf(
                ThemePreset.AMOLED_NIGHT,
                ThemePreset.GRAPHITE,
                ThemePreset.FOREST_NIGHT,
                ThemePreset.OCEAN_DRIFT,
                ThemePreset.BURGUNDY,
                ThemePreset.PAPER,
                ThemePreset.MIST,
            ),
            ThemePreset.values().toSet(),
        )
        assertEquals(7, ThemePreset.values().size)
    }

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