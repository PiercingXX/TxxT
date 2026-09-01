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
import com.piercingxx.txxt.data.TxxTDatabase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Retrieve path for inbound MMS. Photos are fetched on arrival; a failed
 * fetch can still be retried from a tap. Voice MMS is dropped (PRIVACY.md §5).
 */
object MmsRetrieve {

    private val inFlight = ConcurrentHashMap<Long, Deferred<Boolean>>()

    /** True when an inbound MMS still has an MMSC location and no photo on disk. */
    fun needsRetrieve(message: Message): Boolean =
        message.transport == MessageTransport.MMS &&
            message.direction == MessageDirection.INCOMING &&
            !message.contentLocation.isNullOrBlank() &&
            message.mediaPath.isNullOrBlank()

    /**
     * Fetches one inbound MMS and applies it. Concurrent callers for the same
     * id share the in-flight retrieve so a tap during auto-download does not
     * start a second MMSC GET.
     */
    suspend fun retrieveAndStore(
        context: Context,
        messageId: Long,
        location: String? = null,
    ): Boolean {
        val created = CompletableDeferred<Boolean>()
        val existing = inFlight.putIfAbsent(messageId, created)
        if (existing != null) return existing.await()
        try {
            val ok = retrieveOnce(context, messageId, location)
            created.complete(ok)
            return ok
        } catch (t: Throwable) {
            created.complete(false)
            if (t is CancellationException) throw t
            return false
        } finally {
            inFlight.remove(messageId, created)
        }
    }

    private suspend fun retrieveOnce(
        context: Context,
        messageId: Long,
        location: String?,
    ): Boolean {
        val dao = TxxTDatabase.instance(context).messageDao()
        val row = dao.getById(messageId) ?: return false
        val existingPath = row.mediaPath
        if (!existingPath.isNullOrBlank() && File(existingPath).isFile) return true
        val url = location?.takeIf { it.isNotBlank() } ?: row.contentLocation
        if (url.isNullOrBlank()) return false
        val pdu = MmsContentFetcher().fetch(context, url) ?: return false
        return applyPdu(dao, messageId, pdu) { bytes, mime ->
            saveRetrievedImage(context, messageId, bytes, mime)
        }
    }

    /**
     * Applies a retrieved PDU onto the stored metadata row. Audio-only and
     * empty/unknown content are deleted. An image becomes `[Photo]` plus a
     * saved file (shown only after the operator taps). A captioned photo keeps
     * the caption. Clears [contentLocation] once applied. An image MIME with
     * no saved bytes is left pending so a tap can retry — it is not deleted.
     */
    suspend fun applyPdu(
        messages: MessageDao,
        messageId: Long,
        pdu: ByteArray,
        saveImage: ((ByteArray, String) -> String?)? = null,
    ): Boolean {
        val row = messages.getById(messageId) ?: return false
        val parsed = MmsRetrievedContentParser.parse(pdu)
        if (parsed.dropUnstored || parsed.body == MmsRetrievedContent.MMS_PLACEHOLDER) {
            messages.deleteById(messageId)
            return true
        }
        val imageBytes = parsed.imageBytes
        val mime = parsed.imageMime
        val path = if (imageBytes != null && mime != null) {
            saveImage?.invoke(imageBytes, mime)
        } else {
            null
        }
        val caption = parsed.body.takeIf {
            it.isNotBlank() &&
                it != MmsRetrievedContent.PHOTO_PLACEHOLDER &&
                it != MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER &&
                it != MmsRetrievedContent.MMS_PLACEHOLDER
        }
        when {
            path != null -> {
                messages.upsert(
                    row.copy(
                        body = caption ?: MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                        contentLocation = null,
                        mediaPath = path,
                    )
                )
                return true
            }
            caption != null -> {
                messages.upsert(
                    row.copy(
                        body = caption,
                        contentLocation = null,
                    )
                )
                return true
            }
            parsed.body == MmsRetrievedContent.PHOTO_PLACEHOLDER -> {
                // Image was advertised but not saved. Keep the row + location.
                return false
            }
            else -> {
                messages.deleteById(messageId)
                return true
            }
        }
    }

    /** Writes retrieved image bytes under `filesDir/mms/` and returns the path. */
    fun saveRetrievedImage(
        context: Context,
        messageId: Long,
        bytes: ByteArray,
        mime: String,
    ): String? = try {
        val dir = File(context.filesDir, "mms")
        if (!dir.exists()) dir.mkdirs()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val file = File(dir, "$messageId.$ext")
        file.writeBytes(bytes)
        file.absolutePath
    } catch (_: Exception) {
        null
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
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1).takeIf { it >= 0 }
            ?: intent.data?.lastPathSegment?.toIntOrNull()
            ?: -1
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
        MmsUriGrants.grantWrite(context, dest)
        val signaled = try {
            download(context, locationUrl, dest)
        } catch (_: Exception) {
            false
        }
        val bytes = when {
            file.length() > 0L -> try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
            signaled -> readBytes(context, dest)
            else -> null
        }
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
                    .setData(Uri.parse("txxt-mms://download/$requestId"))
                    .putExtra(MmsDownloadedReceiver.EXTRA_REQUEST_ID, requestId),
                flags,
            )
            MmsUriGrants.grantWrite(context, dest)
            return try {
                SendPipeline.resolveSmsManager(context)
                    .downloadMultimediaMessage(context, locationUrl, dest, null, pending)
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { waiter.await() } ?: false
            } catch (_: Exception) {
                MmsDownloadWaiters.complete(requestId, false)
                false
            }
        }
    }
}
