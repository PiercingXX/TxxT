package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.data.BackupJson
import com.piercingxx.txxt.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the settings store survives the backup format (WS12 T4).
 *
 * Drives the real backup path end to end: a [SettingsStore] is rendered into
 * the backup's settings map by [SettingsBackup.toSettingsMap], placed into a
 * [BackupData] payload, serialized to JSON by the Gson-backed [BackupJson],
 * deserialized back, and reconstructed into a [SettingsStore] by
 * [SettingsBackup.fromSettingsMap]. The round-trip must reproduce the store
 * exactly. This fails if [SettingsBackup] is not wired into the backup payload
 * shape.
 *
 * Also verifies [SettingsBackupFile], the on-disk settings-backup file format
 * behind the settings screen's honest backup/restore buttons: both stores'
 * maps survive an encode/decode round-trip — including values with embedded
 * newlines (the blocking sets) — and non-backup payloads are rejected.
 */
class SettingsBackupTest {

    private fun roundTrip(store: SettingsStore): SettingsStore {
        val data = BackupData(
            version = BACKUP_VERSION,
            messages = emptyList(),
            settings = SettingsBackup.toSettingsMap(store),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        val restored = BackupJson.deserialize(BackupJson.serialize(data))
        return SettingsBackup.fromSettingsMap(restored.settings)
    }

    @Test
    fun `non-default store round-trips through the backup JSON unchanged`() {
        val store = SettingsStore(
            lockScreenPrivacy = LockScreenPrivacy.NOTHING,
            alertStyle = AlertStyle.VIBRATE,
            autoSyncTheme = false,
            themePreset = ThemePreset.PAPER,
            fontMode = FontMode.JETBRAINS_MONO,
        )
        assertEquals(store, roundTrip(store))
    }

    @Test
    fun `default store round-trips through the backup JSON unchanged`() {
        assertEquals(SettingsStore.defaults(), roundTrip(SettingsStore.defaults()))
    }

    @Test
    fun `partial backup map falls back to defaults for missing fields`() {
        val store = SettingsBackup.fromSettingsMap(emptyMap())
        assertEquals(SettingsStore.defaults(), store)
    }

    @Test
    fun `unrecognised enum value falls back to that field's default`() {
        val store = SettingsBackup.fromSettingsMap(
            mapOf(
                SettingsBackup.KEY_LOCK_SCREEN_PRIVACY to "NOT_A_PRIVACY",
                SettingsBackup.KEY_THEME_PRESET to "NEON",
                SettingsBackup.KEY_FONT_MODE to "COMIC_SANS",
            ),
        )
        assertEquals(LockScreenPrivacy.SENDER_ONLY, store.lockScreenPrivacy)
        assertEquals(ThemePreset.AMOLED_NIGHT, store.themePreset)
        assertEquals(FontMode.SPACE_MONO, store.fontMode)
    }

    @Test
    fun `non-boolean auto-sync value falls back to the default off`() {
        val store = SettingsBackup.fromSettingsMap(
            mapOf(SettingsBackup.KEY_AUTO_SYNC_THEME to "maybe"),
        )
        // Default is off (privacy-by-default), matching SettingsStore and the
        // render store's ThemeStore.autoSyncEnabled default.
        assertEquals(false, store.autoSyncTheme)
    }

    @Test
    fun `alert-style backup key stays at its legacy name for compat`() {
        // The alert style was renamed from NotificationPosture to AlertStyle;
        // the serialized key string must NOT change or older backups/prefs stop
        // importing.
        assertEquals("notificationPosture", SettingsBackup.KEY_NOTIFICATION_POSTURE)
    }

    @Test
    fun `theme preset serializes by name into the same keys the renderer reads`() {
        // H4 regression lock: the settings map carries the render-path enum
        // under themePreset. Both stores serialize by Enum.name — the settings
        // map here and SettingsBackup's own deserialization — so backup and
        // rendering agree on one representation of a pick.
        val map = SettingsBackup.toSettingsMap(
            SettingsStore(themePreset = ThemePreset.FOREST_NIGHT),
        )
        assertEquals("FOREST_NIGHT", map[SettingsBackup.KEY_THEME_PRESET])
        assertEquals(
            ThemePreset.FOREST_NIGHT,
            ThemePreset.valueOf(map[SettingsBackup.KEY_THEME_PRESET]!!),
        )
        // And the reconstructed store holds the very enum object the render
        // path's controller persists.
        assertEquals(ThemePreset.FOREST_NIGHT, SettingsBackup.fromSettingsMap(map).themePreset)
    }

    // ---- On-disk backup file format (SettingsBackupFile) ----

    private val sampleBlocking = mapOf(
        SettingsBlockingStore.KEY_KEYWORDS to "spam\nlottery",
        SettingsBlockingStore.KEY_BLOCKED_ADDRESSES to "+15550001111",
        SettingsBlockingStore.KEY_STARRED_CONTACTS to "",
    )

    private val sampleSettings = mapOf(
        SettingsBackup.KEY_LOCK_SCREEN_PRIVACY to LockScreenPrivacy.NOTHING.name,
        SettingsBackup.KEY_NOTIFICATION_POSTURE to AlertStyle.SILENT.name,
        SettingsBackup.KEY_AUTO_SYNC_THEME to "false",
        SettingsBackup.KEY_THEME_PRESET to ThemePreset.MIST.name,
        SettingsBackup.KEY_FONT_MODE to FontMode.JETBRAINS_MONO.name,
    )

    @Test
    fun `backup file round-trips both stores including newline-laden values`() {
        val text = SettingsBackupFile.encode(sampleSettings, sampleBlocking)
        val (settings, blocking) = SettingsBackupFile.decode(text)!!
        assertEquals(sampleSettings, settings)
        assertEquals(sampleBlocking, blocking)
    }

    @Test
    fun `backup file survives values containing equals and backslashes`() {
        val settings = mapOf("k" to "a=b\\c\nd")
        val text = SettingsBackupFile.encode(settings, emptyMap())
        val (decodedSettings, _) = SettingsBackupFile.decode(text)!!
        assertEquals("a=b\\c\nd", decodedSettings["k"])
    }

    @Test
    fun `decoding a non-backup payload returns null`() {
        assertNull(SettingsBackupFile.decode("<html>not a backup</html>"))
        assertNull(SettingsBackupFile.decode(""))
    }

    @Test
    fun `decoding skips unknown sections and malformed lines leniently`() {
        val text = """
            [future-section]
            someKey=someValue
            ${SettingsBackupFile.SECTION_SETTINGS}
            broken-line-without-equals
            ${SettingsBackup.KEY_THEME_PRESET}=${ThemePreset.PAPER.name}
            ${SettingsBackupFile.SECTION_BLOCKING}
        """.trimIndent()
        val (settings, blocking) = SettingsBackupFile.decode(text)!!
        assertEquals(mapOf(SettingsBackup.KEY_THEME_PRESET to "PAPER"), settings)
        assertEquals(emptyMap<String, String>(), blocking)
    }
}
