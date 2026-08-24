package com.piercingxx.txxt.service

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the T6 default-SMS-handler revocation warning and the
 * runtime role-request seam.
 *
 * `DefaultHandlerMonitor.warnIfRevoked` (DefaultHandlerMonitor.kt) is the decision
 * seam the running app consumes at boot: when this app is no longer the platform's
 * default SMS handler (the user switched the default SMS app, or a factory reset /
 * app update dropped the grant), the monitor reports it loudly (so the revocation
 * is never silent) and returns `false`. `roleRequest` is the companion seam the
 * launcher consumes on create: it hands back the system intent that asks for the
 * role (API 29+ RoleManager) or null when there is nothing to request — held,
 * unavailable, or API <29 where no RoleManager exists and the user grants the
 * role manually through system settings. The platform default-SMS lookup /
 * `Toast` dispatch / RoleManager construction is not JVM-testable without
 * Robolectric (not in the offline cache), so the monitor's seams are driven
 * directly and the `BootReceiver` wire-in is locked by a source-reading assertion
 * (the established `PermissionGateTest` / `RebootReconcileTest` pattern).
 */
class DefaultHandlerWarningTest {

    private val context: Context = mockk(relaxed = true)

    private fun monitor(defaultPackage: String?, onRevoked: () -> Unit): DefaultHandlerMonitor =
        DefaultHandlerMonitor(
            defaultSmsPackage = { _ -> defaultPackage },
            onRevoked = { _ -> onRevoked() },
        )

    // ---- DefaultHandlerMonitor decision seam ----

    @Test
    fun `warnIfRevoked returns false and warns when this app is no longer the default handler`() {
        // The platform's default SMS package is a different app — the role was revoked.
        var warned = false
        val monitor = monitor(defaultPackage = "com.other.sms", onRevoked = { warned = true })

        val result = monitor.warnIfRevoked(context)

        assertFalse("a revoked role must report the app is no longer the default", result)
        assertTrue("the revocation must be warned loudly, never silent", warned)
    }

    @Test
    fun `warnIfRevoked returns false and warns when no app is the default handler`() {
        // getDefaultSmsPackage returns null when no app holds the role.
        var warned = false
        val monitor = monitor(defaultPackage = null, onRevoked = { warned = true })

        val result = monitor.warnIfRevoked(context)

        assertFalse("a missing default handler must report the app is not the default", result)
        assertTrue("the missing role must be warned loudly, never silent", warned)
    }

    @Test
    fun `warnIfRevoked returns true and does not warn when this app is the default handler`() {
        val appPackage = "com.piercingxx.txxt"
        var warned = false
        val monitor = monitor(defaultPackage = appPackage, onRevoked = { warned = true })
        val selfContext = mockk<Context>(relaxed = true)
        io.mockk.every { selfContext.packageName } returns appPackage

        val result = monitor.warnIfRevoked(selfContext)

        assertTrue("an intact role must report the app is still the default", result)
        assertFalse("an intact role must not warn", warned)
    }

    // ---- DefaultHandlerMonitor role-request seam ----

    @Test
    fun `roleRequest returns the intent when the seam supplies one`() {
        // API 29+ with the role available and not held: the monitor surfaces
        // the system request intent for the caller to start.
        val intent = mockk<Intent>()
        val monitor = DefaultHandlerMonitor(roleRequestIntent = { _ -> intent })

        assertEquals(intent, monitor.roleRequest(context))
    }

    @Test
    fun `roleRequest returns null when the seam supplies nothing`() {
        // Role already held, unavailable, or API <29 (no RoleManager — manual
        // grant): nothing to request, so the caller must not start anything.
        val monitor = DefaultHandlerMonitor(roleRequestIntent = { _ -> null })

        assertNull(monitor.roleRequest(context))
    }

    // ---- Wire-in (rule: a test that constructs the monitor directly proves only
    // the constructor; the running boot path must reach it) ----

    @Test
    fun `BootReceiver source reaches the DefaultHandlerMonitor on boot`() {
        val source = sourceText("service/RebootReconcile.kt")
        assertTrue(
            "BootReceiver must construct a DefaultHandlerMonitor",
            source.contains("DefaultHandlerMonitor()"),
        )
        assertTrue(
            "BootReceiver must reach the monitor's revocation check",
            source.contains("warnIfRevoked("),
        )
    }

    @Test
    fun `the monitor class resolves`() {
        Class.forName("com.piercingxx.txxt.service.DefaultHandlerMonitor")
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}