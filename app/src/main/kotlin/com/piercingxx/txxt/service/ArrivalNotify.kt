package com.piercingxx.txxt.service

import android.content.Context
import com.piercingxx.txxt.contacts.PhoneLookupIdentity
import com.piercingxx.txxt.core.ViewedThread
import com.piercingxx.txxt.data.ConversationMute
import com.piercingxx.txxt.data.TxxTDatabase
import java.time.LocalDateTime

/**
 * Shared inbound-notification path for SMS and MMS deliver receivers.
 *
 * Persisted lock-screen privacy, mute, and TxxT's own starred set still
 * decide [NotificationPosture]. Business-tier membership is read from
 * XX-Dialer: outside that window the alert is forced onto the silent
 * channel (message still stored, shade still updates — same disposition
 * as a silenced call). Starred — TxxT's list or the provider STARRED
 * flag — beats the window, matching dialer R5/R6.
 */
object ArrivalNotify {

    suspend fun post(context: Context, sender: String, body: String) {
        // The operator is already reading this thread — do not leave a shade
        // tile that they then have to swipe away.
        if (ViewedThread.isOpenFor(sender)) return
        val gate = PermissionGate()
        if (!gate.canNotify(context)) return
        val hit = PhoneLookupIdentity.lookup(context, sender)
        if (hit.lookupKey != null &&
            hit.lookupKey in DialerGroups.keysNamed(context, DialerGroups.BLOCKED)
        ) {
            return
        }
        val starred = NotificationPrefs.isStarred(context, sender) || hit.starred
        val quiet = BusinessSchedule.shouldQuiet(
            snapshot = DialerBusinessTier.load(context),
            lookupKey = hit.lookupKey,
            starred = starred,
            now = LocalDateTime.now(),
        )
        val muted = ConversationMute.isMuted(
            TxxTDatabase.instance(context).conversationDao(),
            sender,
        )
        NotificationService(context).postMessageNotification(
            sender = sender,
            body = body,
            starred = starred,
            globalPosture = NotificationPrefs.globalPosture(context),
            channelId = if (quiet) CHANNEL_ID_SILENT else NotificationPrefs.channelId(context),
            muted = muted,
        )
    }
}
