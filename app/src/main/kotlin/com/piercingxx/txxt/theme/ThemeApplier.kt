package com.piercingxx.txxt.theme

/**
 * Drives the running UI from the chosen theme (T6).
 *
 * Reads the effective theme from a [ThemeController] (the single place the
 * manual-wins precedence rule lives, T4), derives the concrete [ThemeTokens]
 * via [deriveTokens] (T1), and hands them to an `applyTokens` seam that paints
 * the running views. The seam is injected as a lambda so the class stays pure
 * JVM (no `android.*` imports) and the token derivation/application contract is
 * testable without a device; the production seam is wired in `ThreadActivity`
 * and paints the thread screen's chrome (ground, compose bar, input, send
 * button) from the tokens.
 *
 * The applier is the T6 half of the theme contract (PRIVACY.md §7): the
 * settings screen (WS12) and the launcher-sync receiver (T5) report their
 * intent through the controller, and this applier reads [ThemeController.effectiveTheme]
 * and re-applies the theme to the running UI — so a launcher broadcast or a
 * manual pick is reflected on screen.
 */
class ThemeApplier(
    private val controller: ThemeController,
    private val applyTokens: (ThemeTokens) -> Unit,
) {
    /**
     * Re-applies the current effective theme to the running UI: derives the
     * tokens for [ThemeController.effectiveTheme] and paints them via the
     * [applyTokens] seam.
     */
    fun apply() {
        applyTokens(deriveTokens(controller.effectiveTheme))
    }
}