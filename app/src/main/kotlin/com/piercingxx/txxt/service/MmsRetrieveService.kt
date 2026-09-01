package com.piercingxx.txxt.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
 * process writes the PDU. Starting this service from `onReceive` is the
 * allowed window for a default SMS app to finish the retrieve.
 */
class MmsRetrieveService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_mms_retrieve_message_id"
        const val EXTRA_LOCATION = "extra_mms_retrieve_location"

        /**
         * Starts a retrieve for [messageId]. Returns false when the platform
         * refuses a background start, so the caller can fetch inline.
         */
        fun enqueue(context: Context, messageId: Long, location: String?): Boolean {
            val intent = Intent(context, MmsRetrieveService::class.java)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_LOCATION, location)
            return try {
                context.startService(intent) != null
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
