package com.piercingxx.txxt.theme

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate

/**
 * Drives the running UI from the chosen theme (T6).
 *
 * Reads the effective theme from a [ThemeController] (the single place the
 * manual-wins precedence rule lives, T4), derives the concrete [ThemeTokens]
 * via [deriveTokens] (T1), and hands them to an `applyTokens` seam that paints
 * the running views. The seam is injected as a lambda so token derivation stays
 * JVM-testable without a device; the production seam is wired from each
 * activity's `applyTheme()` and paints that screen's chrome from the tokens.
 *
 * The applier is the T6 half of the theme contract (PRIVACY.md §7): the
 * settings screen (WS12) and the launcher-sync receiver (T5) report their
 * intent through the controller, and this applier reads [ThemeController.effectiveGround]
 * and re-applies the theme to the running UI — so a launcher broadcast or a
 * manual pick is reflected on screen.
 */
class ThemeApplier(
    private val controller: ThemeController,
    private val applyTokens: (ThemeTokens) -> Unit,
) {
    /**
     * Re-applies the current effective theme to the running UI: derives the
     * tokens for [ThemeController.effectiveGround] and paints them via the
     * [applyTokens] seam.
     *
     * Reads the GROUND rather than the preset-shaped view so a launcher
     * Custom broadcast actually reaches the screen: Custom has no
     * [ThemePreset] entry, so painting from `effectiveTheme` would silently
     * repaint the default ground instead of the colour the user picked.
     */
    fun apply() {
        applyTokens(deriveTokens(controller.effectiveGround))
    }

    companion object {

        /**
         * Night mode for a ground: dark presets → [AppCompatDelegate.MODE_NIGHT_YES],
         * Paper/Mist and light custom → [AppCompatDelegate.MODE_NIGHT_NO].
         *
         * Never [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM] — that value would
         * hand SearchView/dialogs back to the OS clock. Family rule is
         * ground-from-preset.
         */
        fun nightModeFor(isDark: Boolean): Int =
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

        /** Night mode for a [ThemeGround] — a total function of [ThemeGround.isDark]. */
        fun nightModeFor(ground: ThemeGround): Int = nightModeFor(ground.isDark)

        /**
         * Pins the process to [isDark]'s night mode so Material surfaces the
         * applier does not paint (SearchView, dialogs, spinners) follow the
         * chosen ground, not the OS clock. A no-op when the mode is already
         * set — [AppCompatDelegate.setDefaultNightMode] recreates activities
         * only when the value changes.
         */
        fun applyNightMode(isDark: Boolean) {
            val mode = nightModeFor(isDark)
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        /**
         * Paints [root]'s chrome from [tokens]: the ground on the root, then
         * every TextView/Button/Switch/EditText in the tree. Used by screens
         * that do not name every label (Settings, Blocking). Recycler rows
         * re-bind through their adapter after this runs.
         */
        fun paintChrome(root: View, tokens: ThemeTokens) {
            root.setBackgroundColor(tokens.background.toInt())
            paintTree(root, tokens)
        }

        private fun paintTree(view: View, tokens: ThemeTokens) {
            when (view) {
                is EditText -> {
                    view.setTextColor(tokens.text.toInt())
                    view.setHintTextColor(tokens.muted.toInt())
                }
                is Button -> view.setTextColor(tokens.accent.toInt())
                is Switch -> view.setTextColor(tokens.text.toInt())
                is TextView -> view.setTextColor(tokens.text.toInt())
                is ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        paintTree(view.getChildAt(i), tokens)
                    }
                }
            }
        }
    }
}
