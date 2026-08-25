package com.piercingxx.txxt.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * `BroadcastReceiver` for the xx-launcher's theme-change broadcast
 * (`xx.launcher.THEME_CHANGED`).
 *
 * Completes the theme auto-sync contract (`docs/PRIVACY.md §7`): the launcher
 * publishes its active theme; TxxT subscribes. Each broadcast carries two
 * extras — the active theme's display name and its resolved background ARGB —
 * and the receiver resolves the pair with [resolveSyncedTheme] into a
 * [ThemeGround], then reports it to a [ThemeController] via
 * [ThemeController.onLauncherGround] — which persists the report into the
 * `txxt_theme` store, so it survives process death and is read by any
 * controller constructed later (the applier's included). The manual-wins
 * precedence rule ("explicit beats ambient", `docs/PRIVACY.md §7`) lives in
 * ThemeController; T6's applier reads [ThemeController.effectiveGround] and
 * re-applies the theme to the running UI.
 *
 * Both extras matter, which is why the background is read at all: the family's
 * eighth theme, **Custom**, is a colour the user picked in the launcher and it
 * maps to no name the enum knows. Resolving on the name alone made every
 * Custom broadcast unresolvable, so TxxT dropped it silently and stayed on its
 * previous preset while every sibling app followed — the bug this seam fixes.
 * A Custom broadcast that arrives with no background still persists nothing:
 * keeping the ground the user already has beats guessing a colour (the
 * sibling-wide rule).
 *
 * The default controller is built over the same SharedPreferences (`txxt_theme`)
 * the launcher activity wires in `MainActivity`, so a broadcast reaches the same
 * persisted store. The controller factory, action string, extra key, and name
 * extractor are injectable so a JVM unit test can drive [onReceive] with a real
 * [Intent] without mocking the Android platform.
 */
class ThemeSyncReceiver(
    /**
     * Builds the [ThemeController] the receiver reports a launcher theme to.
     * Defaults to a controller over the app's `txxt_theme` SharedPreferences
     * (the same store MainActivity wires); injectable so a JVM unit test can
     * supply a controller over an in-memory store.
     */
    private val controllerFactory: (Context) -> ThemeController = { context ->
        ThemeController(
            ThemeStore(
                SharedPreferencesThemeKeyValueStore(
                    context.getSharedPreferences("txxt_theme", Context.MODE_PRIVATE)
                )
            )
        )
    },
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * proposed launcher action (`docs/PRIVACY.md §7`); injectable so a JVM unit
     * test can drive [onReceive] without the Android stub returning null.
     */
    private val action: String = ACTION_THEME_CHANGED,
    /**
     * Extra key the launcher carries the active preset name under. Injectable so
     * a JVM unit test can drive [onReceive] with a real [Intent].
     */
    private val extraThemeName: String = EXTRA_THEME_NAME,
    /**
     * Extracts the active preset name from [Intent]. Defaults to reading the
     * [extraThemeName] extra; injectable so a JVM unit test can supply a name
     * without depending on the extra-key constant.
     */
    private val extractThemeName: (Intent) -> String? = { intent ->
        intent.getStringExtra(extraThemeName)
    },
    /**
     * Extra key the launcher carries the active ground's ARGB under. Present
     * on every broadcast, and the ONLY source of truth for "Custom" — that
     * theme has no name-to-colour mapping anywhere in the family.
     */
    private val extraBackground: String = EXTRA_BACKGROUND,
    /**
     * Extracts the resolved background ARGB from [Intent], or null when the
     * broadcast carried none.
     *
     * The launcher writes a *signed* Int (0xFF131316 does not fit a positive
     * Int), so the mask is what turns it back into the unsigned 0xAARRGGBB
     * long the theme model speaks — without it a custom ground would arrive as
     * a huge negative number and derive nonsense colours. Injectable, like the
     * other seams, so a JVM unit test can drive [onReceive] without the
     * Android stub's Intent.
     */
    private val extractBackground: (Intent) -> Long? = { intent ->
        if (intent.hasExtra(extraBackground)) {
            intent.getIntExtra(extraBackground, 0).toLong() and 0xFFFFFFFFL
        } else {
            null
        }
    },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return

        // resolveSyncedTheme owns the whole decision (named preset, Custom via
        // the background extra, or nothing): keeping it there is what keeps
        // TxxT's answer identical to every sibling app's for the same payload.
        val ground = resolveSyncedTheme(
            extractThemeName(intent),
            extractBackground(intent),
        ) ?: return

        controllerFactory(context).onLauncherGround(ground)
    }

    companion object {
        /** The xx-launcher's theme-change broadcast action (proposed, `docs/PRIVACY.md §7`). */
        const val ACTION_THEME_CHANGED = "xx.launcher.THEME_CHANGED"
        /** Extra key the launcher carries the active preset name under. */
        const val EXTRA_THEME_NAME = "xx.launcher.extra.THEME_NAME"
        /** Extra key the launcher carries the resolved background ARGB int under. */
        const val EXTRA_BACKGROUND = "xx.launcher.extra.BACKGROUND"
    }
}