package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the dictation wiring: the manifest declares the mic permission, the
 * thread layout exposes a dictation affordance, and ThreadActivity wires that
 * affordance to on-device speech recognition whose result is routed through
 * [DictationInsert.insert] into the compose field.
 *
 * Following the established manifest/source-reading pattern (ThreadWiringTest,
 * ThreadLayoutTest), the on-device `SpeechRecognizer` hop — capturing audio and
 * returning recognized text — is not JVM-testable without a device (see the
 * plan's deferred verification). What the box locks here is the deterministic
 * route: the dictation button must reach a handler that calls
 * `DictationInsert.insert` with the recognized text, so the test FAILS if the
 * wire-in is missing. The insertion semantics themselves are proven
 * behaviourally by [DictationInsertTest].
 */
class DictationWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    private val threadLayout: String
        get() = sequenceOf(
            File("src/main/res/layout/activity_thread.xml"),
            File("app/src/main/res/layout/activity_thread.xml"),
        ).first { it.exists() }.readText()

    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }

    // ---- Mic permission (manifest) ----

    @Test
    fun `the manifest declares the mic permission`() {
        assertTrue(
            "AndroidManifest.xml must declare RECORD_AUDIO for SpeechRecognizer",
            manifestText.contains("android.permission.RECORD_AUDIO"),
        )
    }

    @Test
    fun `the manifest still declares no INTERNET permission`() {
        assertTrue(
            "TxxT must never declare INTERNET — dictation is on-device",
            !manifestText.contains("android.permission.INTERNET"),
        )
    }

    // ---- Dictation affordance (layout) ----

    @Test
    fun `the compose bar exposes a dictation affordance`() {
        assertTrue(
            "activity_thread.xml must declare a dictation (mic) button",
            threadLayout.contains("@+id/dictation_button"),
        )
    }

    // ---- ThreadActivity wiring (source read, fails if the call is absent) ----

    @Test
    fun `ThreadActivity wires the dictation button and routes through DictationInsert`() {
        assertTrue(
            "ThreadActivity must bind the dictation button",
            threadActivity.contains("dictationButton = findViewById(R.id.dictation_button)"),
        )
        assertTrue(
            "ThreadActivity must start dictation from the button",
            threadActivity.contains("dictationButton.setOnClickListener"),
        )
        assertTrue(
            "ThreadActivity must use SpeechRecognizer for on-device recognition",
            threadActivity.contains("SpeechRecognizer.createSpeechRecognizer"),
        )
        assertTrue(
            "ThreadActivity must route recognized text through DictationInsert.insert",
            threadActivity.contains("DictationInsert.insert"),
        )
    }
}
