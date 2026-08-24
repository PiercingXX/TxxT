package com.piercingxx.txxt.ui

import com.piercingxx.txxt.block.InboundFilter

/**
 * The blocking-management model for the settings screen (WS12 T3).
 *
 * DESIGN.md:76 lists "blocking management" among the settings the screen
 * exposes. This is the persistence-shaped model the screen edits: the set of
 * keyword rules, phrase rules, and blocked sender addresses the inbound filter
 * applies. It mirrors the pure-Kotlin, zero-`android.*` style of [SettingsStore]
 * and the core [com.piercingxx.txxt.core.Blocklist] so it is JVM-testable and
 * round-trips through the backup format (WS12 T4).
 *
 * The model is the settings-screen view of the blocklist; the *matching*
 * behaviour of the rules it holds is the core [com.piercingxx.txxt.core.BlockingFilter]'s,
 * and the actual application to inbound messages is the app-layer
 * [InboundFilter]. [filter] builds that filter from the current rules so the
 * settings the user edits reach the running application.
 */
class SettingsBlocking(
    keywords: Set<String> = emptySet(),
    phrases: Set<String> = emptySet(),
    blockedAddresses: Set<String> = emptySet(),
) {
    private val keywordRules = keywords.toMutableSet()
    private val phraseRules = phrases.toMutableSet()
    private val blockedAddresses = blockedAddresses.toMutableSet()

    /** Adds [term] as a keyword rule. Returns this model for chaining. */
    fun addKeyword(term: String): SettingsBlocking {
        keywordRules += term
        return this
    }

    /** Adds [term] as a phrase rule. Returns this model for chaining. */
    fun addPhrase(term: String): SettingsBlocking {
        phraseRules += term
        return this
    }

    /** Blocks [address] outright. Returns this model for chaining. */
    fun blockAddress(address: String): SettingsBlocking {
        blockedAddresses += address
        return this
    }

    /** Removes [term] from the keyword rules. Returns this model for chaining. */
    fun removeKeyword(term: String): SettingsBlocking {
        keywordRules -= term
        return this
    }

    /** Removes [term] from the phrase rules. Returns this model for chaining. */
    fun removePhrase(term: String): SettingsBlocking {
        phraseRules -= term
        return this
    }

    /** Unblocks [address]. Returns this model for chaining. */
    fun unblockAddress(address: String): SettingsBlocking {
        blockedAddresses -= address
        return this
    }

    /** The current keyword rules, as an unmodifiable snapshot. */
    fun keywords(): Set<String> = keywordRules.toSet()

    /** The current phrase rules, as an unmodifiable snapshot. */
    fun phrases(): Set<String> = phraseRules.toSet()

    /** The current blocked sender addresses, as an unmodifiable snapshot. */
    fun blockedAddresses(): Set<String> = blockedAddresses.toSet()

    /** Whether [term] is currently blocked as a keyword or a phrase rule. */
    fun isBlocked(term: String): Boolean = term in keywordRules || term in phraseRules

    /** Whether [address] is currently on the blocked-address list. */
    fun isAddressBlocked(address: String): Boolean = address in blockedAddresses

    /**
     * Builds the [InboundFilter] the running application applies, from the
     * current rules, the [starred] contact list, and the caller's known-contact
     * set. This is the wiring that makes the settings the user edits reach the
     * inbound message path. [quarantineUnknownSenders] passes through to
     * [InboundFilter]: quarantine stays opt-in until a quarantine store exists.
     */
    fun filter(
        starred: SettingsStarred,
        knownContacts: Set<String> = emptySet(),
        quarantineUnknownSenders: Boolean = false,
    ): InboundFilter =
        InboundFilter(
            knownContacts = knownContacts,
            blockedAddresses = blockedAddresses.toSet(),
            contentKeywords = keywordRules.toSet(),
            contentPhrases = phraseRules.toSet(),
            starredContacts = starred.contacts(),
            quarantineUnknownSenders = quarantineUnknownSenders,
        )
}