package com.piercingxx.txxt.theme

import android.content.SharedPreferences

/**
 * Key-value persistence backing [ThemeStore].
 *
 * Abstracted behind this interface so the store's logic is JVM-testable without
 * a device or Robolectric (which is not in the offline cache) — the test injects
 * an in-memory fake, while the app wires [SharedPreferencesThemeKeyValueStore]
 * at the call site. The keys are opaque to callers; only [ThemeStore] reads them.
 */
interface ThemeKeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

/**
 * [ThemeKeyValueStore] backed by Android [SharedPreferences].
 */
class SharedPreferencesThemeKeyValueStore(
    private val prefs: SharedPreferences,
) : ThemeKeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

/**
 * Persists the user's theme choices across launches.
 *
 * Two independent settings, exactly the two the settings screen (WS12) exposes
 * and the theme controller (T4) reads:
 *
 *  - [manualTheme] — the theme the user picked in-app. Defaults to
 *    [ThemePreset.DEFAULT] (AMOLED Night). When the user picks one, a manual
 *    in-app theme wins over auto-sync (PRIVACY.md §7 "explicit beats ambient");
 *    that precedence rule lives in ThemeController (T4), which reads this.
 *  - [autoSyncEnabled] — whether TxxT follows the launcher's active theme via
 *    the broadcast receiver (T5). Defaults to **off** (privacy by default,
 *    PRIVACY.md §7), so the app never starts following the launcher until the
 *    user opts in.
 *
 * Values are stored under stable keys (the preset's [ThemePreset.key], the
 * toggle as a boolean) so a stored setting survives an app update and the
 * launcher broadcast can match by name.
 */
class ThemeStore(
    private val kv: ThemeKeyValueStore,
) {
    /** The theme the user selected in-app; [ThemePreset.DEFAULT] until they pick one. */
    var manualTheme: ThemePreset
        get() = ThemePreset.fromKey(kv.getString(KEY_MANUAL_THEME)) ?: ThemePreset.DEFAULT
        set(value) = kv.putString(KEY_MANUAL_THEME, value.key)

    /** Whether TxxT follows the launcher's active theme. Off by default. */
    var autoSyncEnabled: Boolean
        get() = kv.getBoolean(KEY_AUTO_SYNC, false)
        set(value) = kv.putBoolean(KEY_AUTO_SYNC, value)

    /**
     * The theme that should actually drive the UI, applying the manual-wins
     * precedence: a manual in-app theme overrides auto-sync. When the user has
     * set a manual theme (different from the default) it wins; otherwise the
     * launcher-synced [launcherTheme] applies when auto-sync is on, else the
     * default.
     *
     * @param launcherTheme the launcher's active theme (from T5's receiver), or
     *   null when none is known.
     */
    fun effectiveTheme(launcherTheme: ThemePreset?): ThemePreset {
        val manual = manualTheme
        if (manual != ThemePreset.DEFAULT) return manual
        if (autoSyncEnabled && launcherTheme != null) return launcherTheme
        return ThemePreset.DEFAULT
    }

    companion object {
        const val KEY_MANUAL_THEME = "theme_manual"
        const val KEY_AUTO_SYNC = "theme_auto_sync"
    }
}