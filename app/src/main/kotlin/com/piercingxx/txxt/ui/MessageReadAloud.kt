package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection

/**
 * The read-aloud-text seam (text-to-speech, pure).
 *
 * Pure Kotlin with zero `android.*` imports so the mapping is JVM-testable
 * without a device (docs/DESIGN.md §"Pure-Kotlin core"). The on-device
 * `TextToSpeech` hop — producing audible speech — needs a speaker and cannot
 * run on the box; what this seam owns is the deterministic part: what text is
 * read aloud when the user taps a message (docs/FEATURES.md §Accessibility,
 * WS13 "a message is read aloud"). ThreadActivity wires the framework service
 * into this seam as its only source of the spoken text.
 */
object MessageReadAloud {

    /**
     * Maps a message to the text spoken when the user taps it.
     *
     * An incoming message is prefixed with a short sender label so the listener
     * knows who spoke (`"from <sender>: <body>"`); an outgoing message is read
     * as the body alone. A blank or whitespace-only body yields an empty string
     * so the caller can skip the `speak` call — blank messages are never read
     * aloud.
     */
    fun speakable(message: Message): String {
        val body = message.body.trim()
        if (body.isEmpty()) return ""
        return when (message.direction) {
            MessageDirection.INCOMING ->
                "from ${message.senderAddress.orEmpty()}: $body"
            MessageDirection.OUTGOING -> body
        }
    }
}