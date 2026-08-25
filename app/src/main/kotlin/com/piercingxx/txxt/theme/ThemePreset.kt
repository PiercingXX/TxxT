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

    /**
     * The [ThemeGround] this preset resolves to — its own background and its
     * own dark/light classification, tagged with its stable [key].
     *
     * Exists so a named preset and the launcher's "Custom" ground are the SAME
     * shape downstream: the store, the controller and the applier only ever
     * speak [ThemeGround], which is what lets Custom flow through a pipeline
     * that was originally enum-typed without the enum growing an eighth entry
     * that has no colour of its own.
     */
    val ground: ThemeGround
        get() = ThemeGround(background, isDark, key)

    companion object {
        /** The default preset (AMOLED Night — the brand's default ground). */
        val DEFAULT: ThemePreset = AMOLED_NIGHT

        /**
         * Resolve a preset by its stable [key]. Returns null for an unknown
         * key so callers can fall back to [DEFAULT] without throwing.
         */
        fun fromKey(key: String?): ThemePreset? =
            entries.firstOrNull { it.key == key }

        /**
         * Resolve a preset by its display name (case-insensitive).
         *
         * Returns null for anything unrecognised — the launcher's "Custom"
         * included. Custom deliberately has no entry here: it carries no
         * colour of its own, so it is honoured through the broadcast's
         * background extra instead (see [resolveSyncedTheme]).
         */
        fun fromDisplayName(name: String?): ThemePreset? =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

/**
 * Display name the xx-launcher broadcasts for a user-picked custom ground —
 * the family's eighth theme, matched case-insensitively because the name
 * crossed a process boundary and a casing tweak on the launcher side must not
 * silently break sync.
 */
const val CUSTOM_THEME_NAME = "Custom"

/**
 * Stable persisted key standing in for a Custom ground.
 *
 * Deliberately NOT a [ThemePreset] entry (the family-wide choice — see
 * xx-clock's `CUSTOM_PRESET_KEY` and xx-phone's `ThemeGroundStore.KEY_CUSTOM`):
 * Custom has no ground of its own, it always resolves through a remembered
 * background plus the contrast rule. Keeping it out of the enum is also what
 * keeps the seven brand presets and their colours untouched, and what makes
 * [ThemePreset.fromKey] return null for a Custom key — see
 * [ThemeStore.lastLauncherTheme] for why that null is the right answer.
 */
const val CUSTOM_PRESET_KEY = "custom"

/**
 * Perceived luminance of a 0xAARRGGBB colour per the family-wide contrast
 * rule: `0.299 r + 0.587 g + 0.114 b` (0..255).
 */
fun luminance(argb: Long): Double {
    val r = ((argb ushr 16) and 0xFF).toDouble()
    val g = ((argb ushr 8) and 0xFF).toDouble()
    val b = (argb and 0xFF).toDouble()
    return 0.299 * r + 0.587 * g + 0.114 * b
}

/**
 * Family-wide contrast rule, identical in every sibling app and in the
 * launcher: a ground with luminance strictly above 182 takes the dark
 * (near-black) foreground; anything darker takes white.
 *
 * The threshold is copied verbatim rather than re-derived on purpose — a
 * different cut-off would put two family apps on opposite sides of the
 * decision for the same mid-tone custom ground.
 */
fun prefersDarkForeground(background: Long): Boolean = luminance(background) > 182.0

/**
 * The GROUND a theme resolves to: the exact background to paint plus whether
 * the app should wear its dark look (white foreground ramp) or its light one
 * (black ramp).
 *
 * This — not [ThemePreset] — is the currency of the render path, because the
 * launcher can broadcast a ground that belongs to no named preset. A named
 * preset's ground carries that preset's [ThemePreset.key]; a Custom ground
 * carries [CUSTOM_PRESET_KEY]. Pure Kotlin, so the whole resolution rule stays
 * JVM-testable.
 */
data class ThemeGround(
    /** Background (ground) colour as a 0xAARRGGBB long. */
    val background: Long,
    /** True → dark look (white ramp); false → light look (black ramp). */
    val isDark: Boolean,
    /** Stable persisted key: a [ThemePreset.key], or [CUSTOM_PRESET_KEY]. */
    val presetKey: String,
)

/**
 * The ground an arbitrary (Custom) [background] resolves to.
 *
 * The dark/light flag is *derived*, never carried: the launcher sends a colour
 * and nothing else, so the contrast rule is the only thing standing between a
 * pale custom ground and white-on-white text. Deriving it here is what makes a
 * Custom ground legible on the very first broadcast, with no second round-trip.
 */
fun customGround(background: Long): ThemeGround =
    ThemeGround(background, isDark = !prefersDarkForeground(background), presetKey = CUSTOM_PRESET_KEY)

/**
 * Resolve an xx-launcher theme broadcast's payload to the [ThemeGround] it
 * means, or null when the payload says nothing actionable.
 *
 * Three cases, matching every sibling app's receiver verbatim so the family
 * never disagrees about what a broadcast meant:
 *  - a named preset resolves to its own ground and classification;
 *  - [CUSTOM_THEME_NAME] resolves through [backgroundExtra] plus the contrast
 *    rule ([customGround]);
 *  - anything else — an unknown name, or a Custom broadcast that carried no
 *    background — resolves to null. Null means *persist nothing*: keeping the
 *    ground the user already has beats guessing at a colour the launcher
 *    never sent.
 *
 * The name is trimmed before matching because it crossed a process boundary.
 */
fun resolveSyncedTheme(displayName: String?, backgroundExtra: Long?): ThemeGround? {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    ThemePreset.fromDisplayName(name)?.let { return it.ground }
    if (name.equals(CUSTOM_THEME_NAME, ignoreCase = true) && backgroundExtra != null) {
        return customGround(backgroundExtra)
    }
    return null
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
 * Derive the full [ThemeTokens] for [ground] from its background color.
 *
 * Dark grounds ramp white over the background; light grounds ramp black (the
 * brand guide §3.2's "transparency ramps of white-on-black (and the inverse)").
 * The signal accent is always pure white with ink text on it.
 *
 * Takes a [ThemeGround] rather than a [ThemePreset] so the launcher's Custom
 * ground — which belongs to no preset — derives through exactly the same
 * ramps, and therefore gets the same legible text hierarchy as the seven
 * named presets rather than a second-class fallback.
 */
fun deriveTokens(ground: ThemeGround): ThemeTokens {
    val fg = if (ground.isDark) 0xFFL else 0x00L // white foreground on dark, black on light
    return ThemeTokens(
        background = ground.background,
        surface = if (ground.isDark) {
            lighten(ground.background, SURFACE_STEP)
        } else {
            darken(ground.background, SURFACE_STEP)
        },
        text = withAlpha(fg, TEXT_STOP),
        muted = withAlpha(fg, MUTED_STOP),
        line = withAlpha(fg, LINE_STOP),
        accent = ThemeTokens.SIGNAL,
        accentOn = ThemeTokens.INK,
        isDark = ground.isDark,
    )
}

/**
 * Derive the full [ThemeTokens] for a named [preset] — its ground run through
 * the same [deriveTokens] ramps. Kept as its own overload because the seven
 * named presets are what most callers and tests speak.
 */
fun deriveTokens(preset: ThemePreset): ThemeTokens = deriveTokens(preset.ground)

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