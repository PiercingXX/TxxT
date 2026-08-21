package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts manifest-level constraints for the notification surface (T3):
 *  - No SYSTEM_ALERT_WINDOW (overlay) permission — the app never draws
 *    over other apps (bubbles, chat heads, floating windows);
 *  - Activities are declared and resolve to real classes.
 *
 * The Android component lifecycle itself is not JVM-testable without
 * Robolectric (not in the offline cache), but the OS only ever starts
 * components that are (a) declared in the manifest and (b) resolvable
 * as concrete classes. This test locks both halves of that contract.
 */
class NotificationManifestTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory, so the manifest is src/main/AndroidManifest.xml relative to
    // that; fall back to the workspace-root-relative path for robustness.
    private val manifest: File =
        sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }

    private val manifestText: String
        get() = manifest.readText()

    private fun assertClassResolves(className: String) {
        // Throws ClassNotFoundException if the declared name is not a real class.
        Class.forName(className)
    }

    @Test
    fun `no SYSTEM_ALERT_WINDOW overlay permission`() {
        assertFalse(
            "AndroidManifest.xml must not declare SYSTEM_ALERT_WINDOW — no overlay windows allowed",
            manifestText.contains("SYSTEM_ALERT_WINDOW"),
        )
    }

    @Test
    fun `the manifest declares the MainActivity`() {
        assertTrue(
            "AndroidManifest.xml must declare the MainActivity component",
            manifestText.contains(".MainActivity"),
        )
    }

    @Test
    fun `the declared MainActivity name resolves to a class`() {
        assertClassResolves("com.piercingxx.txxt.MainActivity")
    }
}
