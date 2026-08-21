package com.piercingxx.txxt.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the manifest-declared receiver class names resolve to those classes (T3).
 *
 * The Android broadcast dispatch itself (`onReceive`, `Intent`, `Telephony`) is not
 * JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), but the OS only ever dispatches `SMS_RECEIVED` /
 * `WAP_PUSH_RECEIVED` to components that are (a) declared in the manifest and
 * (b) resolvable as concrete classes. This test locks both halves of that
 * contract: the manifest (app/src/main/AndroidManifest.xml) declares the
 * `.service.SmsReceiver` / `.service.MmsReceiver` names, and those names resolve
 * via `Class.forName` to the receiver classes the service package ships.
 */
class ReceiverManifestTest {

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
    fun `the manifest declares the SMS receiver`() {
        assertTrue(
            "AndroidManifest.xml must declare the SmsReceiver component",
            manifestText.contains(".service.SmsReceiver"),
        )
    }

    @Test
    fun `the manifest declares the MMS receiver`() {
        assertTrue(
            "AndroidManifest.xml must declare the MmsReceiver component",
            manifestText.contains(".service.MmsReceiver"),
        )
    }

    @Test
    fun `the declared SMS receiver name resolves to a class`() {
        assertClassResolves("com.piercingxx.txxt.service.SmsReceiver")
    }

    @Test
    fun `the declared MMS receiver name resolves to a class`() {
        assertClassResolves("com.piercingxx.txxt.service.MmsReceiver")
    }
}