package com.piercingxx.txxt.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
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
     * absent/unknown) → `txxt_messages_v[generation]`. A channel's
     * sound/vibration is fixed at creation, so the style choice picks the
     * channel. Pure — JVM-testable.
     */
    fun channelIdFor(
        alertStyleName: String?,
        generation: Int = SOUND_CHANNEL_GENERATION,
    ): String = when (alertStyleName) {
        AlertStyle.SILENT.name -> CHANNEL_ID_SILENT
        AlertStyle.VIBRATE.name -> CHANNEL_ID_VIBRATE
        else -> soundChannelId(generation)
    }

    /** The effective alert-style channel from the persisted settings. */
    fun channelId(context: Context): String {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return channelIdFor(
            prefs.getString(SettingsBackup.KEY_NOTIFICATION_POSTURE, null),
            prefs.getInt(KEY_SOUND_GENERATION, SOUND_CHANNEL_GENERATION),
        )
    }

    /** Live sound-channel id for [generation]: `txxt_messages_vN`. */
    fun soundChannelId(generation: Int): String = "$SOUND_CHANNEL_PREFIX$generation"

    fun soundChannelId(context: Context): String {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return soundChannelId(prefs.getInt(KEY_SOUND_GENERATION, SOUND_CHANNEL_GENERATION))
    }

    /**
     * The ringtone the sound channel should play. Empty prefs → the phone's
     * default notification sound, so TxxT follows Settings → Sound.
     */
    fun soundUri(context: Context): Uri {
        val stored = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOUND_URI, null)
        if (!stored.isNullOrBlank()) return Uri.parse(stored)
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
    }

    /**
     * Persists a picked notification ringtone and bumps the sound-channel
     * generation so [ensureMessageChannels] can mint a channel with the new
     * URI. A null / default URI stores empty and follows the phone default.
     */
    fun setSound(context: Context, uri: Uri?) {
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val stored = if (uri == null || uri == defaultUri) "" else uri.toString()
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_SOUND_GENERATION, SOUND_CHANNEL_GENERATION) + 1
        prefs.edit()
            .putString(KEY_SOUND_URI, stored)
            .putInt(KEY_SOUND_GENERATION, next)
            .apply()
    }

    /**
     * True when [channelId] is a retired sound channel that must be deleted
     * (v1 `txxt_messages`, or `txxt_messages_vN` other than [currentSoundId]).
     * Does not match vibrate/silent (`txxt_messages_vibrate` / `_silent`).
     */
    fun isRetiredSoundChannel(channelId: String, currentSoundId: String): Boolean {
        if (channelId == currentSoundId) return false
        if (channelId == LEGACY_CHANNEL_ID_SOUND) return true
        return SOUND_CHANNEL_ID.matches(channelId)
    }

    /** The settings SharedPreferences file (SettingsActivity.PREFS_NAME). */
    private const val SETTINGS_PREFS = "txxt_settings"

    const val KEY_SOUND_URI = "notificationSoundUri"
    const val KEY_SOUND_GENERATION = "notificationSoundGeneration"
    const val SOUND_CHANNEL_GENERATION = 3
    const val SOUND_CHANNEL_PREFIX = "txxt_messages_v"
    private val SOUND_CHANNEL_ID = Regex("^txxt_messages_v\\d+$")
}
