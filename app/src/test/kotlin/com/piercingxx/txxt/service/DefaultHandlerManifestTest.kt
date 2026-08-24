package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the manifest declares every component the default-SMS-handler role
 * requires, with its component-scoped protections intact.
 *
 * The Android role grant itself (`RoleManager`) is not JVM-testable without a
 * device, but the OS only ever grants `android.app.role.SMS` to apps whose
 * manifest declares — and whose APK resolves — all four component types:
 * SMS_DELIVER / WAP_PUSH_DELIVER receivers, a SENDTO compose activity, and a
 * RESPOND_VIA_MESSAGE service. The deliver receivers must carry their
 * signature-held `android:permission` guards (BROADCAST_SMS /
 * BROADCAST_WAP_PUSH), and none of those signature permissions may leak into
 * `<uses-permission>` (they are platform-granted; asking for them is both
 * wrong and ungrantable). This test locks all of that.
 */
class DefaultHandlerManifestTest {

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

    /** The full XML element for the named component, or "" when absent. */
    private fun componentBlock(name: String): String {
        val regex = Regex(
            pattern = """<(activity|receiver|service)\b[^>]*android:name="$name"[\s\S]*?</\1>""",
        )
        return regex.find(manifestText)?.value.orEmpty()
    }

    private fun assertClassResolves(className: String) {
        // Throws ClassNotFoundException if the declared name is not a real class.
        Class.forName(className)
    }

    @Test
    fun `the manifest declares the SMS deliver receiver`() {
        assertTrue(
            "AndroidManifest.xml must declare the SmsDeliverReceiver component",
            componentBlock(".service.SmsDeliverReceiver").isNotEmpty(),
        )
    }

    @Test
    fun `the manifest declares the MMS deliver receiver`() {
        assertTrue(
            "AndroidManifest.xml must declare the MmsDeliverReceiver component",
            componentBlock(".service.MmsDeliverReceiver").isNotEmpty(),
        )
    }

    @Test
    fun `the manifest declares the compose activity`() {
        assertTrue(
            "AndroidManifest.xml must declare the ComposeActivity component",
            componentBlock(".ui.ComposeActivity").isNotEmpty(),
        )
    }

    @Test
    fun `the manifest declares the respond-via-message service`() {
        assertTrue(
            "AndroidManifest.xml must declare the RespondViaMessageService component",
            componentBlock(".service.RespondViaMessageService").isNotEmpty(),
        )
    }

    @Test
    fun `the SMS deliver receiver is guarded by BROADCAST_SMS`() {
        val block = componentBlock(".service.SmsDeliverReceiver")
        assertTrue(
            "SmsDeliverReceiver must declare android:permission BROADCAST_SMS",
            block.contains("""android:permission="android.permission.BROADCAST_SMS""""),
        )
        assertTrue(
            "SmsDeliverReceiver must listen on Telephony.SMS_DELIVER",
            block.contains("android.provider.Telephony.SMS_DELIVER"),
        )
    }

    @Test
    fun `the MMS deliver receiver is guarded by BROADCAST_WAP_PUSH and carries the MMS mime type`() {
        val block = componentBlock(".service.MmsDeliverReceiver")
        assertTrue(
            "MmsDeliverReceiver must declare android:permission BROADCAST_WAP_PUSH",
            block.contains("""android:permission="android.permission.BROADCAST_WAP_PUSH""""),
        )
        assertTrue(
            "MmsDeliverReceiver must listen on Telephony.WAP_PUSH_DELIVER",
            block.contains("android.provider.Telephony.WAP_PUSH_DELIVER"),
        )
        assertTrue(
            "MmsDeliverReceiver's filter must carry the MMS mime type",
            block.contains("""android:mimeType="application/vnd.wap.mms-message""""),
        )
    }

    @Test
    fun `the compose activity answers SENDTO across all four schemes`() {
        val block = componentBlock(".ui.ComposeActivity")
        assertTrue(
            "ComposeActivity must answer ACTION_SENDTO",
            block.contains("android.intent.action.SENDTO"),
        )
        listOf("sms", "smsto", "mms", "mmsto").forEach { scheme ->
            assertTrue(
                "ComposeActivity's filter must declare scheme $scheme",
                block.contains("""android:scheme="$scheme""""),
            )
        }
        // SmsApplication resolves the compose activity with MATCH_DEFAULT_ONLY,
        // and the role contract requires BROWSABLE so browsers can hand off —
        // dropping either category silently breaks role eligibility.
        assertTrue(
            "ComposeActivity's filter must declare category DEFAULT (MATCH_DEFAULT_ONLY resolution)",
            block.contains("android.intent.category.DEFAULT"),
        )
        assertTrue(
            "ComposeActivity's filter must declare category BROWSABLE",
            block.contains("android.intent.category.BROWSABLE"),
        )
    }

    @Test
    fun `the respond-via-message service answers RESPOND_VIA_MESSAGE under SEND_RESPOND_VIA_MESSAGE`() {
        val block = componentBlock(".service.RespondViaMessageService")
        assertTrue(
            "RespondViaMessageService must be guarded by SEND_RESPOND_VIA_MESSAGE",
            block.contains("""android:permission="android.permission.SEND_RESPOND_VIA_MESSAGE""""),
        )
        assertTrue(
            "RespondViaMessageService must answer ACTION_RESPOND_VIA_MESSAGE",
            block.contains("android.intent.action.RESPOND_VIA_MESSAGE"),
        )
    }

    @Test
    fun `every declared default-handler component resolves to a class`() {
        assertClassResolves("com.piercingxx.txxt.service.SmsDeliverReceiver")
        assertClassResolves("com.piercingxx.txxt.service.MmsDeliverReceiver")
        assertClassResolves("com.piercingxx.txxt.ui.ComposeActivity")
        assertClassResolves("com.piercingxx.txxt.service.RespondViaMessageService")
    }

    @Test
    fun `no signature-held deliver permission leaks into uses-permission`() {
        // BROADCAST_SMS / BROADCAST_WAP_PUSH / SEND_RESPOND_VIA_MESSAGE are
        // platform-held signatures: they belong ONLY in android:permission
        // attributes scoping our components. A <uses-permission> request would
        // be ungrantable and dishonest about what the app holds.
        listOf(
            "BROADCAST_SMS",
            "BROADCAST_WAP_PUSH",
            "SEND_RESPOND_VIA_MESSAGE",
        ).forEach { permission ->
            val offendingLines = manifestText.lines().filter { line ->
                line.contains(permission) && line.contains("uses-permission")
            }
            assertTrue(
                "$permission must stay component-scoped, never declared as a uses-permission",
                offendingLines.isEmpty(),
            )
        }
    }

    @Test
    fun `the stale WS7 comment is gone - the role comment names the real components`() {
        assertFalse(
            "The manifest must no longer say the role-completing receivers are future work",
            manifestText.contains("WS7's scope"),
        )
        assertTrue(
            "The updated role comment must point at the RoleManager runtime grant step",
            manifestText.contains("RoleManager"),
        )
    }
}
