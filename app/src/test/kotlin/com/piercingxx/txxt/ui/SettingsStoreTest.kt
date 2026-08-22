package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun `defaults use sender-only lock-screen privacy`() {
        // PRIVACY.md §3: lock-screen privacy defaults to sender-only.
        assertEquals(LockScreenPrivacy.SENDER_ONLY, SettingsStore.defaults().lockScreenPrivacy)
    }

    @Test
    fun `defaults use sound notification posture`() {
        // PRIVACY.md §8: the global posture is sound; content redaction is a
        // separate dimension handled by lock-screen privacy.
        assertEquals(NotificationPosture.SOUND, SettingsStore.defaults().notificationPosture)
    }

    @Test
    fun `defaults enable theme auto-sync`() {
        // PRIVACY.md §7: TxxT's background theme follows the xx-launcher by
        // default; a manual in-app theme wins over auto-sync.
        assertTrue(SettingsStore.defaults().autoSyncTheme)
    }

    @Test
    fun `store holds the chosen settings`() {
        val store = SettingsStore(
            lockScreenPrivacy = LockScreenPrivacy.CONTENT,
            notificationPosture = NotificationPosture.SILENT,
            autoSyncTheme = false,
        )
        assertEquals(LockScreenPrivacy.CONTENT, store.lockScreenPrivacy)
        assertEquals(NotificationPosture.SILENT, store.notificationPosture)
        assertFalse(store.autoSyncTheme)
    }

    @Test
    fun `data class equality round-trips the stored values`() {
        // The store is a plain data class so it round-trips through the backup
        // format (WS12 T4) unchanged: serialise/deserialise reproduces the same
        // value object.
        val store = SettingsStore(
            lockScreenPrivacy = LockScreenPrivacy.NOTHING,
            notificationPosture = NotificationPosture.VIBRATE,
            autoSyncTheme = false,
        )
        assertEquals(store, SettingsStore(store.lockScreenPrivacy, store.notificationPosture, store.autoSyncTheme))
    }
}