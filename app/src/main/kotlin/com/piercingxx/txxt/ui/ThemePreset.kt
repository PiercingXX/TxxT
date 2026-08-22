package com.piercingxx.txxt.ui

/**
 * The seven named theme presets (WS12 T2).
 *
 * The brand guide §3.3 names seven presets (docs/FEATURES.md:58-59): AMOLED
 * Night, Graphite, Forest Night, Ocean Drift, Burgundy, Paper, Mist. WS12
 * exposes and persists the chosen preset; the actual rendering of each preset
 * is WS14's scope (contracts/TxxT.md:285-297).
 *
 * Pure Kotlin with zero `android.*` imports so the model is JVM-testable,
 * mirroring [SettingsStore].
 */
enum class ThemePreset {
    /** True #000000 background for the OLED panel — the brand default. */
    AMOLED_NIGHT,

    /** Neutral graphite. */
    GRAPHITE,

    /** Dark forest green. */
    FOREST_NIGHT,

    /** Blue ocean drift. */
    OCEAN_DRIFT,

    /** Deep burgundy. */
    BURGUNDY,

    /** Light paper. */
    PAPER,

    /** Soft mist. */
    MIST,
    ;

    companion object {
        /** The factory default for a fresh install — the brand's AMOLED black. */
        fun defaults() = AMOLED_NIGHT
    }
}