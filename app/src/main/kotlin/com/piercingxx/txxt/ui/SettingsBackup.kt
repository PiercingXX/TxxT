package com.piercingxx.txxt.ui

import com.piercingxx.txxt.theme.ThemePreset

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

    /**
     * Backup settings-map key for [SettingsStore.alertStyle].
     *
     * LEGACY KEY: the value string stays `"notificationPosture"` (the field's
     * pre-rename name, when the alert style was modelled as a
     * `NotificationPosture`) so backups and persisted prefs written by older
     * builds keep round-tripping. Do not change this string.
     */
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
        KEY_NOTIFICATION_POSTURE to store.alertStyle.name,
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
        alertStyle = enumOr(map[KEY_NOTIFICATION_POSTURE], AlertStyle.SOUND),
        autoSyncTheme = map[KEY_AUTO_SYNC_THEME]?.toBooleanStrictOrNull() ?: false,
        themePreset = enumOr(map[KEY_THEME_PRESET], ThemePreset.DEFAULT),
        fontMode = enumOr(map[KEY_FONT_MODE], FontMode.SPACE_MONO),
    )

    /** Returns the [E] whose [Enum.name] equals [value], or [default] when absent/unrecognised. */
    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}

/**
 * The on-disk settings-backup file format (WS12 T4, honest backup buttons).
 *
 * FORMAT (deliberately NOT JSON): a line-oriented, sectioned key=value text.
 * `org.json` is stubbed on the JVM test classpath (android.jar returns
 * defaults rather than parsing), so the format is kept pure-Kotlin-parseable:
 *
 * ```
 * [settings]
 * lockScreenPrivacy=SENDER_ONLY
 * ...
 * [blocking]
 * blockingKeywords=spam\nlottery
 * ...
 * ```
 *
 * Values are escaped (`\` → `\\`, newline → `\n`, carriage return → `\r`) so
 * multi-value entries — e.g. the blocking sets [SettingsBlockingStore] joins
 * with newlines inside one map value — survive the line-based encoding. Keys
 * contain neither `=` nor newlines, so values may freely contain `=` (the
 * parser splits at the first one). Unknown sections and malformed lines are
 * skipped leniently; a payload carrying neither known section header is
 * rejected ([decode] returns null).
 */
object SettingsBackupFile {

    /** Section header introducing the [SettingsBackup.toSettingsMap] entries. */
    const val SECTION_SETTINGS = "[settings]"

    /** Section header introducing the [SettingsBlockingStore.toMap] entries. */
    const val SECTION_BLOCKING = "[blocking]"

    /** Renders both stores' string maps into the backup file payload. */
    fun encode(settings: Map<String, String>, blocking: Map<String, String>): String = buildString {
        appendLine(SECTION_SETTINGS)
        settings.forEach { (key, value) -> appendLine("$key=${escape(value)}") }
        appendLine(SECTION_BLOCKING)
        blocking.forEach { (key, value) -> appendLine("$key=${escape(value)}") }
    }

    /**
     * Parses a backup file payload into its `(settings, blocking)` maps, or
     * null when the text carries neither known section header (i.e. it is not
     * a TxxT settings backup). Unrecognised keys and lines are skipped.
     */
    fun decode(text: String): Pair<Map<String, String>, Map<String, String>>? {
        var section: String? = null
        val settings = mutableMapOf<String, String>()
        val blocking = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            when {
                line.trim() == SECTION_SETTINGS -> section = "settings"
                line.trim() == SECTION_BLOCKING -> section = "blocking"
                else -> {
                    val splitAt = line.indexOf('=')
                    val target = when (section) {
                        "settings" -> settings
                        "blocking" -> blocking
                        else -> null
                    }
                    if (splitAt > 0 && target != null) {
                        target[line.take(splitAt)] = unescape(line.substring(splitAt + 1))
                    }
                }
            }
        }
        if (!text.contains(SECTION_SETTINGS) && !text.contains(SECTION_BLOCKING)) return null
        return settings to blocking
    }

    /** Escapes a value for the line-based encoding. */
    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    /** Reverses [escape]. An unknown escape keeps both characters verbatim. */
    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        return buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (val next = value[i + 1]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        '\\' -> append('\\')
                        else -> append(c).append(next)
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}