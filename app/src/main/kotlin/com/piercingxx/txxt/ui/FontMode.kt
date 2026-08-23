package com.piercingxx.txxt.ui

/**
 * The font-mode toggle (WS12 T2).
 *
 * DESIGN.md:29-30: Space Mono is the lead chrome font (names, timestamps,
 * unread counts) and JetBrains Mono the message body; both ship in `res/font/`
 * with the theme system (WS14). The toggle switches which mono font the app
 * uses. WS12 exposes and persists the choice; the actual font resource files
 * are WS14's scope.
 *
 * Pure Kotlin with zero `android.*` imports so the model is JVM-testable,
 * mirroring [SettingsStore].
 */
enum class FontMode {
    /** Space Mono — the brand's lead font. Default. */
    SPACE_MONO,

    /** JetBrains Mono. */
    JETBRAINS_MONO,
    ;

    companion object {
        /** The factory default for a fresh install. */
        fun defaults() = SPACE_MONO
    }
}