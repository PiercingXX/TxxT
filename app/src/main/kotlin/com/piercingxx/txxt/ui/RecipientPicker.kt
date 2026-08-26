package com.piercingxx.txxt.ui

import com.piercingxx.txxt.contacts.ContactEntry
import com.piercingxx.txxt.core.PhoneNumbers

/**
 * One row on the NEW-conversation picker: either a typed address the
 * operator can send to as-is, or a saved contact.
 */
sealed class RecipientRow {
    /** The operator typed [number] and can open a thread with it directly. */
    data class UseNumber(val number: String) : RecipientRow()

    /** A saved contact; tapping it opens a thread with [ContactEntry.number]. */
    data class Contact(val entry: ContactEntry) : RecipientRow()
}

/**
 * Pure mapping from (saved contacts, search query) → picker rows.
 *
 * The NEW affordance used to be a phone-pad dialog, so a contact could only
 * be reached by remembering their number. This object is the replacement
 * rule: type a name *or* a number, see matching contacts, and still be
 * able to open a raw address that is not in the book.
 *
 * Zero `android.*` imports so the rule is JVM-testable without a device.
 */
object RecipientPicker {

    /**
     * [contacts] reduced to those matching [query], starred first then
     * display-name. A blank query returns the whole book (same order). A
     * contact matches when the query appears in the name or in the number
     * (raw or digit-normalised, so `555` hits `+1 (555) 010-0100`).
     */
    fun filter(contacts: List<ContactEntry>, query: String): List<ContactEntry> {
        val sorted = contacts.sortedWith(
            compareByDescending<ContactEntry> { it.starred }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.number },
        )
        val q = query.trim()
        if (q.isEmpty()) return sorted
        val lower = q.lowercase()
        val digits = PhoneNumbers.normalize(q)
        return sorted.filter { entry ->
            entry.displayName.lowercase().contains(lower) ||
                entry.number.lowercase().contains(lower) ||
                (digits.isNotEmpty() && PhoneNumbers.normalize(entry.number).contains(digits))
        }
    }

    /**
     * The address to open if the operator meant the query itself as a
     * destination: a phone number (any digits) or an email-gateway address.
     * Names (`Ada`) and punctuation-only garbage return null — those are
     * search strings, not SMS destinations. The returned string is the
     * trimmed query, not a normalised form, so the thread keeps what they
     * typed.
     */
    fun typedAddress(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        if (PhoneNumbers.conversationKey(trimmed).isEmpty()) return null
        val hasDigits = trimmed.any { it.isDigit() }
        val isEmail = '@' in trimmed
        return if (hasDigits || isEmail) trimmed else null
    }

    /**
     * Rows the picker should show for [query]: a leading "use this number"
     * row when the query is itself a sendable address, then every matching
     * contact. The typed-number row is always first so a number that also
     * happens to match a name is still reachable as a raw address.
     */
    fun rows(contacts: List<ContactEntry>, query: String): List<RecipientRow> {
        val matches = filter(contacts, query)
        val typed = typedAddress(query)
        val out = ArrayList<RecipientRow>(matches.size + if (typed != null) 1 else 0)
        if (typed != null) out += RecipientRow.UseNumber(typed)
        matches.mapTo(out) { RecipientRow.Contact(it) }
        return out
    }

    /**
     * The address the IME submit action should open: the typed number when
     * there is one, otherwise the single remaining contact match. Two or
     * more name matches (or none) return null so submit does not guess.
     */
    fun addressOnSubmit(query: String, contacts: List<ContactEntry>): String? {
        typedAddress(query)?.let { return it }
        return filter(contacts, query).singleOrNull()?.number
    }
}
