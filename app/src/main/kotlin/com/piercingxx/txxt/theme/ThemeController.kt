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
     * The last theme THIS instance was told about by the launcher broadcast
     * (T5's receiver), or null. Durable reports live in
     * [ThemeStore.lastLauncherTheme] and surface through [effectiveTheme].
     */
    var launcherTheme: ThemePreset? = null
        private set

    /**
     * The theme that should actually drive the UI right now, applying the
     * manual-wins precedence: a manual pick wins; otherwise the in-memory or
     * durably-persisted launcher theme applies when auto-sync is on; else the
     * default ground.
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
     * Records it in memory for this instance AND persists it through the store,
     * so the report is durable across process death and visible to any
     * controller constructed later over the same backing store.
     */
    fun onLauncherTheme(preset: ThemePreset) {
        launcherTheme = preset
        store.lastLauncherTheme = preset
    }
}