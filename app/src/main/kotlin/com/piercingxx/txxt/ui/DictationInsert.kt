package com.piercingxx.txxt.ui

/**
 * The dictation-insertion seam (speech-to-text, pure).
 *
 * Pure Kotlin with zero `android.*` imports so the insertion logic is
 * JVM-testable without a device (docs/DESIGN.md §"Pure-Kotlin core"). The
 * on-device `SpeechRecognizer` hop — capturing audio and returning recognized
 * text — needs a mic and cannot run on the box; what this seam owns is the
 * deterministic part: how recognized speech text is inserted into the compose
 * field (docs/FEATURES.md §Accessibility, WS13 "dictation inserts into the
 * compose field"). ThreadActivity wires the framework service into this seam.
 */
object DictationInsert {

    /**
     * Inserts recognized speech text into the current compose-field text.
     *
     * The result is what the compose field should hold after dictation. When
     * the field is blank the recognized text becomes the whole field; when it
     * already holds text the recognized text is appended after a single space
     * so a mid-composition dictation continues the draft rather than
     * clobbering it. Blank recognized text leaves the field unchanged.
     */
    fun insert(currentText: String, recognized: String): String {
        val draft = currentText.trim()
        val spoken = recognized.trim()
        if (spoken.isEmpty()) return currentText
        return if (draft.isEmpty()) spoken else "$draft $spoken"
    }
}
