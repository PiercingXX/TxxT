package com.piercingxx.txxt.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

/**
 * Custom groups from XX-Dialer's `content://com.piercingxx.xxdialer.tier/groups`.
 * Same rows Contacts and People write. Fail-open to empty.
 */
object DialerGroups {

    val GROUPS_URI: Uri = Uri.parse("content://${DialerBusinessTier.AUTHORITY}/groups")
    const val COL_GROUP_NAME = "group_name"
    const val COL_LOOKUP_KEY = "lookup_key"

    fun ofContact(context: Context, lookupKey: String): List<String> =
        ofContact(context.contentResolver, lookupKey)

    fun ofContact(resolver: ContentResolver, lookupKey: String): List<String> {
        if (lookupKey.isEmpty()) return emptyList()
        return rows(resolver)
            .filter { it.second == lookupKey }
            .map { it.first }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun names(resolver: ContentResolver): List<String> =
        rows(resolver).map { it.first }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun keysNamed(context: Context, name: String): Set<String> =
        keysNamed(context.contentResolver, name)

    fun keysNamed(resolver: ContentResolver, name: String): Set<String> =
        rows(resolver).filter { it.first.equals(name, ignoreCase = true) }
            .map { it.second }
            .toSet()

    private fun rows(resolver: ContentResolver): List<Pair<String, String>> = try {
        resolver.query(GROUPS_URI, null, null, null, null)?.use { cursor ->
            val nameCol = cursor.getColumnIndex(COL_GROUP_NAME)
            val keyCol = cursor.getColumnIndex(COL_LOOKUP_KEY)
            if (nameCol < 0 || keyCol < 0) return@use emptyList()
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)?.trim().orEmpty()
                    val key = cursor.getString(keyCol)?.trim().orEmpty()
                    if (name.isNotEmpty() && key.isNotEmpty()) add(name to key)
                }
            }
        } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }
}
