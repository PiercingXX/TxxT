package com.piercingxx.txxt.theme

import android.content.Context
import android.content.Intent

/**
 * Ask xx-apps to make this in-app pick the suite theme. Existing
 * THEME_CHANGED receivers (including TxxT's) then converge.
 */
object SuiteThemeClient {

    const val APPS_PACKAGE = "com.piercingxx.apps"
    const val ACTION_SET_SUITE_THEME = "xx.apps.SET_SUITE_THEME"
    const val EXTRA_THEME_NAME = "xx.launcher.extra.THEME_NAME"
    const val EXTRA_BACKGROUND = "xx.launcher.extra.BACKGROUND"
    const val EXTRA_PRESET_KEY = "xx.apps.extra.PRESET_KEY"

    fun suitePresetKey(txxtKey: String): String = when (txxtKey) {
        "amoled-night" -> "amoled"
        "forest-night" -> "forest"
        "ocean-drift" -> "ocean"
        else -> txxtKey
    }

    fun requestIntent(preset: ThemePreset): Intent =
        Intent(ACTION_SET_SUITE_THEME)
            .setPackage(APPS_PACKAGE)
            .putExtra(EXTRA_PRESET_KEY, suitePresetKey(preset.key))
            .putExtra(EXTRA_THEME_NAME, preset.displayName)
            .putExtra(EXTRA_BACKGROUND, preset.background.toInt())

    fun request(context: Context, preset: ThemePreset) {
        runCatching { context.packageManager.getPackageInfo(APPS_PACKAGE, 0) }
            .onSuccess { context.sendBroadcast(requestIntent(preset)) }
    }
}
