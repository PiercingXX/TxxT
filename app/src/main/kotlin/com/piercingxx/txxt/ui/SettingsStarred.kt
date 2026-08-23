package com.piercingxx.txxt.ui

/**
 * The starred-contacts list for the settings screen (WS12 T3).
 *
 * DESIGN.md:78 lists the starred-contacts list — the call-through list that
 * bypasses every suppression (PRIVACY.md §6) — among the settings the screen
 * exposes. This is the persistence-shaped model the screen edits: the set of
 * starred sender addresses. Starred is a first-class contact flag
 * (`docs/PRIVACY.md:110`), exported/imported with backup (WS12 T4), and
 * explicit — never inferred (`docs/PRIVACY.md:119`).
 *
 * It mirrors the pure-Kotlin, zero-`android.*` style of [SettingsStore] so it is
 * JVM-testable. The *bypass* behaviour is the core
 * [com.piercingxx.txxt.core.StarredBypass]'s; [SettingsBlocking.filter] feeds
 * this list into the app-layer [com.piercingxx.txxt.block.InboundFilter] so the
 * starred contacts the user edits reach the running application.
 */
class SettingsStarred(
    contacts: Set<String> = emptySet(),
) {
    private val starredContacts = contacts.toMutableSet()

    /** Stars [address]. Returns this list for chaining. */
    fun star(address: String): SettingsStarred {
        starredContacts += address
        return this
    }

    /** Unstars [address]. Returns this list for chaining. */
    fun unstar(address: String): SettingsStarred {
        starredContacts -= address
        return this
    }

    /** The current starred contacts, as an unmodifiable snapshot. */
    fun contacts(): Set<String> = starredContacts.toSet()

    /** Whether [address] is currently starred. */
    fun isStarred(address: String): Boolean = address in starredContacts
}