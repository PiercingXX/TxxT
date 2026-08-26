package com.piercingxx.txxt.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.piercingxx.txxt.core.PhoneNumbers

/**
 * One phone number the operator saved in the system contacts provider.
 *
 * A contact with two numbers is two entries — the picker is choosing a
 * destination, not a person-card. [displayName] is what the operator typed
 * for them; it falls back to [number] when the contact has no usable name
 * so a row is never blank.
 */
data class ContactEntry(
    val displayName: String,
    val number: String,
    val starred: Boolean = false,
)

/**
 * Enumerates phone numbers from the **system contacts provider** so the NEW
 * conversation picker can search by name, not only by digits.
 *
 * This is a different job from [ContactNameResolver]. The resolver answers
 * "what is this number called?" through `PhoneLookup` (one address in, one
 * label out). The directory answers "which saved numbers match this typing?"
 * and that requires reading `CommonDataKinds.Phone` — the table of every
 * number the operator stored. Using PhoneLookup here would be wrong: its
 * filter URI needs an address in the path and cannot list the book.
 *
 * **Read-only, local-only, denial is a first-class path.** The same
 * `READ_CONTACTS` grant the resolver uses; no write permission; a denied
 * grant, a throw, or an empty book all return an empty list so the picker
 * can still open a thread from a typed number (docs/PRIVACY.md §10).
 *
 * The Android-touching decisions are injectable seams (the
 * [ContactNameResolver] / [com.piercingxx.txxt.service.PermissionGate]
 * pattern) so the directory is drivable in a plain JVM unit test without
 * Robolectric (not in the offline cache).
 */
class ContactDirectory(
    private val context: Context,
    /**
     * Whether the app currently holds `READ_CONTACTS`. Consulted on every
     * [all] so a mid-session grant is honoured without reconstructing the
     * directory.
     */
    private val hasReadContacts: (Context) -> Boolean = { ctx ->
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    },
    /**
     * The provider listing: `(context) -> saved phone entries`. Defaults to
     * [queryPhoneContacts], the real `CommonDataKinds.Phone` query.
     */
    private val queryContacts: (Context) -> List<ContactEntry> = ::queryPhoneContacts,
) {

    /**
     * Every saved phone number, or an empty list when the provider cannot
     * be asked. Never throws.
     */
    fun all(): List<ContactEntry> = try {
        if (!hasReadContacts(context)) {
            emptyList()
        } else {
            queryContacts(context)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    companion object {

        /**
         * Turns one provider row into a [ContactEntry], or null when the
         * row has no usable SMS address. Blank names fall back to the
         * number so a picker row is never empty. Pure over its inputs —
         * JVM-testable.
         */
        fun entryOf(
            name: String?,
            number: String?,
            starred: Boolean = false,
        ): ContactEntry? {
            val trimmedNumber = number?.trim().orEmpty()
            if (trimmedNumber.isEmpty()) return null
            if (PhoneNumbers.conversationKey(trimmedNumber).isEmpty()) return null
            val trimmedName = name?.trim().orEmpty().ifEmpty { trimmedNumber }
            return ContactEntry(
                displayName = trimmedName,
                number = trimmedNumber,
                starred = starred,
            )
        }

        /**
         * Drops duplicate (name, number) pairs so a contact stored twice
         * with the same number does not occupy two picker rows. Different
         * names sharing a number, or one name with two numbers, stay.
         * Numbers compare through [PhoneNumbers.matches] so `+1 555 0100`
         * and `555-0100` collapse. Pure — JVM-testable.
         */
        fun dedupe(entries: List<ContactEntry>): List<ContactEntry> {
            val kept = ArrayList<ContactEntry>(entries.size)
            for (entry in entries) {
                val duplicate = kept.any { existing ->
                    existing.displayName.equals(entry.displayName, ignoreCase = true) &&
                        PhoneNumbers.matches(existing.number, entry.number)
                }
                if (!duplicate) kept += entry
            }
            return kept
        }

        /**
         * The real listing: every row on `CommonDataKinds.Phone.CONTENT_URI`.
         *
         * The cursor is closed through `use` on every path, including the
         * throw path — an unclosed contacts cursor leaks a Binder-backed
         * window. A missing column is treated as empty rather than throwing
         * so a vendor contacts app that omits STARRED still lists names.
         */
        fun queryPhoneContacts(context: Context): List<ContactEntry> {
            val rows = mutableListOf<ContactEntry>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val nameCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                while (cursor.moveToNext()) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val number = if (numberCol >= 0) cursor.getString(numberCol) else null
                    val starred = starredCol >= 0 && cursor.getInt(starredCol) != 0
                    entryOf(name, number, starred)?.let { rows += it }
                }
            }
            return dedupe(rows)
        }
    }
}
