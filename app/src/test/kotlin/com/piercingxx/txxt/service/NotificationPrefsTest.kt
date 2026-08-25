package com.piercingxx.txxt.service

import com.piercingxx.txxt.ui.LockScreenPrivacy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour-verifies [NotificationPrefs.postureFor]: the pure mapping from the
 * persisted lock-screen privacy choice to the notification posture the deliver
 * receivers hand [NotificationPosture.decide]. This is the bridge that makes
 * the settings screen's choice actually reach the posted notification.
 */
class NotificationPrefsTest {

    @Test
    fun `sender-only maps to REDACTED`() {
        assertEquals(
            NotificationPosture.Posture.REDACTED,
            NotificationPrefs.postureFor(LockScreenPrivacy.SENDER_ONLY.name),
        )
    }

    @Test
    fun `content maps to NOTIFY`() {
        assertEquals(
            NotificationPosture.Posture.NOTIFY,
            NotificationPrefs.postureFor(LockScreenPrivacy.CONTENT.name),
        )
    }

    @Test
    fun `nothing maps to SUPPRESS`() {
        assertEquals(
            NotificationPosture.Posture.SUPPRESS,
            NotificationPrefs.postureFor(LockScreenPrivacy.NOTHING.name),
        )
    }

    @Test
    fun `absent or unknown values fall back to the privacy default REDACTED`() {
        assertEquals(NotificationPosture.Posture.REDACTED, NotificationPrefs.postureFor(null))
        assertEquals(NotificationPosture.Posture.REDACTED, NotificationPrefs.postureFor("garbage"))
    }

    @Test
    fun `alert styles pick their channel`() {
        assertEquals(
            CHANNEL_ID_SILENT,
            NotificationPrefs.channelIdFor(com.piercingxx.txxt.ui.AlertStyle.SILENT.name),
        )
        assertEquals(
            CHANNEL_ID_VIBRATE,
            NotificationPrefs.channelIdFor(com.piercingxx.txxt.ui.AlertStyle.VIBRATE.name),
        )
        assertEquals(
            CHANNEL_ID,
            NotificationPrefs.channelIdFor(com.piercingxx.txxt.ui.AlertStyle.SOUND.name),
        )
    }

    @Test
    fun `absent or unknown alert styles fall back to the sound channel`() {
        assertEquals(CHANNEL_ID, NotificationPrefs.channelIdFor(null))
        assertEquals(CHANNEL_ID, NotificationPrefs.channelIdFor("garbage"))
    }
}
