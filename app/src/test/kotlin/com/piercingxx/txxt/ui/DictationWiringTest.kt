package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the dictation posture after the UI rework: the thread screen exposes
 * NO dictation affordance and ThreadActivity carries no recognizer wiring —
 * the mic button was deliberately deleted from the compose bar, and it was the
 * feature's only entry point, so any resurfaced button or recognizer wiring is
 * a regression this test catches. The pure [DictationInsert] seam itself stays
 * (docs/FEATURES.md §Accessibility) so a future entry point can re-wire it,
 * and its insertion semantics are proven behaviourally below.
 *
 * Following the established manifest/source-reading pattern (ThreadWiringTest,
 * ThreadLayoutTest): the layout and activity source are read directly because
 * inflating them is not JVM-testable without Robolectric (not in the offline
 * cache — see the plan's deferred verification).
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

    // ---- Manifest posture ----

    // NOTE: this box used to also assert the manifest declares no INTERNET
    // permission, framed as "everything stays on-device". That framing was
    // struck: TxxT sends and receives SMS/MMS, which traverse the carrier
    // network through the system messaging stack, so the absence of INTERNET
    // means this process opens no sockets of its own — not that messages stay
    // on the handset. The manifest still declares no INTERNET, and the exact
    // permission set is machine-checked against the BUILT APK by
    // scripts/verify_privacy_claims.py; what is gone is the overstated claim,
    // not the posture.

    @Test
    fun `the manifest declares no RECORD_AUDIO permission`() {
        // The mic was declared for the compose bar's dictation button; the
        // rework deleted that button — its only entry point — so the app can
        // no longer exercise the permission. Asking the user for a mic it
        // cannot use is exactly what the permission list must never do.
        assertFalse(
            "TxxT must not declare RECORD_AUDIO while no code path can use it",
            manifestText.contains("android.permission.RECORD_AUDIO"),
        )
    }

    // ---- No dictation affordance (layout + activity) ----

    @Test
    fun `the compose bar exposes NO dictation affordance`() {
        assertFalse(
            "activity_thread.xml must not declare a dictation (mic) button — " +
                "the affordance was deliberately removed from the compose bar",
            threadLayout.contains("dictation_button"),
        )
    }

    @Test
    fun `ThreadActivity carries no recognizer wiring`() {
        assertFalse(
            "ThreadActivity must not bind a dictation button",
            threadActivity.contains("dictation_button"),
        )
        assertFalse(
            "ThreadActivity must not create a SpeechRecognizer — the deleted " +
                "mic button was the only entry point, so recognizer wiring is dead",
            threadActivity.contains("SpeechRecognizer"),
        )
    }

    // ---- DictationInsert seam (behavioural: the pure logic stays proven) ----

    @Test
    fun `dictation into a blank field becomes the whole field`() {
        assertEquals("hello there", DictationInsert.insert("", "hello there"))
        assertEquals("hello", DictationInsert.insert("   ", " hello "))
    }

    @Test
    fun `dictation mid-composition appends after a single space`() {
        assertEquals("on my way home", DictationInsert.insert("on my way", "home"))
        assertEquals("on my way home", DictationInsert.insert("on my way ", " home "))
    }

    @Test
    fun `blank recognized text leaves the field unchanged`() {
        assertEquals("draft", DictationInsert.insert("draft", ""))
        assertEquals("draft", DictationInsert.insert("draft", "   "))
    }
}
