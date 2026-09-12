package com.piercingxx.txxt.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.core.MmsRetrievedContentParser
import com.piercingxx.txxt.data.MessageDao
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.log.AppLog
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
        val row = dao.getById(messageId) ?: run {
            AppLog.w("mms", "retrieve missing row id=$messageId")
            return false
        }
        val existingPath = row.mediaPath
        if (!existingPath.isNullOrBlank() && File(existingPath).isFile) return true
        val url = location?.takeIf { it.isNotBlank() } ?: row.contentLocation
        if (url.isNullOrBlank()) {
            AppLog.w("mms", "retrieve no location id=$messageId")
            return false
        }
        AppLog.i("mms", "retrieve start id=$messageId")
        val pdu = MmsContentFetcher().fetch(context, url)
        if (pdu == null) {
            AppLog.w("mms", "retrieve empty pdu id=$messageId")
            return false
        }
        val parsed = MmsRetrievedContentParser.parse(pdu)
        AppLog.i(
            "mms",
            "retrieve pdu id=$messageId bytes=${pdu.size} mime=${parsed.imageMime} " +
                "image=${parsed.imageBytes?.size ?: 0}",
        )
        val ok = applyPdu(dao, messageId, pdu) { bytes, mime ->
            saveRetrievedImage(context, messageId, bytes, mime)
        }
        AppLog.i("mms", "retrieve ${if (ok) "ok" else "pending"} id=$messageId bytes=${pdu.size}")
        return ok
    }

    /**
     * Applies a retrieved PDU onto the stored metadata row. Audio-only content
     * is deleted. An image becomes `[Photo]` plus a saved file (shown only
     * after the operator taps). A captioned photo keeps the caption. Clears
     * [contentLocation] once applied. An image MIME with no saved bytes, or a
     * SMIL-only/empty retrieve, is left pending so a tap can retry.
     */
    suspend fun applyPdu(
        messages: MessageDao,
        messageId: Long,
        pdu: ByteArray,
        saveImage: ((ByteArray, String) -> String?)? = null,
    ): Boolean {
        val row = messages.getById(messageId) ?: return false
        val parsed = MmsRetrievedContentParser.parse(pdu)
        if (parsed.dropUnstored) {
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
                // Empty / SMIL-only retrieve. Keep the pending [Photo] row so
                // a tap can retry — do not delete a notification we already stored.
                return false
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
            mime.contains("heic") || mime.contains("heif") -> "heic"
            mime.contains("bmp") -> "bmp"
            mime.contains("3gp") || mime.contains("mp4") || mime.startsWith("video/") -> "3gp"
            else -> "jpg"
        }
        if (ext == "3gp") {
            stillFromVideo(dir, messageId, bytes)?.let { return it }
        }
        val file = File(dir, "$messageId.$ext")
        file.writeBytes(bytes)
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    /**
     * Verizon often wraps a still photo as `video/3gpp`. The thread can
     * only show a bitmap, so pull the first frame.
     */
    internal fun stillFromVideo(dir: File, messageId: Long, bytes: ByteArray): String? {
        val tmp = File(dir, "$messageId.video")
        return try {
            tmp.writeBytes(bytes)
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(tmp.absolutePath)
            val frame = retriever.frameAtTime
            retriever.release()
            if (frame == null) null
            else {
                val out = File(dir, "$messageId.jpg")
                java.io.FileOutputStream(out).use { stream ->
                    frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                }
                out.absolutePath
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
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
 * Fetches one MMS from the MMSC and returns the PDU bytes.
 *
 * GrapheneOS MmsService [isValidContentUri] rejects FileProvider destinations
 * (`Blocked unauthorized URI access`). The dest must be a `content://mms/…`
 * part the telephony provider owns. Injectable [download] so JVM tests never
 * touch SmsManager.
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
        val telephony = createTelephonyDest(context)
        if (telephony != null) {
            try {
                try {
                    download(context, locationUrl, telephony)
                } catch (_: Exception) {
                }
                val bytes = readBytes(context, telephony)
                    ?: readTelephonyParts(context, telephony)
                val harvested = readRecentInboxImages(context, telephony)
                val best = pickImagePdu(listOfNotNull(bytes) + harvested)
                if (best != null && best.isNotEmpty()) return best
            } finally {
                deleteTelephonyDest(context, telephony)
            }
        }
        return fetchViaFileProvider(context, locationUrl)
    }

    private suspend fun fetchViaFileProvider(context: Context, locationUrl: String): ByteArray? {
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
        try {
            download(context, locationUrl, dest)
        } catch (_: Exception) {
        }
        val bytes = when {
            file.length() > 0L -> try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
            else -> readBytes(context, dest)
        }
        file.delete()
        return bytes?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 90_000L
        private const val TAG = "TxxT-Mms"

        /**
         * A `content://mms/{id}/part/{partId}` URI MmsService is allowed to
         * write. FileProvider URIs are rejected as unauthorized.
         */
        internal fun createTelephonyDest(context: Context): Uri? {
            return try {
                val cr = context.contentResolver
                val msgUri = cr.insert(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Mms.READ, 0)
                        put(Telephony.Mms.SEEN, 0)
                        put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L)
                        put(Telephony.Mms.TEXT_ONLY, 0)
                    },
                ) ?: return null
                val msgId = ContentUris.parseId(msgUri)
                if (msgId < 0L) {
                    cr.delete(msgUri, null, null)
                    return null
                }
                val partUri = cr.insert(
                    Uri.parse("content://mms/$msgId/part"),
                    ContentValues().apply {
                        put(Telephony.Mms.Part.MSG_ID, msgId)
                        put(Telephony.Mms.Part.CONTENT_TYPE, "application/vnd.wap.mms-message")
                    },
                )
                if (partUri == null) {
                    cr.delete(msgUri, null, null)
                    null
                } else {
                    // Force the provider to allocate a backing file. MmsService
                    // writes through openFileDescriptor; an insert-only part
                    // can have no _data and the download lands empty.
                    try {
                        cr.openOutputStream(partUri)?.use { }
                    } catch (t: Exception) {
                        Log.w(TAG, "part dest not writable", t)
                    }
                    Log.i(TAG, "download dest $partUri")
                    partUri
                }
            } catch (t: Exception) {
                Log.w(TAG, "telephony dest failed", t)
                null
            }
        }

        internal fun deleteTelephonyDest(context: Context, dest: Uri) {
            val msgId = dest.pathSegments.firstOrNull { it.toLongOrNull() != null } ?: return
            try {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, msgId.toLong()),
                    null,
                    null,
                )
            } catch (_: Exception) {
            }
        }

        /**
         * GrapheneOS MmsService may persist the retrieve-conf as sibling parts
         * of the stub inbox row instead of writing the dest URI. Collect those
         * bytes before the stub is deleted.
         */
        internal fun readTelephonyParts(context: Context, dest: Uri): ByteArray? {
            val msgId = dest.pathSegments.firstOrNull { it.toLongOrNull() != null } ?: return null
            return readMessageParts(context, msgId)
        }

        /**
         * AOSP/GrapheneOS persistIfRequired stores the downloaded message as a
         * *new* inbox row. Our dest stub can stay empty. Copy image parts from
         * recently inserted inbox messages.
         */
        internal fun readRecentInboxImages(context: Context, dest: Uri): List<ByteArray> {
            val stubId = dest.pathSegments.firstOrNull { it.toLongOrNull() != null }
            val since = System.currentTimeMillis() / 1000L - 180L
            val cursor = try {
                context.contentResolver.query(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                    "${Telephony.Mms.DATE}>=?",
                    arrayOf(since.toString()),
                    "${Telephony.Mms.DATE} DESC",
                )
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            val out = ArrayList<ByteArray>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    if (stubId != null && id.toString() == stubId) continue
                    readMessageParts(context, id.toString())?.let { bytes -> out += bytes }
                }
            }
            return out
        }

        private fun readMessageParts(context: Context, msgId: String): ByteArray? {
            val cursor = try {
                context.contentResolver.query(
                    Telephony.Mms.Part.CONTENT_URI,
                    arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE),
                    "${Telephony.Mms.Part.MSG_ID}=?",
                    arrayOf(msgId),
                    null,
                )
            } catch (_: Exception) {
                null
            } ?: return null
            val chunks = ArrayList<Pair<String?, ByteArray>>()
            cursor.use {
                while (it.moveToNext()) {
                    val partId = it.getLong(0)
                    val mime = if (it.columnCount > 1) it.getString(1) else null
                    val partUri = ContentUris.withAppendedId(Telephony.Mms.Part.CONTENT_URI, partId)
                    val bytes = try {
                        context.contentResolver.openInputStream(partUri)?.use { stream ->
                            stream.readBytes()
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (bytes != null && bytes.isNotEmpty()) chunks += mime to bytes
                }
            }
            if (chunks.isEmpty()) return null
            chunks.firstOrNull { (mime, bytes) ->
                mime?.startsWith("image/") == true ||
                    mime?.startsWith("video/") == true ||
                    MmsRetrievedContentParser.parse(bytes).imageBytes != null
            }?.let { return it.second }
            return chunks.maxByOrNull { it.second.size }?.second
        }

        internal fun pickImagePdu(candidates: List<ByteArray>): ByteArray? {
            if (candidates.isEmpty()) return null
            return candidates.firstOrNull { chunk ->
                MmsRetrievedContentParser.parse(chunk).imageBytes != null
            } ?: candidates.maxByOrNull { it.size }
        }

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
