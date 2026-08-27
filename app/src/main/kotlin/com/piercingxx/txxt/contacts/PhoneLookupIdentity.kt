package com.piercingxx.txxt.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * One PhoneLookup for LOOKUP_KEY and STARRED — the two provider facts TxxT
 * needs to honour xx-dialer's Business schedule. Display names stay on
 * [ContactNameResolver]; this path is notify-time only.
 *
 * Failures degrade to "unknown, not starred" so a missing grant never
 * silences a message.
 */
object PhoneLookupIdentity {

    data class Hit(
        val lookupKey: String?,
        val starred: Boolean,
    )

    val UNKNOWN = Hit(lookupKey = null, starred = false)

    fun lookup(context: Context, address: String): Hit {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return UNKNOWN
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return UNKNOWN
        }
        return runCatching { query(context, trimmed) }.getOrNull() ?: UNKNOWN
    }

    private fun query(context: Context, address: String): Hit {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.STARRED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return UNKNOWN
            val keyCol = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
            val starCol = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
            val key = if (keyCol >= 0) cursor.getString(keyCol) else null
            val starred = starCol >= 0 && cursor.getInt(starCol) != 0
            return Hit(key?.takeIf { it.isNotEmpty() }, starred)
        }
        return UNKNOWN
    }
}
