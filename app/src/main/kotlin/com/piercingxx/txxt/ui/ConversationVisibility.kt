package com.piercingxx.txxt.ui

/**
 * Blocked-group people stay off the open inbox. A non-blank search is the
 * only way they appear, matching xx-contacts.
 */
object ConversationVisibility {

    fun showOnList(blocked: Boolean, searchQuery: String?): Boolean {
        if (!blocked) return true
        return !searchQuery.isNullOrBlank()
    }
}
