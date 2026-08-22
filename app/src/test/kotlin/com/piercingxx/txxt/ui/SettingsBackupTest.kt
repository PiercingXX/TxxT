package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.data.BackupJson
import org.junit.Assert.assertEquals
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
            notificationPosture = NotificationPosture.VIBRATE,
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
    fun `non-boolean auto-sync value falls back to the default on`() {
        val store = SettingsBackup.fromSettingsMap(
            mapOf(SettingsBackup.KEY_AUTO_SYNC_THEME to "maybe"),
        )
        assertEquals(true, store.autoSyncTheme)
    }
}