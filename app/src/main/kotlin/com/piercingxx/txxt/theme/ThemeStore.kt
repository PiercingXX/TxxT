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
 * Three independent settings, exactly the ones the settings screen (WS12) and
 * the theme controller (T4) read:
 *
 *  - [manualTheme] — the theme the user picked in-app. Defaults to
 *    [ThemePreset.DEFAULT] (AMOLED Night). When the user picks one, a manual
 *    in-app theme wins over auto-sync (PRIVACY.md §7 "explicit beats ambient");
 *    that precedence rule lives in ThemeController (T4), which reads this.
 *  - [autoSyncEnabled] — whether TxxT follows the launcher's active theme via
 *    the broadcast receiver (T5). Defaults to **on** (the family contract), so
 *    a fresh install follows the launcher until the user opts out.
 *  - [lastLauncherGround] / [lastLauncherTheme] — the launcher's most recently
 *    broadcast ground, persisted so a receiver's report survives process death
 *    and is visible to controllers constructed later (e.g. the thread
 *    screen's). Stored as a ground, not merely a preset key, because the
 *    family's eighth theme — Custom — is a raw colour that exists nowhere but
 *    in the broadcast that carried it.
 *
 * Values are stored under stable keys (the preset's [ThemePreset.key] or
 * [CUSTOM_PRESET_KEY], the colour as a string, the toggle as a boolean) so a
 * stored setting survives an app update and the launcher broadcast can match
 * by name.
 */
class ThemeStore(
    private val kv: ThemeKeyValueStore,
) {
    /** The theme the user selected in-app; [ThemePreset.DEFAULT] until they pick one. */
    var manualTheme: ThemePreset
        get() = ThemePreset.fromKey(kv.getString(KEY_MANUAL_THEME)) ?: ThemePreset.DEFAULT
        set(value) = kv.putString(KEY_MANUAL_THEME, value.key)

    /** Whether TxxT follows the launcher's active theme. On by default. */
    var autoSyncEnabled: Boolean
        get() = kv.getBoolean(KEY_AUTO_SYNC, default = true)
        set(value) = kv.putBoolean(KEY_AUTO_SYNC, value)

    /**
     * The launcher's most recently reported theme *as a named preset*, or null.
     *
     * Null has two meanings and both are correct: no broadcast has landed yet,
     * or the last broadcast was the family's **Custom** theme — which has no
     * [ThemePreset] entry (it carries no colour of its own, see
     * [CUSTOM_PRESET_KEY]), so [ThemePreset.fromKey] cannot and must not invent
     * one. Callers that need the actual ground a Custom broadcast carried read
     * [lastLauncherGround]; this preset-shaped view stays deliberately narrow
     * so nothing downstream mistakes a custom colour for a named preset.
     *
     * Null-safe on assignment: writing null leaves the last persisted value
     * untouched — a launcher broadcast always carries a real ground, so there
     * is nothing legitimate to erase.
     */
    var lastLauncherTheme: ThemePreset?
        get() = ThemePreset.fromKey(kv.getString(KEY_LAST_LAUNCHER_THEME))
        set(value) {
            // Route through the ground writer so the durable record is never
            // half-written: a preset assignment must also refresh the stored
            // background, or a later named-preset broadcast would leave a
            // previous Custom colour behind as a stale ground.
            lastLauncherGround = value?.ground
        }

    /**
     * The launcher's most recently reported **ground** — the full truth of the
     * last broadcast, Custom included — or null when none has landed.
     *
     * Persisted as three values under stable keys: the preset key (a
     * [ThemePreset.key] or [CUSTOM_PRESET_KEY]), the raw background, and the
     * dark/light flag. The background and flag are what make Custom survive
     * process death: a preset key alone can be re-resolved from the enum, but
     * a custom colour exists nowhere else — drop it and the app would silently
     * fall back to its default ground on the next cold start.
     *
     * The getter prefers the enum for a named key (so the seven presets stay
     * the single source of truth for their own colours, and an install that
     * only ever persisted a key — before Custom support existed — still
     * resolves). Null-safe on assignment for the same reason as
     * [lastLauncherTheme].
     */
    var lastLauncherGround: ThemeGround?
        get() {
            val key = kv.getString(KEY_LAST_LAUNCHER_THEME) ?: return null
            ThemePreset.fromKey(key)?.let { return it.ground }
            if (key != CUSTOM_PRESET_KEY) return null
            val background = kv.getString(KEY_LAST_LAUNCHER_BACKGROUND)?.toLongOrNull() ?: return null
            return ThemeGround(
                background = background,
                // Fall back to the contrast rule rather than to a hardcoded
                // default: a stored flag can be missing (a partially-written
                // legacy record), and guessing "dark" would be illegible over
                // a pale ground.
                isDark = kv.getBoolean(KEY_LAST_LAUNCHER_DARK, !prefersDarkForeground(background)),
                presetKey = CUSTOM_PRESET_KEY,
            )
        }
        set(value) {
            if (value == null) return
            kv.putString(KEY_LAST_LAUNCHER_THEME, value.presetKey)
            // Longs go through the String seam: ThemeKeyValueStore is
            // deliberately string+boolean only, and a 0xAARRGGBB value does
            // not fit a positive Int.
            kv.putString(KEY_LAST_LAUNCHER_BACKGROUND, value.background.toString())
            kv.putBoolean(KEY_LAST_LAUNCHER_DARK, value.isDark)
        }

    /**
     * The theme that should actually drive the UI, applying the manual-wins
     * precedence: a manual in-app theme overrides auto-sync. When the user has
     * set a manual theme (different from the default) it wins; otherwise the
     * launcher-synced [launcherTheme] applies when auto-sync is on, else the
     * default.
     *
     * @param launcherTheme the launcher's active theme (from T5's receiver), or
     *   null when none is known in memory. Callers that want the durable
     *   fallback should pass `inMemory ?: store.lastLauncherTheme` — as
     *   ThemeController does.
     */
    fun effectiveTheme(launcherTheme: ThemePreset?): ThemePreset {
        val manual = manualTheme
        if (manual != ThemePreset.DEFAULT) return manual
        if (autoSyncEnabled && launcherTheme != null) return launcherTheme
        return ThemePreset.DEFAULT
    }

    /**
     * The ground that should actually paint the UI, applying the SAME
     * manual-wins precedence [effectiveTheme] applies — this is that rule
     * expressed over [ThemeGround] so a Custom launcher ground can win an
     * auto-sync round it would otherwise be unable to represent.
     *
     * Deliberately a sibling of [effectiveTheme] rather than a replacement:
     * the precedence itself ("explicit beats ambient", PRIVACY.md §7) is
     * unchanged and stated once in each shape — a manual in-app pick wins;
     * otherwise [launcherGround] applies when auto-sync is on; else the
     * default ground.
     *
     * @param launcherGround the launcher's active ground (from T5's receiver),
     *   or null when none is known in memory. Callers wanting the durable
     *   fallback pass `inMemory ?: store.lastLauncherGround`.
     */
    fun effectiveGround(launcherGround: ThemeGround?): ThemeGround {
        val manual = manualTheme
        if (manual != ThemePreset.DEFAULT) return manual.ground
        if (autoSyncEnabled && launcherGround != null) return launcherGround
        return ThemePreset.DEFAULT.ground
    }

    companion object {
        const val KEY_MANUAL_THEME = "theme_manual"
        const val KEY_AUTO_SYNC = "theme_auto_sync"
        const val KEY_LAST_LAUNCHER_THEME = "theme_last_launcher"

        /** Raw 0xAARRGGBB ground of the last broadcast, as a decimal string. */
        const val KEY_LAST_LAUNCHER_BACKGROUND = "theme_last_launcher_background"

        /** Dark/light classification of the last broadcast's ground. */
        const val KEY_LAST_LAUNCHER_DARK = "theme_last_launcher_dark"
    }
}