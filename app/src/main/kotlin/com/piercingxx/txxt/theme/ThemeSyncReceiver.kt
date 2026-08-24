package com.piercingxx.txxt.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * `BroadcastReceiver` for the xx-launcher's theme-change broadcast
 * (`xx.launcher.THEME_CHANGED`).
 *
 * Completes the theme auto-sync contract (`docs/PRIVACY.md §7`): the launcher
 * publishes its active theme; TxxT subscribes. On a broadcast the receiver reads
 * the carried preset name, resolves it to a [ThemePreset], and reports it to a
 * [ThemeController] via [ThemeController.onLauncherTheme] — which persists the
 * report into the `txxt_theme` store, so it survives process death and is read
 * by any controller constructed later (the applier's included). The manual-wins
 * precedence rule ("explicit beats ambient", `docs/PRIVACY.md §7`) lives in
 * ThemeController; T6's applier reads [ThemeController.effectiveTheme] and
 * re-applies the theme to the running UI.
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
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return

        val name = extractThemeName(intent) ?: return
        val preset = ThemePreset.fromDisplayName(name) ?: return
        controllerFactory(context).onLauncherTheme(preset)
    }

    companion object {
        /** The xx-launcher's theme-change broadcast action (proposed, `docs/PRIVACY.md §7`). */
        const val ACTION_THEME_CHANGED = "xx.launcher.THEME_CHANGED"
        /** Extra key the launcher carries the active preset name under. */
        const val EXTRA_THEME_NAME = "xx.launcher.extra.THEME_NAME"
    }
}