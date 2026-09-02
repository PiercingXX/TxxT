package com.piercingxx.txxt.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the photo-attachment wire-in: the compose bar carries the affordances
 * in the layout, `ThreadActivity` drives the Android photo picker and routes an
 * attached send through `SendPipeline.sendMms`, and the app's permission list
 * did **not** grow to pay for any of it.
 *
 * Inflating a layout and dispatching an activity result are not JVM-testable
 * without Robolectric (not in the offline cache — the plan's deferred
 * verification), so this follows the established manifest/source-reading
 * pattern of [ThreadLayoutTest] and [ThreadWiringTest] and locks the structural
 * contract. The behaviour that can be driven — the send plan, the indicator
 * text, the staging rules — is driven in [PhotoAttachmentTest] and
 * [PhotoStagingTest] instead of being asserted from source here.
 */
class PhotoAttachmentWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun read(vararg candidates: String): String =
        candidates.asSequence().map { File(it) }.first { it.exists() }.readText()

    private val activityThread: String by lazy {
        read("src/main/res/layout/activity_thread.xml", "app/src/main/res/layout/activity_thread.xml")
            // Strip comments so assertions check real elements, not the prose
            // that documents them.
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val threadActivity: String by lazy {
        // Comments are stripped so the assertions check the CODE, not the
        // doc-comments — several of which name `ACTION_GET_CONTENT` and
        // `READ_MEDIA_IMAGES` precisely to explain why the app does not use
        // them, and would otherwise trip the negative assertions below.
        read(
            "src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt",
            "app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt",
        )
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?m)^\\s*//.*$"), "")
    }

    private val manifest: String by lazy {
        read("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
    }

    private val buildScript: String by lazy { read("build.gradle", "app/build.gradle") }

    // ---- Layout: the affordance exists, in the compose bar's own idiom ----

    @Test
    fun `the compose bar carries a borderless monochrome attach glyph`() {
        val composeBar = activityThread.substring(activityThread.indexOf("@+id/compose_bar"))
        assertTrue(
            "the compose bar must carry an attach affordance",
            composeBar.contains("@+id/attach_button"),
        )
        assertTrue(
            "the attach button must show the ⊕ glyph, not a text label or an icon",
            composeBar.contains("android:text=\"${PhotoAttachment.ATTACH_GLYPH}\""),
        )
        assertTrue(
            "the attach button must use the bundled monospace face",
            composeBar.contains("@font/font_body"),
        )
        assertFalse(
            "no attachment affordance may use a vector drawable icon",
            activityThread.contains("drawableStart") ||
                activityThread.contains("android:src") ||
                activityThread.contains("ImageButton"),
        )
    }

    @Test
    fun `the attach affordance is borderless like send, with no filled pill`() {
        val attach = activityThread.substring(activityThread.indexOf("@+id/attach_button"))
        val declaration = attach.substring(0, attach.indexOf("/>"))
        assertTrue(
            "the attach button must be borderless",
            declaration.contains("?android:attr/borderlessButtonStyle"),
        )
        assertFalse(
            "the attach button must not carry a filled background",
            declaration.contains("android:background"),
        )
        // The layout-wide backgroundTint ban (ThreadLayoutTest) still holds.
        assertFalse(activityThread.contains("backgroundTint"))
    }

    @Test
    fun `the layout has a one-line attachment indicator that can be removed`() {
        assertTrue(
            "the compose bar must declare an attachment indicator row",
            activityThread.contains("@+id/attachment_row"),
        )
        assertTrue(
            "the indicator must be a TextView line, not a thumbnail card",
            activityThread.contains("@+id/attachment_label"),
        )
        assertTrue(
            "the indicator must be hidden until something is attached",
            activityThread.substring(activityThread.indexOf("@+id/attachment_row"))
                .substringBefore("@+id/attachment_label")
                .contains("android:visibility=\"gone\""),
        )
        assertTrue(
            "the indicator must carry a remove affordance",
            activityThread.contains("@+id/attachment_clear"),
        )
        assertTrue(
            "the remove affordance must be the ✕ glyph",
            activityThread.contains("android:text=\"${PhotoAttachment.REMOVE_GLYPH}\""),
        )
        assertFalse(
            "the indicator must stay text-first — no thumbnail, no image view",
            activityThread.contains("ImageView"),
        )
    }

    // ---- ThreadActivity: picker, staging, routing ----

    @Test
    fun `ThreadActivity uses the Android photo picker restricted to images`() {
        assertTrue(
            "the picker must be ActivityResultContracts.PickVisualMedia",
            threadActivity.contains("ActivityResultContracts.PickVisualMedia()"),
        )
        assertTrue(
            "the request must be a PickVisualMediaRequest",
            threadActivity.contains("PickVisualMediaRequest.Builder()"),
        )
        assertTrue(
            "the picker must be restricted to images",
            threadActivity.contains("ActivityResultContracts.PickVisualMedia.ImageOnly"),
        )
    }

    @Test
    fun `ThreadActivity never falls back to the wide-open document intents`() {
        // ACTION_GET_CONTENT and a READ_MEDIA_IMAGES grant would both widen what
        // the app can reach for no benefit the photo picker does not already
        // give. Neither may appear in the source.
        assertFalse(
            "the app must not use ACTION_GET_CONTENT",
            threadActivity.contains("ACTION_GET_CONTENT") ||
                threadActivity.contains("GET_CONTENT"),
        )
        assertFalse(
            "the app must not reference a media-read permission",
            threadActivity.contains("READ_MEDIA_IMAGES") ||
                threadActivity.contains("READ_EXTERNAL_STORAGE"),
        )
    }

    @Test
    fun `the picked photo is staged and the picker URI is never held`() {
        assertTrue(
            "the picked photo must be copied into app-private staging",
            threadActivity.contains("PhotoStaging.stage("),
        )
        assertTrue(
            "removing an attachment must delete its staged copy",
            threadActivity.contains("PhotoStaging.discard("),
        )
        assertTrue(
            "orphaned staged copies must be swept",
            threadActivity.contains("PhotoStaging.sweep("),
        )
        assertTrue(
            "the staged path must survive a saved-state restore",
            threadActivity.contains("onSaveInstanceState") &&
                threadActivity.contains("restoreStagedPhoto"),
        )
        assertFalse(
            "the picker's one-shot grant is not persistable and must not be taken",
            threadActivity.contains("takePersistableUriPermission"),
        )
    }

    @Test
    fun `send routes text through sendSms and an attachment through sendMms`() {
        assertTrue(
            "a text-only send must still go through SendPipeline.sendSms",
            threadActivity.contains("SendPipeline.sendSms"),
        )
        assertTrue(
            "an attached photo must go through SendPipeline.sendMms",
            threadActivity.contains("SendPipeline.sendMms"),
        )
        assertTrue(
            "the routing decision must come from the pure PhotoAttachment.plan seam",
            threadActivity.contains("PhotoAttachment.plan("),
        )
        assertTrue(
            "an outgoing MMS must be persisted before it is sent",
            threadActivity.contains("OutboundStore.persistOutgoingMms("),
        )
        assertTrue(
            "a sent photo must be copied into filesDir/mms so it toggles like inbound",
            threadActivity.contains("MmsRetrieve.saveRetrievedImage"),
        )
    }

    @Test
    fun `a failed send is surfaced, never silently dropped`() {
        assertTrue(
            "a failed step must reach the operator",
            threadActivity.contains("\"Not sent: "),
        )
        assertTrue(
            "an unreadable or unstageable photo must be reported",
            threadActivity.contains("\"Photo could not be attached\""),
        )
    }

    // ---- The permission list did not grow ----

    @Test
    fun `attaching a photo costs the app no new permission`() {
        // The photo picker's entire justification here: the operator picks one
        // photo and the app gets access to that one item, so the manifest's
        // short, comment-by-comment justified list is untouched.
        assertFalse(
            "the manifest must not declare a media-read permission",
            manifest.contains("READ_MEDIA_IMAGES") ||
                manifest.contains("READ_MEDIA_VISUAL_USER_SELECTED") ||
                manifest.contains("READ_EXTERNAL_STORAGE"),
        )
    }

    @Test
    fun `the picker contract is available at the pinned dependency version`() {
        // PickVisualMedia landed in androidx.activity 1.7.0; appcompat only
        // drags in 1.6.0, so the dependency has to be explicit or the compose
        // bar's attach button silently has no picker to open.
        assertTrue(
            "app/build.gradle must pin androidx.activity for the photo picker",
            buildScript.contains("androidx.activity:activity:"),
        )
        // initialize = false: the contract's static setup touches the Android
        // framework, which is stubbed in a JVM unit test. Resolving the class is
        // what proves the dependency is actually on the classpath.
        val loader = javaClass.classLoader!!
        Class.forName(
            "androidx.activity.result.contract.ActivityResultContracts\$PickVisualMedia",
            false,
            loader,
        )
        Class.forName("androidx.activity.result.PickVisualMediaRequest", false, loader)
    }
}
