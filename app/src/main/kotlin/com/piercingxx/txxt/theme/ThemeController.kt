package com.piercingxx.txxt.theme

/**
 * Coordinator for the app's theme decisions: the single place the settings
 * screen (WS12) and the launcher-sync receiver (T5) report their intent, and
 * the place the applier (T6) reads the final choice from.
 *
 * Owns the manual-wins precedence rule end to end (PRIVACY.md §7 "explicit
 * beats ambient"): a theme the user picks in-app always overrides whatever the
 * launcher broadcasts. It remembers the last launcher theme it was told about —
 * in memory AND persisted through the store — so the report survives process
 * death: [onLauncherTheme] writes both, and [effectiveTheme] falls back to
 * [ThemeStore.lastLauncherTheme] when this instance has not been told directly.
 * A receiver can therefore report a broadcast into one (short-lived) controller
 * and a controller constructed later — e.g. the thread screen's — still
 * resolves the launcher's choice; the applier just reads [effectiveTheme].
 */
class ThemeController(
    private val store: ThemeStore,
) {
    /**
     * The last GROUND this instance was told about by the launcher broadcast
     * (T5's receiver), or null. A ground rather than a preset because the
     * launcher's Custom theme is a raw colour with no [ThemePreset] entry —
     * this is the field that carries it. Durable reports live in
     * [ThemeStore.lastLauncherGround] and surface through [effectiveGround].
     */
    var launcherGround: ThemeGround? = null
        private set

    /**
     * The last launcher theme this instance was told about *as a named
     * preset*, or null — which is also the honest answer for a Custom
     * broadcast (see [ThemeStore.lastLauncherTheme]).
     */
    val launcherTheme: ThemePreset?
        get() = ThemePreset.fromKey(launcherGround?.presetKey)

    /**
     * The GROUND that should actually paint the UI right now — what the
     * applier reads. Applies the manual-wins precedence unchanged: a manual
     * pick wins; otherwise the in-memory or durably-persisted launcher ground
     * applies when auto-sync is on; else the default ground.
     */
    val effectiveGround: ThemeGround
        get() = store.effectiveGround(launcherGround ?: store.lastLauncherGround)

    /**
     * The effective theme in its preset-shaped view, applying the same
     * manual-wins precedence. Resolves to the default ground whenever the
     * winning theme has no [ThemePreset] entry (a Custom broadcast); the
     * render path reads [effectiveGround] instead, which carries the custom
     * colour faithfully.
     */
    val effectiveTheme: ThemePreset
        get() = store.effectiveTheme(launcherTheme ?: store.lastLauncherTheme)

    /** The theme the user picked in-app; [ThemePreset.DEFAULT] until they pick one. */
    val manualTheme: ThemePreset
        get() = store.manualTheme

    /** Whether TxxT follows the launcher's active theme. Off by default. */
    val autoSyncEnabled: Boolean
        get() = store.autoSyncEnabled

    /**
     * The user picked [preset] in-app. A manual pick always wins over auto-sync
     * (PRIVACY.md §7 "explicit beats ambient").
     */
    fun setManualTheme(preset: ThemePreset) {
        store.manualTheme = preset
    }

    /** Turn following the launcher's active theme on/off. */
    fun setAutoSync(enabled: Boolean) {
        store.autoSyncEnabled = enabled
    }

    /**
     * The launcher broadcast reported [preset] as active (T5's receiver).
     * Convenience over [onLauncherGround] for a named preset.
     */
    fun onLauncherTheme(preset: ThemePreset) {
        onLauncherGround(preset.ground)
    }

    /**
     * The launcher broadcast reported [ground] as active and TxxT should follow
     * it (T5's receiver). Applies the synced launcher theme through the
     * manual-wins precedence rule: the ground is recorded in memory AND
     * persisted through the store, so a later controller — or the applier —
     * resolves it via [effectiveGround] exactly as [onLauncherGround] dictates.
     * This is the single entry point the theme-sync receiver calls.
     */
    fun applySyncedTheme(ground: ThemeGround) {
        onLauncherGround(ground)
    }

    /**
     * The launcher broadcast reported [ground] as active (T5's receiver) —
     * either a named preset's ground or the family's Custom colour.
     *
     * Records it in memory for this instance AND persists it through the store,
     * so the report is durable across process death and visible to any
     * controller constructed later over the same backing store. This is the
     * single entry point for launcher reports: routing named presets through
     * it too is what keeps the persisted record whole, whichever kind of
     * broadcast lands last.
     */
    fun onLauncherGround(ground: ThemeGround) {
        launcherGround = ground
        store.lastLauncherGround = ground
    }
}