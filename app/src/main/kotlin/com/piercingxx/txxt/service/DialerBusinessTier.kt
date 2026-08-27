package com.piercingxx.txxt.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

/**
 * Reads XX-Dialer's Business tier over the signature-gated
 * `content://com.piercingxx.xxdialer.tier` provider. Fail-open: any miss
 * (dialer missing, permission, dead provider) returns null and TxxT notifies
 * as if the sender were not Business.
 */
object DialerBusinessTier {

    const val AUTHORITY = "com.piercingxx.xxdialer.tier"
    val WINDOW_URI: Uri = Uri.parse("content://$AUTHORITY/window")
    val BIZ_URI: Uri = Uri.parse("content://$AUTHORITY/biz")

    const val COL_START_MINUTE = "start_minute"
    const val COL_END_MINUTE = "end_minute"
    const val COL_DAYS_MASK = "days_mask"
    const val COL_LOOKUP_KEY = "lookup_key"

    data class Snapshot(
        val startMinute: Int,
        val endMinute: Int,
        val daysMask: Int,
        val keys: Set<String>,
    )

    fun load(context: Context): Snapshot? = load(context.contentResolver)

    fun load(resolver: ContentResolver): Snapshot? {
        val window = queryWindow(resolver)
        val keys = queryKeys(resolver)
        if (window == null && keys == null) return null
        val (start, end, mask) = window
            ?: Triple(
                BusinessSchedule.DEFAULT_START_MINUTE,
                BusinessSchedule.DEFAULT_END_MINUTE,
                BusinessSchedule.ALL_DAYS,
            )
        return Snapshot(start, end, mask, keys.orEmpty())
    }

    private fun queryWindow(resolver: ContentResolver): Triple<Int, Int, Int>? = try {
        resolver.query(WINDOW_URI, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val startCol = cursor.getColumnIndex(COL_START_MINUTE)
            val endCol = cursor.getColumnIndex(COL_END_MINUTE)
            val maskCol = cursor.getColumnIndex(COL_DAYS_MASK)
            if (startCol < 0 || endCol < 0 || maskCol < 0) return@use null
            Triple(cursor.getInt(startCol), cursor.getInt(endCol), cursor.getInt(maskCol))
        }
    } catch (_: Throwable) {
        null
    }

    private fun queryKeys(resolver: ContentResolver): Set<String>? = try {
        resolver.query(BIZ_URI, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(COL_LOOKUP_KEY)
            if (col < 0) return@use emptySet()
            buildSet {
                while (cursor.moveToNext()) {
                    cursor.getString(col)?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }
    } catch (_: Throwable) {
        null
    }
}
