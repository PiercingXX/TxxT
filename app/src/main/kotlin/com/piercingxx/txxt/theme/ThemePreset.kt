package com.piercingxx.txxt.theme

import kotlin.math.roundToInt

/**
 * The seven named background presets TxxT ships, mirroring the xx-launcher's
 * theme set (brand guide §3.3). Pure Kotlin — no `android.*` imports — so the
 * model and its token derivation are JVM-testable without a device.
 *
 * Names and background values are the brand's own (§3.3), reused verbatim so
 * theme auto-sync with the launcher can match by name.
 */
enum class ThemePreset(
    /** Stable identifier used in the launcher broadcast and persisted settings. */
    val key: String,
    /** Display name, e.g. "AMOLED Night". */
    val displayName: String,
    /** Background color as a 0xAARRGGBB long. */
    val background: Long,
    /** Whether the preset is a dark theme. */
    val isDark: Boolean,
) {
    AMOLED_NIGHT("amoled-night", "AMOLED Night", 0xFF000000, true),
    GRAPHITE("graphite", "Graphite", 0xFF131316, true),
    FOREST_NIGHT("forest-night", "Forest Night", 0xFF10261B, true),
    OCEAN_DRIFT("ocean-drift", "Ocean Drift", 0xFF0F1C2E, true),
    BURGUNDY("burgundy", "Burgundy", 0xFF2A1018, true),
    PAPER("paper", "Paper", 0xFFF3EEE2, false),
    MIST("mist", "Mist", 0xFFE6EDF5, false);

    companion object {
        /** The default preset (AMOLED Night — the brand's default ground). */
        val DEFAULT: ThemePreset = AMOLED_NIGHT

        /**
         * Resolve a preset by its stable [key]. Returns null for an unknown
         * key so callers can fall back to [DEFAULT] without throwing.
         */
        fun fromKey(key: String?): ThemePreset? =
            entries.firstOrNull { it.key == key }

        /** Resolve a preset by its display name (case-insensitive). */
        fun fromDisplayName(name: String?): ThemePreset? =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

/**
 * The full set of semantic color tokens a theme drives. Colors are 0xAARRGGBB
 * longs so the model stays pure Kotlin; the Android layer (T2 resources, T6
 * applier) maps them onto `Color`/resources.
 *
 * Derived from a [ThemePreset]'s background via [deriveTokens] following the
 * brand rules: a white or black opacity ramp carries hierarchy, the signal
 * accent is pure white with ink text on it (inverted emphasis).
 */
data class ThemeTokens(
    /** Ground / background color. */
    val background: Long,
    /** Raised surfaces (cards, wells) — the background nudged toward white (dark) or black (light). */
    val surface: Long,
    /** Body text — the ceiling for type (90% of the foreground). */
    val text: Long,
    /** Secondary text (50% of the foreground). */
    val muted: Long,
    /** Hairline borders (10% of the foreground). */
    val line: Long,
    /** The signal accent — pure white, reserved. */
    val accent: Long,
    /** Text on an accent block (inverted emphasis) — ink on signal. */
    val accentOn: Long,
    /** True for dark themes, false for light. */
    val isDark: Boolean,
) {
    companion object {
        /** Pure white — the brand's reserved signal accent. */
        const val SIGNAL: Long = 0xFFFFFFFFL
        /** Ink black — text on a signal block. */
        const val INK: Long = 0xFF000000L
    }
}

// Foreground opacity stops (white on dark, black on light).
private const val TEXT_STOP = 0xE6 // 90%
private const val MUTED_STOP = 0x80 // 50%
private const val LINE_STOP = 0x1A // 10%

// How far the surface is nudged from the background toward the foreground
// (4%) to read as a raised plane.
private const val SURFACE_STEP = 0.04f

/**
 * Derive the full [ThemeTokens] for [preset] from its background color.
 *
 * Dark presets ramp white over the background; light presets ramp black (the
 * brand guide §3.2's "transparency ramps of white-on-black (and the inverse)").
 * The signal accent is always pure white with ink text on it.
 */
fun deriveTokens(preset: ThemePreset): ThemeTokens {
    val fg = if (preset.isDark) 0xFFL else 0x00L // white foreground on dark, black on light
    return ThemeTokens(
        background = preset.background,
        surface = if (preset.isDark) {
            lighten(preset.background, SURFACE_STEP)
        } else {
            darken(preset.background, SURFACE_STEP)
        },
        text = withAlpha(fg, TEXT_STOP),
        muted = withAlpha(fg, MUTED_STOP),
        line = withAlpha(fg, LINE_STOP),
        accent = ThemeTokens.SIGNAL,
        accentOn = ThemeTokens.INK,
        isDark = preset.isDark,
    )
}

/** Mix [color] toward white by [fraction] (0..1), preserving alpha. */
private fun lighten(color: Long, fraction: Float): Long = mix(color, 0xFFFFFFFFL, fraction)

/** Mix [color] toward black by [fraction] (0..1), preserving alpha. */
private fun darken(color: Long, fraction: Float): Long = mix(color, 0xFF000000L, fraction)

/** Linearly interpolate [color] toward [target] by [fraction] (0..1). */
private fun mix(color: Long, target: Long, fraction: Float): Long {
    val a = (color ushr 24) and 0xFF
    val r = ((color ushr 16) and 0xFF).toFloat()
    val g = ((color ushr 8) and 0xFF).toFloat()
    val b = (color and 0xFF).toFloat()
    val tr = ((target ushr 16) and 0xFF).toFloat()
    val tg = ((target ushr 8) and 0xFF).toFloat()
    val tb = (target and 0xFF).toFloat()
    val nr = (r + (tr - r) * fraction).roundToInt()
    val ng = (g + (tg - g) * fraction).roundToInt()
    val nb = (b + (tb - b) * fraction).roundToInt()
    return ((a.toLong() shl 24) or (nr.toLong() shl 16) or (ng.toLong() shl 8) or nb.toLong())
}

/** Build an ARGB color from a foreground base (0xFF or 0x00) and an alpha stop. */
private fun withAlpha(base: Long, alpha: Int): Long =
    ((alpha.toLong() shl 24) or (base shl 16) or (base shl 8) or base)