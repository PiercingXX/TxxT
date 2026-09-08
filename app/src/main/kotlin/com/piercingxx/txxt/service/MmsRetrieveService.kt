package com.piercingxx.txxt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while an inbound MMS is fetched from the MMSC.
 *
 * [MmsDeliverReceiver] cannot wait on `downloadMultimediaMessage` inside
 * `goAsync()`: the broadcast timeout kills the waiter before the telephony
 * process writes the PDU. A foreground service is the allowed window for a
 * default SMS app to finish the retrieve after the broadcast ends.
 */
class MmsRetrieveService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startRetrieveForeground()
        val messageId = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
        val location = intent?.getStringExtra(EXTRA_LOCATION)
        scope.launch {
            try {
                if (messageId > 0L) {
                    retrieveAndNotify(this@MmsRetrieveService, messageId, location)
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startRetrieveForeground() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading photo")
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Photo download",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_mms_retrieve_message_id"
        const val EXTRA_LOCATION = "extra_mms_retrieve_location"
        private const val CHANNEL_ID = "txxt_mms_retrieve"
        private const val NOTIFICATION_ID = 71

        /**
         * Starts a retrieve for [messageId]. Returns false when the platform
         * refuses a background start, so the caller can fetch inline.
         */
        fun enqueue(context: Context, messageId: Long, location: String?): Boolean {
            val intent = Intent(context, MmsRetrieveService::class.java)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_LOCATION, location)
            return try {
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                started != null
            } catch (_: IllegalStateException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }

        internal suspend fun retrieveAndNotify(
            context: Context,
            messageId: Long,
            location: String?,
        ) {
            MmsRetrieve.retrieveAndStore(context, messageId, location)
            val row = TxxTDatabase.instance(context).messageDao().getById(messageId) ?: return
            val sender = row.senderAddress?.takeIf { it.isNotBlank() } ?: return
            ArrivalNotify.post(context, sender, row.body)
        }
    }
}
