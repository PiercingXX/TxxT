package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message

/**
 * Filters a thread's messages by query (case-insensitive over body).
 * A blank query returns the list unchanged. Pure — JVM-testable.
 */
object ThreadSearchFilter {
    fun filter(messages: List<Message>, query: String): List<Message> {
        val q = query.trim()
        if (q.isEmpty()) return messages
        val lower = q.lowercase()
        return messages.filter { it.body.lowercase().contains(lower) }
    }
}
