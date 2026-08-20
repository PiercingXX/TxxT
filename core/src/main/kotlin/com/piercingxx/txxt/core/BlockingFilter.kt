package com.piercingxx.txxt.core

/**
 * Pure-Kotlin blocking filter that decides whether an incoming message body
 * should be blocked based on configured keywords and phrases.
 *
 * - A **keyword** matches when it appears as a whole word in the body
 *   (case-insensitive), so "loan" matches "loan offers" but not "loans".
 * - A **phrase** matches when it appears as a substring of the body
 *   (case-insensitive), so "free money" matches "get free money now".
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class BlockingFilter(
    private val keywords: Set<String> = emptySet(),
    private val phrases: Set<String> = emptySet(),
) {

    /** True when [text] matches any configured keyword or phrase. */
    fun matches(text: String): Boolean = matchedTerm(text) != null

    /**
     * The configured keyword or phrase that [text] matches, or null when none
     * does. Returns the first match in keyword-then-phrase order so the reason
     * surfaced to the user is deterministic.
     */
    fun matchedTerm(text: String): String? {
        val lower = text.lowercase()
        for (keyword in keywords.sorted()) {
            if (containsWord(lower, keyword.lowercase())) return keyword
        }
        for (phrase in phrases.sorted()) {
            if (lower.contains(phrase.lowercase())) return phrase
        }
        return null
    }

    /** Whether [text] contains [word] as a whole word, case-insensitively. */
    private fun containsWord(text: String, word: String): Boolean {
        if (word.isEmpty()) return false
        var index = text.indexOf(word)
        while (index != -1) {
            val beforeWordBoundary = index == 0 || !text[index - 1].isLetterOrDigit()
            val afterWord = index + word.length
            val afterWordBoundary = afterWord == text.length || !text[afterWord].isLetterOrDigit()
            if (beforeWordBoundary && afterWordBoundary) return true
            index = text.indexOf(word, index + 1)
        }
        return false
    }
}