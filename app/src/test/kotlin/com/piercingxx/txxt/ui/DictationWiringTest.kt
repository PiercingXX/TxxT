package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `ThreadActivity hoists a single recognizer destroyed on teardown`() {
        val creations = Regex("""createSpeechRecognizer\(""").findAll(threadActivity).count()
        assertEquals(
            "dictation must reuse one hoisted SpeechRecognizer, not create a leaked one per tap",
            1,
            creations,
        )
        assertTrue(
            "the recognizer must live in a single nullable activity field",
            threadActivity.contains("private var recognizer: SpeechRecognizer? = null"),
        )
        assertTrue(
            "the recognizer must be created lazily into that single field",
            threadActivity.contains("recognizer ?: run {") &&
                threadActivity.contains(".also { recognizer = it }"),
        )
        assertTrue(
            "the recognizer must be guarded by isRecognitionAvailable before creation",
            threadActivity.contains("SpeechRecognizer.isRecognitionAvailable(this)"),
        )
        assertTrue(
            "the created recognizer must be destroyed exactly once, in onDestroy",
            threadActivity.contains("recognizer?.destroy()"),
        )
    }

    @Test
    fun `ThreadActivity requests RECORD_AUDIO at runtime before listening`() {
        assertTrue(
            "dictation must consult the PermissionGate.canRecord seam before listening",
            threadActivity.contains("canRecord(this)"),
        )
        val canRecordIndex = threadActivity.indexOf("canRecord(this)")
        val requestIndex = threadActivity.indexOf("ActivityCompat.requestPermissions")
        assertTrue(
            "a denied mic permission must trigger a runtime request instead of silent listening",
            requestIndex >= 0,
        )
        assertTrue(
            "the permission check must gate the request and both must precede any listening",
            canRecordIndex >= 0 && canRecordIndex < requestIndex,
        )
        assertTrue(
            "the runtime request must ask for RECORD_AUDIO",
            threadActivity.contains("Manifest.permission.RECORD_AUDIO"),
        )
        assertTrue(
            "the request needs a request code the activity owns",
            threadActivity.contains("REQUEST_RECORD_AUDIO"),
        )
    }

    // ---- Behavioural proof of the error mapping (pure companion function; the
    // on-device Toast hop is deferred like the recognition itself).

    @Test
    fun `dictation errors map to short human-readable lines`() {
        assertFalse(
            ThreadActivity.errorMessage(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
                .isBlank(),
        )
        assertFalse(
            ThreadActivity.errorMessage(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                .isBlank(),
        )
        assertFalse(
            ThreadActivity.errorMessage(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                .isBlank(),
        )
    }

    @Test
    fun `a permission failure names the mic permission`() {
        val message = ThreadActivity.errorMessage(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertTrue(
            "an insufficient-permissions error must point at the mic permission",
            message.contains("permission", ignoreCase = true),
        )
    }

    @Test
    fun `unknown dictation errors fall back to a generic line`() {
        assertEquals(
            ThreadActivity.errorMessage(-999),
            ThreadActivity.errorMessage(Int.MIN_VALUE),
        )
    }
}
