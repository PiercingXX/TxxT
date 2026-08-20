package com.piercingxx.txxt.core

/**
 * Pure-Kotlin value type holding the set of blocking rules the filters apply,
 * split into keyword and phrase entries.
 *
 * - A **keyword** rule matches whole words (see [BlockingFilter]).
 * - A **phrase** rule matches as a substring (see [BlockingFilter]).
 *
 * The model supports adding and removing a rule, listing the current rules,
 * and answering whether a given term is currently blocked. It is the
 * persistence-shaped model that WS5's backup serialization and WS9's wiring
 * consume, so it is a plain value type with no `android.*` imports.
 */
class Blocklist(
    keywords: Set<String> = emptySet(),
    phrases: Set<String> = emptySet(),
) {

    private val keywordRules = keywords.toMutableSet()
    private val phraseRules = phrases.toMutableSet()

    /** Adds [term] as a keyword rule. Returns this list for chaining. */
    fun addKeyword(term: String): Blocklist {
        keywordRules += term
        return this
    }

    /** Adds [term] as a phrase rule. Returns this list for chaining. */
    fun addPhrase(term: String): Blocklist {
        phraseRules += term
        return this
    }

    /** Removes [term] from the keyword rules. Returns this list for chaining. */
    fun removeKeyword(term: String): Blocklist {
        keywordRules -= term
        return this
    }

    /** Removes [term] from the phrase rules. Returns this list for chaining. */
    fun removePhrase(term: String): Blocklist {
        phraseRules -= term
        return this
    }

    /** The current keyword rules, as an unmodifiable snapshot. */
    fun keywords(): Set<String> = keywordRules.toSet()

    /** The current phrase rules, as an unmodifiable snapshot. */
    fun phrases(): Set<String> = phraseRules.toSet()

    /** Whether [term] is currently blocked as a keyword or a phrase rule. */
    fun isBlocked(term: String): Boolean = term in keywordRules || term in phraseRules
}