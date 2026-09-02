package com.piercingxx.txxt.ui

import com.piercingxx.txxt.theme.ThemePreset

/**
 * Lock-screen privacy posture for notifications.
 *
 * PRIVACY.md §3: notifications show the sender name only by default; the
 * options are sender-only / content / nothing, defaulting to **sender-only**
 * (tightens FEATURES.md Q13 from "content" to "sender-only").
 */
enum class LockScreenPrivacy {
    /** Show only the sender's name on the lock screen. Default. */
    SENDER_ONLY,

    /** Show the full message content on the lock screen. */
    CONTENT,

    /** Show nothing on the lock screen. */
    NOTHING,
}

/**
 * Global notification alert style.
 *
 * PRIVACY.md §8: per-contact notification control defaults to the global
 * posture (silent / vibrate / sound). The default is [AlertStyle.SOUND] —
 * notifications still announce; content redaction is governed separately by
 * [LockScreenPrivacy] (sender-only by default).
 *
 * Named `AlertStyle` (not `NotificationPosture`) so it cannot be confused by
 * name with the service layer's `service.NotificationPosture`, which models a
 * different dimension entirely (NOTIFY/REDACTED/SUPPRESS content posture).
 */
enum class AlertStyle {
    /** No sound and no vibration. */
    SILENT,

    /** Vibration only, no sound. */
    VIBRATE,

    /** Sound (plus system vibration) — the default alert style. */
    SOUND,
}

/**
 * The persisted settings model for the settings screen (WS12).
 *
 * Pure Kotlin with zero `android.*` imports (the theme preset comes from the
 * pure-Kotlin `theme` package) so the model is JVM-testable without a device,
 * mirroring [ThreadMessagePresenter]. Holds the settings the screen exposes
 * and persists: lock-screen privacy, the global notification alert style, the
 * theme auto-sync toggle, the chosen theme preset, and the font mode.
 *
 * The theme preset is [com.piercingxx.txxt.theme.ThemePreset] — the same enum
 * the rendering path (ThemeStore/ThemeController) persists — so a pick made
 * here drives the actual UI instead of writing to a shadow copy. The enum's
 * member names are identical to the historical settings-local enum, so
 * `.name` serialization in existing backups/prefs round-trips unchanged.
 *
 * Defaults follow the family contract: lock-screen privacy defaults to
 * sender-only (§3), and theme auto-sync defaults to **on** — a fresh install
 * follows the launcher until the user opts out, matching the rendering store's
 * own default ([com.piercingxx.txxt.theme.ThemeStore.autoSyncEnabled]). A
 * manual in-app theme always wins once set (PRIVACY.md §7 "explicit beats
 * ambient").
 * The store is a plain data class so it round-trips through the backup format
 * (WS12 T4) unchanged.
 */
data class SettingsStore(
    val lockScreenPrivacy: LockScreenPrivacy = LockScreenPrivacy.SENDER_ONLY,
    val alertStyle: AlertStyle = AlertStyle.SOUND,
    val autoSyncTheme: Boolean = true,
    val themePreset: ThemePreset = ThemePreset.DEFAULT,
    val fontMode: FontMode = FontMode.defaults(),
) {
    companion object {
        /** The factory defaults for a fresh install. */
        fun defaults() = SettingsStore()
    }
}