package com.piercingxx.txxt.service

import android.content.Context
import com.piercingxx.txxt.ui.AlertStyle
import com.piercingxx.txxt.ui.BlockingRules
import com.piercingxx.txxt.ui.LockScreenPrivacy
import com.piercingxx.txxt.ui.SettingsBackup
import com.piercingxx.txxt.ui.SettingsBlockingStore

/**
 * Bridges the persisted settings to the notification decision.
 *
 * The settings screen persists the lock-screen privacy choice
 * ([LockScreenPrivacy]: sender-only / content / nothing) and the starred
 * contacts, but the deliver receivers used to post every notification with
 * the hardcoded REDACTED default and `starred = false` — the user's choice
 * never reached the notification. This helper closes that gap: the receivers
 * read the effective posture and the sender's starred flag here, so
 * [NotificationPosture.decide] runs over what the user actually configured
 * (starred contacts bypass suppression per docs/PRIVACY.md §6).
 *
 * The mapping is a pure function ([postureFor]) over the persisted enum name;
 * the two context readers are thin SharedPreferences glue over the same
 * string-map shapes the settings screen and backup write.
 */
object NotificationPrefs {

    /**
     * Maps the persisted lock-screen privacy choice to the notification
     * posture: sender-only → REDACTED (the default), content → NOTIFY,
     * nothing → SUPPRESS. An absent or unknown value falls back to the
     * privacy default, REDACTED. Pure — JVM-testable.
     */
    fun postureFor(lockScreenPrivacyName: String?): NotificationPosture.Posture =
        when (lockScreenPrivacyName) {
            LockScreenPrivacy.CONTENT.name -> NotificationPosture.Posture.NOTIFY
            LockScreenPrivacy.NOTHING.name -> NotificationPosture.Posture.SUPPRESS
            else -> NotificationPosture.Posture.REDACTED
        }

    /** The effective global posture from the persisted settings. */
    fun globalPosture(context: Context): NotificationPosture.Posture {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return postureFor(prefs.getString(SettingsBackup.KEY_LOCK_SCREEN_PRIVACY, null))
    }

    /** Whether [sender] is in the persisted starred-contacts set. */
    fun isStarred(context: Context, sender: String): Boolean {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val map = SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        return BlockingRules.isStarred(map, sender)
    }

    /**
     * Maps the persisted alert style to the notification channel to post on:
     * silent → [CHANNEL_ID_SILENT], vibrate → [CHANNEL_ID_VIBRATE], sound (or
     * absent/unknown) → [CHANNEL_ID]. A channel's sound/vibration is fixed at
     * creation, so the style choice picks the channel. Pure — JVM-testable.
     */
    fun channelIdFor(alertStyleName: String?): String = when (alertStyleName) {
        AlertStyle.SILENT.name -> CHANNEL_ID_SILENT
        AlertStyle.VIBRATE.name -> CHANNEL_ID_VIBRATE
        else -> CHANNEL_ID
    }

    /** The effective alert-style channel from the persisted settings. */
    fun channelId(context: Context): String {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return channelIdFor(prefs.getString(SettingsBackup.KEY_NOTIFICATION_POSTURE, null))
    }

    /** The settings SharedPreferences file (SettingsActivity.PREFS_NAME). */
    private const val SETTINGS_PREFS = "txxt_settings"
}
