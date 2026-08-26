package com.piercingxx.txxt.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.core.MmsRetrievedContentParser
import com.piercingxx.txxt.data.MessageDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tap-to-retrieve for inbound MMS. Auto-download stays off (PRIVACY.md §8.1);
 * holding the SMS role still requires a retrieve path so carrier MMS is not
 * swallowed.
 */
object MmsRetrieve {

    /** True when tapping the row should fetch the PDU instead of reading aloud. */
    fun needsRetrieve(message: Message): Boolean =
        message.transport == MessageTransport.MMS &&
            message.direction == MessageDirection.INCOMING &&
            !message.contentLocation.isNullOrBlank() &&
            message.body.isBlank()

    /**
     * Applies a retrieved PDU onto the stored metadata row. Audio-only is
     * deleted (never stored). Other content becomes a text-first body and
     * clears [contentLocation] so a second tap reads aloud. Pure over the DAO.
     */
    suspend fun applyPdu(messages: MessageDao, messageId: Long, pdu: ByteArray): Boolean {
        val row = messages.getById(messageId) ?: return false
        val parsed = MmsRetrievedContentParser.parse(pdu)
        if (parsed.dropUnstored) {
            messages.deleteById(messageId)
            return true
        }
        messages.upsert(row.copy(body = parsed.body, contentLocation = null))
        return true
    }
}

/**
 * Process-wide waiters for [MmsDownloadedReceiver]. The platform send of the
 * downloaded PendingIntent is the only completion signal `downloadMultimediaMessage`
 * gives.
 */
internal object MmsDownloadWaiters {
    private val waiters = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    fun register(requestId: Int): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        waiters[requestId] = deferred
        return deferred
    }

    fun complete(requestId: Int, ok: Boolean) {
        waiters.remove(requestId)?.complete(ok)
    }
}

/** Completes the waiter for one `downloadMultimediaMessage` request. */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1)
        val ok = resultCode == Activity.RESULT_OK
        MmsDownloadWaiters.complete(requestId, ok)
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_mms_request_id"
        const val ACTION = "com.piercingxx.txxt.action.MMS_DOWNLOADED"
    }
}

/**
 * Fetches one MMS from the MMSC into a FileProvider URI and returns the PDU
 * bytes. Injectable [download] so JVM tests never touch SmsManager.
 */
class MmsContentFetcher(
    private val download: suspend (Context, String, Uri) -> Boolean = { ctx, location, dest ->
        platformDownload(ctx, location, dest)
    },
    private val readBytes: (Context, Uri) -> ByteArray? = { ctx, uri ->
        try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    },
) {

    suspend fun fetch(context: Context, locationUrl: String): ByteArray? {
        val dir = File(context.cacheDir, "mms")
        if (!dir.exists()) dir.mkdirs()
        val file = File.createTempFile("retrieve-", ".pdu", dir)
        val dest = try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.mms",
                file,
            )
        } catch (_: Exception) {
            file.delete()
            return null
        }
        val granted = try {
            download(context, locationUrl, dest)
        } finally {
            // Keep the file until we read it; revoke grants after.
        }
        val bytes = if (granted) readBytes(context, dest) else null
        file.delete()
        return bytes
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 90_000L

        internal suspend fun platformDownload(
            context: Context,
            locationUrl: String,
            dest: Uri,
        ): Boolean {
            val requestId = (System.nanoTime() and 0x7FFFFFFF).toInt()
            val waiter = MmsDownloadWaiters.register(requestId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pending = PendingIntent.getBroadcast(
                context,
                requestId,
                Intent(context, MmsDownloadedReceiver::class.java)
                    .setAction(MmsDownloadedReceiver.ACTION)
                    .putExtra(MmsDownloadedReceiver.EXTRA_REQUEST_ID, requestId),
                flags,
            )
            val grant = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            listOf("com.android.phone", "com.android.mms", "com.android.telephony").forEach { pkg ->
                try {
                    context.grantUriPermission(pkg, dest, grant)
                } catch (_: SecurityException) {
                    // Package may be absent on this device.
                }
            }
            SendPipeline.resolveSmsManager(context)
                .downloadMultimediaMessage(context, locationUrl, dest, null, pending)
            val ok = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { waiter.await() } ?: false
            return ok
        }
    }
}
