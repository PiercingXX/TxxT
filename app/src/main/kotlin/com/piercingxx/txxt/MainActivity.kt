package com.piercingxx.txxt

import android.app.Activity
import android.os.Bundle

/**
 * Launcher activity for TxxT.
 *
 * Declared in the manifest with the MAIN/LAUNCHER intent-filter
 * (`app/src/main/AndroidManifest.xml:36-44`). FLAG_SECURE is deferred
 * until the UI package lands (WS10/WS11 scope — see the plan's deferred
 * verification).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
