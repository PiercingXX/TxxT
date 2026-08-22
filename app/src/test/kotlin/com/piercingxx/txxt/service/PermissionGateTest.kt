package com.piercingxx.txxt.service

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the T1 send-permission gate.
 *
 * `PermissionGate.canSend` (PermissionGate.kt) is the decision seam `SendPipeline`
 * consumes before touching `SmsManager`: when the `SEND_SMS` permission is denied
 * the gate reports it (so the app "says so" on first launch) and returns `false`,
 * and `SendPipeline.sendSms` / `sendMms` skip the send — an outgoing message never
 * silently fails to send. The Android permission check / `SmsManager` dispatch is
 * not JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), so the gate's seams are driven directly and the
 * `SendPipeline` wire-in is locked by a source-reading assertion (the established
 * `ThreadWiringTest` / `ReceiverManifestTest` pattern) plus a behavioural check
 * that `sendSms` reaches the injected gate.
 */
class PermissionGateTest {

    private val context: Context = mockk(relaxed = true)

    private fun gate(hasPermission: Boolean, onDenied: () -> Unit): PermissionGate =
        PermissionGate(
            hasSendPermission = { _ -> hasPermission },
            onDenied = { _ -> onDenied() },
        )

    // ---- PermissionGate decision seam ----

    @Test
    fun `canSend returns false and reports when the SMS permission is denied`() {
        var reported = false
        val gate = gate(hasPermission = false, onDenied = { reported = true })

        val result = gate.canSend(context)

        assertFalse("a denied permission must block the send", result)
        assertTrue("the denial must be reported, never silent", reported)
    }

    @Test
    fun `canSend returns true and does not report when the SMS permission is granted`() {
        var reported = false
        val gate = gate(hasPermission = true, onDenied = { reported = true })

        val result = gate.canSend(context)

        assertTrue("a granted permission must allow the send", result)
        assertFalse("a granted permission must not report a denial", reported)
    }

    // ---- SendPipeline wire-in (rule: a test that constructs the gate directly
    // proves only the constructor; the running send path must reach it) ----

    @Test
    fun `sendSms consults the permission gate before sending`() {
        // A fake denying gate: sendSms must call canSend and, because it returns
        // false, return before reaching SmsManager (which would NPE on the JVM
        // mockable jar — so reaching it here would fail the test).
        var consulted = false
        val denying = PermissionGate(
            hasSendPermission = { _ -> consulted = true; false },
            onDenied = { _ -> },
        )

        SendPipeline.sendSms(context, destination = "+15550001111", body = "hello", gate = denying)

        assertTrue("SendPipeline must reach the gate's permission check", consulted)
    }

    @Test
    fun `sendSms source routes the send through the gate before SmsManager`() {
        // Locks the ordering that makes the "never silently fail" guarantee hold:
        // the gate is consulted and, when it denies, the send returns before any
        // SmsManager call. If the pipeline ever bypassed the gate, this fails.
        val source = sourceText("service/SendPipeline.kt")
        val gateIndex = source.indexOf("gate.canSend(context)")
        val sendIndex = source.indexOf("sendTextMessage(")
        assertTrue("SendPipeline must consult the gate", gateIndex >= 0)
        assertTrue("SendPipeline must send via SmsManager", sendIndex >= 0)
        assertTrue(
            "the gate must be consulted before the send so a denial skips it",
            gateIndex < sendIndex,
        )
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}