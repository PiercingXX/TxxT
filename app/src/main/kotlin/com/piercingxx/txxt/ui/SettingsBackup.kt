package com.piercingxx.txxt.ui

/**
 * Backup/restore integration for the settings store (WS12 T4).
 *
 * DESIGN.md:78 lists backup/restore among the settings the screen exposes. The
 * on-disk backup format (core [com.piercingxx.txxt.core.BackupData]) carries
 * settings as a string-keyed, string-valued map, because that is what the
 * launcher's Gson-based export shape expects (`docs/INSPIRATION.md:132`). This
 * object bridges the pure, strongly-typed [SettingsStore] the screen edits and
 * that string map, so a [SettingsStore] round-trips through the backup JSON
 * unchanged.
 *
 * Serialization writes each field under a stable key and stores enum values by
 * [Enum.name] (stable across refactors, unlike ordinal). Deserialization is
 * lenient on import: a missing or unrecognised value falls back to the
 * factory default for that field, so a backup written by an older or partial
 * build imports cleanly instead of failing the whole restore.
 *
 * Pure Kotlin with zero `android.*` imports, mirroring [SettingsStore], so the
 * round-trip is JVM-testable without a device. The actual writing/reading of a
 * file on device through the screen is the operator's on-device check; this
 * proves the store survives the backup format.
 */
object SettingsBackup {

    /** Backup settings-map key for [SettingsStore.lockScreenPrivacy]. */
    const val KEY_LOCK_SCREEN_PRIVACY = "lockScreenPrivacy"

    /** Backup settings-map key for [SettingsStore.notificationPosture]. */
    const val KEY_NOTIFICATION_POSTURE = "notificationPosture"

    /** Backup settings-map key for [SettingsStore.autoSyncTheme]. */
    const val KEY_AUTO_SYNC_THEME = "autoSyncTheme"

    /** Backup settings-map key for [SettingsStore.themePreset]. */
    const val KEY_THEME_PRESET = "themePreset"

    /** Backup settings-map key for [SettingsStore.fontMode]. */
    const val KEY_FONT_MODE = "fontMode"

    /** All backup settings-map keys, in [SettingsStore] constructor order. */
    val KEY_NAMES: List<String> = listOf(
        KEY_LOCK_SCREEN_PRIVACY,
        KEY_NOTIFICATION_POSTURE,
        KEY_AUTO_SYNC_THEME,
        KEY_THEME_PRESET,
        KEY_FONT_MODE,
    )

    /**
     * Renders [store] into the backup's settings map, one string entry per
     * field. Feed the result into [com.piercingxx.txxt.core.BackupData.settings]
     * to export the store with the backup payload.
     */
    fun toSettingsMap(store: SettingsStore): Map<String, String> = mapOf(
        KEY_LOCK_SCREEN_PRIVACY to store.lockScreenPrivacy.name,
        KEY_NOTIFICATION_POSTURE to store.notificationPosture.name,
        KEY_AUTO_SYNC_THEME to store.autoSyncTheme.toString(),
        KEY_THEME_PRESET to store.themePreset.name,
        KEY_FONT_MODE to store.fontMode.name,
    )

    /**
     * Reconstructs a [SettingsStore] from the backup's settings map. Any field
     * that is missing, or whose value is not a recognised member of its enum
     * (or a valid boolean), falls back to that field's factory default so the
     * import never fails on a partial or older backup.
     */
    fun fromSettingsMap(map: Map<String, String>): SettingsStore = SettingsStore(
        lockScreenPrivacy = enumOr(map[KEY_LOCK_SCREEN_PRIVACY], LockScreenPrivacy.SENDER_ONLY),
        notificationPosture = enumOr(map[KEY_NOTIFICATION_POSTURE], NotificationPosture.SOUND),
        autoSyncTheme = map[KEY_AUTO_SYNC_THEME]?.toBooleanStrictOrNull() ?: true,
        themePreset = enumOr(map[KEY_THEME_PRESET], ThemePreset.AMOLED_NIGHT),
        fontMode = enumOr(map[KEY_FONT_MODE], FontMode.SPACE_MONO),
    )

    /** Returns the [E] whose [Enum.name] equals [value], or [default] when absent/unrecognised. */
    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}