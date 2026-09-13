package com.piercingxx.txxt.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import com.piercingxx.txxt.log.AppLog
import kotlin.math.abs

/**
 * Copies photos (and 3GP stills) that MmsService already stored in the
 * telephony inbox into TxxT. Verizon's carrier stack often persists the
 * retrieve-conf there even when our dest URI stays empty.
 *
 * Addresses and bodies are not logged.
 */
object TelephonyInboxImport {

    private const val PREFS = "txxt_mms_import"
    private const val KEY_IDS = "imported_ids"
    private const val FROM_TYPE = 137 // PduHeaders.FROM
    private const val MATCH_WINDOW_MS = 180_000L
    /** Give up on inbox rows that never grew a sender or image. */
    internal const val STALE_MS = 10 * 60 * 1000L

    suspend fun importPending(context: Context): Int {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val imported = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        AppLog.i("mms", "inbox import start")
        val cr = app.contentResolver
        val cursor = try {
            cr.query(
                Telephony.Mms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                null,
                null,
                "${Telephony.Mms.DATE} DESC",
            )
        } catch (t: Exception) {
            AppLog.w("mms", "inbox import query failed", t)
            return 0
        }
        if (cursor == null) {
            AppLog.w("mms", "inbox import query null")
            return 0
        }
        AppLog.i("mms", "inbox import rows=${cursor.count}")
        var count = 0
        var skippedNoFrom = 0
        var skippedNoPart = 0
        val now = System.currentTimeMillis()
        val db = TxxTDatabase.instance(app)
        cursor.use {
            val idCol = it.getColumnIndex(Telephony.Mms._ID)
            val dateCol = it.getColumnIndex(Telephony.Mms.DATE)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if (imported.contains(id.toString())) continue
                val dateMillis = dateMillis(it.getLong(dateCol))
                val from = fromAddress(app, id)
                if (from == null) {
                    skippedNoFrom++
                    if (isStale(dateMillis, now)) imported += id.toString()
                    continue
                }
                val part = imageOrVideoPart(app, id)
                if (part == null) {
                    skippedNoPart++
                    if (isStale(dateMillis, now)) imported += id.toString()
                    continue
                }
                val convId = InboundStore.findOrCreateConversation(db.conversationDao(), from)
                val path = attachOrPersist(
                    app,
                    db,
                    convId,
                    from,
                    dateMillis,
                    part.first,
                    part.second,
                )
                imported += id.toString()
                if (path != null) count++
            }
        }
        prefs.edit().putStringSet(KEY_IDS, imported).apply()
        if (count > 0) AppLog.i("mms", "inbox import attached=$count")
        if (skippedNoFrom > 0 || skippedNoPart > 0) {
            AppLog.i("mms", "inbox import skipped no-from=$skippedNoFrom no-part=$skippedNoPart")
        }
        return count
    }

    internal fun dateMillis(dateSecs: Long): Long =
        if (dateSecs < 10_000_000_000L) dateSecs * 1000L else dateSecs

    internal fun isStale(dateMillis: Long, now: Long): Boolean =
        now - dateMillis >= STALE_MS

    internal fun pickPart(parts: List<Pair<String?, ByteArray>>): Pair<String, ByteArray>? {
        parts.firstOrNull { (mime, bytes) ->
            mime?.startsWith("image/") == true && bytes.size >= 16
        }?.let { return (it.first ?: "image/jpeg") to it.second }
        parts.firstOrNull { (mime, bytes) ->
            mime?.startsWith("video/") == true && bytes.size >= 16
        }?.let { return (it.first ?: "video/3gpp") to it.second }
        return parts.map { it.second }.let { chunks ->
            MmsContentFetcher.pickImagePdu(chunks)
        }?.let { bytes ->
            val parsed = com.piercingxx.txxt.core.MmsRetrievedContentParser.parse(bytes)
            val mime = parsed.imageMime
            val data = parsed.imageBytes
            if (mime != null && data != null) mime to data else null
        }
    }

    private suspend fun attachOrPersist(
        context: Context,
        db: TxxTDatabase,
        convId: Long,
        from: String,
        dateMillis: Long,
        mime: String,
        bytes: ByteArray,
    ): String? {
        val messages = db.messageDao()
        val existing = messages.getForConversation(convId).firstOrNull { row ->
            row.transport == "MMS" &&
                row.mediaPath.isNullOrBlank() &&
                abs(row.timestampMillis - dateMillis) <= MATCH_WINDOW_MS
        }
        val id = existing?.id ?: InboundStore.persistInboundMmsMetadata(
            db.conversationDao(),
            messages,
            from,
            dateMillis,
        )
        val path = MmsRetrieve.saveRetrievedImage(context, id, bytes, mime) ?: return null
        val row = messages.getById(id) ?: return path
        messages.upsert(
            row.copy(
                body = row.body.ifBlank { MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER },
                mediaPath = path,
                contentLocation = null,
            ),
        )
        return path
    }

    private fun fromAddress(context: Context, msgId: Long): String? {
        val uri = Uri.parse("content://mms/$msgId/addr")
        val cursor = try {
            context.contentResolver.query(uri, arrayOf("address", "type"), null, null, null)
        } catch (_: Exception) {
            null
        } ?: return null
        cursor.use {
            val addrCol = it.getColumnIndex("address")
            val typeCol = it.getColumnIndex("type")
            var fallback: String? = null
            while (it.moveToNext()) {
                val address = it.getString(addrCol)?.trim().orEmpty()
                if (address.isEmpty() || address == "insert-address-token") continue
                val type = if (typeCol >= 0) it.getInt(typeCol) else 0
                if (type == FROM_TYPE) return address
                if (fallback == null) fallback = address
            }
            return fallback
        }
    }

    private fun imageOrVideoPart(context: Context, msgId: Long): Pair<String, ByteArray>? {
        val cursor = try {
            context.contentResolver.query(
                Telephony.Mms.Part.CONTENT_URI,
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE),
                "${Telephony.Mms.Part.MSG_ID}=?",
                arrayOf(msgId.toString()),
                null,
            )
        } catch (_: Exception) {
            null
        } ?: return null
        val parts = ArrayList<Pair<String?, ByteArray>>()
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
                if (bytes != null && bytes.isNotEmpty()) parts += mime to bytes
            }
        }
        return pickPart(parts)
    }
}
