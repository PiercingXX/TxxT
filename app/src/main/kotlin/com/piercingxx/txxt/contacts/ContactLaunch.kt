package com.piercingxx.txxt.contacts

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Opens XX-Contacts from a thread header tap.
 *
 * A saved contact lands on the detail screen via the same lookup-key extra
 * the contacts list uses. An unknown number uses SHOW_OR_CREATE so the
 * operator can add birthday / note on a new card instead of staring at a
 * dead tap.
 */
object ContactLaunch {

    const val CONTACTS_PACKAGE = "com.piercingxx.xxcontacts"
    const val DETAIL_ACTIVITY = "$CONTACTS_PACKAGE.ContactDetailActivity"
    const val EDIT_ACTIVITY = "$CONTACTS_PACKAGE.ContactEditActivity"
    const val EXTRA_LOOKUP_KEY = "com.piercingxx.xxcontacts.extra.LOOKUP_KEY"

    fun hasSavedContact(lookupKey: String?): Boolean = !lookupKey.isNullOrBlank()

    fun intent(lookupKey: String?, address: String): Intent {
        val key = lookupKey?.trim()?.takeIf { it.isNotEmpty() }
        return if (key != null) view(key) else create(address)
    }

    fun view(lookupKey: String): Intent =
        Intent()
            .setClassName(CONTACTS_PACKAGE, DETAIL_ACTIVITY)
            .putExtra(EXTRA_LOOKUP_KEY, lookupKey)

    fun create(address: String): Intent =
        Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT)
            .setData(Uri.parse("tel:$address"))
            .setClassName(CONTACTS_PACKAGE, EDIT_ACTIVITY)
}
