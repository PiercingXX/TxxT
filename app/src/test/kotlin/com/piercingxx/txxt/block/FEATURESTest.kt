package com.piercingxx.txxt.block

import com.piercingxx.txxt.ui.PhotoAttachment
import com.piercingxx.txxt.ui.PhotoStaging
import com.piercingxx.txxt.ui.SendStep
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Asserts the compiled feature list in docs/FEATURES.md stays truthful about
 * photo send, and — because a doc's claim is only as good as the wiring it
 * names — behaviour-verifies the photo-send seam the doc points at.
 *
 * The doc used to carry a stale claim ("photo send is not wired; the scrubber
 * is still …") at the end of the metadata-scrubbed bullet. Photo send IS wired:
 * the compose bar's ⊕ drives the system photo picker
 * (`ActivityResultContracts.PickVisualMedia`, `ImageOnly`) in `ThreadActivity`,
 * which stages the picked bytes into app-private storage via `PhotoStaging`
 * (no storage permission, no retained content-URI grant), then routes an
 * attached send through `SendPipeline.sendMms`. The markdown is read directly
 * (it is not JVM-inflatable) to lock the doc's wording; the seam it names is
 * driven against a real temp directory and the pure `PhotoAttachment.plan`
 * dispatch so "wired" is backed by executed behaviour, not just prose.
 */
class FEATURESTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private val featuresText: String
        get() = sequenceOf(
            File("../docs/FEATURES.md"),
            File("docs/FEATURES.md"),
        ).first { it.exists() }.readText()

    // ---- The doc no longer carries the stale claim ----

    @Test
    fun `the feature list no longer claims photo send is not wired`() {
        assertFalse(
            "docs/FEATURES.md must not resurrect the stale 'photo send is not " +
                "wired' claim — photo send IS wired via the system photo picker " +
                "(PickVisualMedia, ImageOnly) in ThreadActivity/PhotoStaging",
            // The specific stale phrase, not the generic substring "not wired":
            // the doc may legitimately use "not wired" for an unrelated feature
            // (e.g. "scheduled send is not wired"), and that must not trip this
            // assertion. Only the exact photo-send claim is the regression this
            // test exists to catch.
            featuresText.contains("photo send is not wired"),
        )
        assertFalse(
            "docs/FEATURES.md must not claim the scrubber is 'still' awaiting a " +
                "wired send path — the send path exists",
            featuresText.contains("scrubber is still"),
        )
    }

    @Test
    fun `the feature list states photo send over MMS is wired`() {
        assertTrue(
            "docs/FEATURES.md must state plainly that photo send IS wired, not "
                + "leave it as pending or unstated work",
            featuresText.contains("photo send is wired"),
        )
        assertTrue(
            "docs/FEATURES.md must name the wiring that makes photo send real " +
                "(the photo picker and the staging store), so the claim is checkable",
            featuresText.contains("PickVisualMedia") && featuresText.contains("PhotoStaging"),
        )
    }

    // ---- The wiring the doc names actually executes ----

    private lateinit var cacheDir: File
    private lateinit var directory: File

    @Before
    fun setUp() {
        cacheDir = File.createTempFile("txxt-features-cache-", "").apply {
            delete()
            mkdirs()
        }
        directory = PhotoStaging.directory(cacheDir)
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `a picked photo is staged into app-private storage and can be discarded`() {
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val staged = PhotoStaging.stage(directory, payload, nowMillis = 1_000L)

        assertNotNull("staging must produce an app-private copy", staged)
        assertTrue(staged!!.isFile)
        assertEquals(directory.absolutePath, staged.parentFile?.absolutePath)

        assertTrue("removing the attachment must delete its staged copy", PhotoStaging.discard(staged))
        assertFalse("the staged copy must not linger after discard", staged.exists())
    }

    @Test
    fun `an attached photo routes through the MMS step of the send plan`() {
        // The doc claims an attached photo is sent over MMS. PhotoAttachment.plan
        // is the pure dispatch that decides that: a photo with a caption expands
        // into the text step then the MMS-photo step, so the caption is never
        // silently dropped.
        assertEquals(
            listOf(SendStep.SMS_TEXT, SendStep.MMS_PHOTO),
            PhotoAttachment.plan(body = "caption", hasPhoto = true),
        )
        assertEquals(
            listOf(SendStep.MMS_PHOTO),
            PhotoAttachment.plan(body = "", hasPhoto = true),
        )
    }
}