package com.piercingxx.txxt.ui

import com.piercingxx.txxt.block.LiveInboundFilter

/**
 * The persisted blocking/starred settings store (WS12-corrective T1).
 *
 * DESIGN.md:76 lists "blocking management" and DESIGN.md:78 the starred-contacts
 * list among the settings the screen exposes. [SettingsBlocking] and
 * [SettingsStarred] are the persistence-shaped models the settings screen edits;
 * this store is the persisted container for both — the sets of keyword rules,
 * phrase rules, blocked sender addresses, and starred contacts that survive a
 * process restart and drive the running inbound filter.
 *
 * It mirrors the pure-Kotlin, zero-`android.*` style of [SettingsStore] so it is
 * JVM-testable without a device. [toMap]/[fromMap] round-trip the store through
 * the backup string-map format (the same shape [SettingsBackup] uses for the
 * five-field store), so a blocking/starred snapshot survives the backup JSON
 * (WS12 T4) unchanged.
 *
 * The load-and-apply seam is [loadAndApply]: it builds the live
 * [SettingsBlocking]/[SettingsStarred] models from the persisted sets and hands
 * them to [LiveInboundFilter.apply], which rebuilds the process-wide
 * [com.piercingxx.txxt.block.InboundFilter] the inbound receivers read. This is
 * the path `SettingsActivity`'s blocking button drives (WS12-corrective T2); it
 * is what makes the persisted settings reach the running application.
 */
class SettingsBlockingStore(
    keywords: Set<String> = emptySet(),
    phrases: Set<String> = emptySet(),
    blockedAddresses: Set<String> = emptySet(),
    starredContacts: Set<String> = emptySet(),
) {
    private val keywordRules = keywords.toMutableSet()
    private val phraseRules = phrases.toMutableSet()
    private val blockedAddresses = blockedAddresses.toMutableSet()
    private val starredContacts = starredContacts.toMutableSet()

    /** The current keyword rules, as an unmodifiable snapshot. */
    fun keywords(): Set<String> = keywordRules.toSet()

    /** The current phrase rules, as an unmodifiable snapshot. */
    fun phrases(): Set<String> = phraseRules.toSet()

    /** The current blocked sender addresses, as an unmodifiable snapshot. */
    fun blockedAddresses(): Set<String> = blockedAddresses.toSet()

    /** The current starred contacts, as an unmodifiable snapshot. */
    fun starredContacts(): Set<String> = starredContacts.toSet()

    /** Builds the [SettingsBlocking] model the settings screen edits, from the persisted rules. */
    fun buildBlocking(): SettingsBlocking =
        SettingsBlocking(
            keywords = keywordRules.toSet(),
            phrases = phraseRules.toSet(),
            blockedAddresses = blockedAddresses.toSet(),
        )

    /** Builds the [SettingsStarred] model the settings screen edits, from the persisted contacts. */
    fun buildStarred(): SettingsStarred = SettingsStarred(contacts = starredContacts.toSet())

    /**
     * The load-and-apply seam: builds the [SettingsBlocking]/[SettingsStarred]
     * models from the persisted sets and applies them through
     * [LiveInboundFilter.apply], rebuilding the process-wide inbound filter the
     * receivers read. This is the *only* path the settings screen uses to make
     * persisted blocking/starred changes reach the running application, so its
     * behaviour is the wiring's behaviour.
     */
    fun loadAndApply() {
        LiveInboundFilter.apply(buildBlocking(), buildStarred())
    }

    companion object {
        /** The factory defaults for a fresh install: no rules, no stars. */
        fun defaults(): SettingsBlockingStore = SettingsBlockingStore()

        /** Backup settings-map key for the keyword rules. */
        const val KEY_KEYWORDS = "blockingKeywords"

        /** Backup settings-map key for the phrase rules. */
        const val KEY_PHRASES = "blockingPhrases"

        /** Backup settings-map key for the blocked sender addresses. */
        const val KEY_BLOCKED_ADDRESSES = "blockedAddresses"

        /** Backup settings-map key for the starred contacts. */
        const val KEY_STARRED_CONTACTS = "starredContacts"

        /** All blocking/starred backup-map keys. */
        val KEY_NAMES: List<String> = listOf(
            KEY_KEYWORDS,
            KEY_PHRASES,
            KEY_BLOCKED_ADDRESSES,
            KEY_STARRED_CONTACTS,
        )

        /** Separator for a set rendered into a single map string. */
        private const val SET_SEPARATOR = "\n"

        /** Renders [store] into the backup settings map, one string entry per set. */
        fun toMap(store: SettingsBlockingStore): Map<String, String> = mapOf(
            KEY_KEYWORDS to store.keywords().joinToString(SET_SEPARATOR),
            KEY_PHRASES to store.phrases().joinToString(SET_SEPARATOR),
            KEY_BLOCKED_ADDRESSES to store.blockedAddresses().joinToString(SET_SEPARATOR),
            KEY_STARRED_CONTACTS to store.starredContacts().joinToString(SET_SEPARATOR),
        )

        /**
         * Reconstructs a [SettingsBlockingStore] from the backup settings map.
         * Any set that is missing or empty falls back to an empty set, so a
         * backup written by an older or partial build imports cleanly.
         */
        fun fromMap(map: Map<String, String>): SettingsBlockingStore = SettingsBlockingStore(
            keywords = splitSet(map[KEY_KEYWORDS]),
            phrases = splitSet(map[KEY_PHRASES]),
            blockedAddresses = splitSet(map[KEY_BLOCKED_ADDRESSES]),
            starredContacts = splitSet(map[KEY_STARRED_CONTACTS]),
        )

        private fun splitSet(value: String?): Set<String> =
            value?.takeIf { it.isNotEmpty() }?.split(SET_SEPARATOR)?.toSet() ?: emptySet()
    }
}