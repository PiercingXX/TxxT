package com.piercingxx.txxt.theme

/**
 * Coordinator for the app's theme decisions: the single place the settings
 * screen (WS12) and the launcher-sync receiver (T5) report their intent, and
 * the place the applier (T6) reads the final choice from.
 *
 * Owns the manual-wins precedence rule end to end (PRIVACY.md §7 "explicit
 * beats ambient"): a theme the user picks in-app always overrides whatever the
 * launcher broadcasts. It remembers the last launcher theme it was told about,
 * so callers resolve the effective theme without threading that value through
 * every call — the receiver reports it once via [onLauncherTheme], and the
 * applier just reads [effectiveTheme].
 */
class ThemeController(
    private val store: ThemeStore,
) {
    /** The last theme the launcher broadcast (T5's receiver) reported, or null. */
    var launcherTheme: ThemePreset? = null
        private set

    /**
     * The theme that should actually drive the UI right now, applying the
     * manual-wins precedence via [ThemeStore.effectiveTheme].
     */
    val effectiveTheme: ThemePreset
        get() = store.effectiveTheme(launcherTheme)

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

    /** The launcher broadcast reported [preset] as active (T5's receiver). */
    fun onLauncherTheme(preset: ThemePreset) {
        launcherTheme = preset
    }
}