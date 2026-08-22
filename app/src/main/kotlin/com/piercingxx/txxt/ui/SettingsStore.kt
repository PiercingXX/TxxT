package com.piercingxx.txxt.ui

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
 * Global notification alert posture.
 *
 * PRIVACY.md §8: per-contact notification control defaults to the global
 * posture (silent / vibrate / sound). The default is [SOUND] — notifications
 * still announce; content redaction is governed separately by
 * [LockScreenPrivacy] (sender-only by default).
 */
enum class NotificationPosture {
    /** No sound and no vibration. */
    SILENT,

    /** Vibration only, no sound. */
    VIBRATE,

    /** Sound (plus system vibration) — the default posture. */
    SOUND,
}

/**
 * The persisted settings model for the settings screen (WS12).
 *
 * Pure Kotlin with zero `android.*` imports so the model is JVM-testable
 * without a device, mirroring [ThreadMessagePresenter]. Holds the settings the
 * screen exposes and persists: lock-screen privacy, the global notification
 * posture, the theme auto-sync toggle, the chosen theme preset, and the font
 * mode.
 *
 * Defaults follow PRIVACY.md: lock-screen privacy defaults to sender-only
 * (§3), and theme auto-sync is on — TxxT's background theme follows the
 * xx-launcher by default, with a manual in-app theme winning (PRIVACY.md §7).
 * The store is a plain data class so it round-trips through the backup format
 * (WS12 T4) unchanged.
 */
data class SettingsStore(
    val lockScreenPrivacy: LockScreenPrivacy = LockScreenPrivacy.SENDER_ONLY,
    val notificationPosture: NotificationPosture = NotificationPosture.SOUND,
    val autoSyncTheme: Boolean = true,
    val themePreset: ThemePreset = ThemePreset.defaults(),
    val fontMode: FontMode = FontMode.defaults(),
) {
    companion object {
        /** The factory defaults for a fresh install. */
        fun defaults() = SettingsStore()
    }
}