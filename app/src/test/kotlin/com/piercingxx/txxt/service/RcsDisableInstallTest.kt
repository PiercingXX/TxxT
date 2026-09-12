package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TxxT is SMS/MMS only. Pixel Shannon RCS still registers Chat with the
 * carrier, so inbound photos never become MMS. A sideloaded app cannot
 * `pm disable` another package; the disable has to ride the install that
 * already has adb. This locks that hook: the script, the packages it
 * touches, and Gradle running it after installDebug.
 */
class RcsDisableInstallTest {

    private fun read(vararg candidates: String): String =
        candidates.asSequence().map { File(it) }.first { it.exists() }.readText()

    private val script: String by lazy {
        read(
            "../scripts/disable_rcs.sh",
            "scripts/disable_rcs.sh",
        )
    }

    private val appBuild: String by lazy {
        read("build.gradle", "app/build.gradle")
    }

    @Test
    fun `installDebug is finalized by the RCS disable task`() {
        assertTrue(
            "app/build.gradle must register a disableRcs task",
            appBuild.contains("tasks.register(\"disableRcs\")") ||
                appBuild.contains("tasks.register('disableRcs')"),
        )
        assertTrue(
            "installDebug must run disableRcs",
            appBuild.contains("installDebug") && appBuild.contains("finalizedBy"),
        )
        assertTrue(
            "the disable task must run scripts/disable_rcs.sh",
            appBuild.contains("scripts/disable_rcs.sh"),
        )
    }

    @Test
    fun `the script disables Shannon RCS and not voice IMS`() {
        val active = script.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n")
        assertTrue(
            "must disable-user Shannon RCS",
            active.contains("pm disable-user") &&
                active.contains("com.shannon.rcsservice"),
        )
        assertTrue(
            "must turn off RCS single-registration",
            active.contains("src set-device-enabled false"),
        )
        assertTrue(
            "must turn off RCS capability exchange",
            active.contains("uce set-device-enabled false"),
        )
        assertFalse(
            "must not disable Shannon voice IMS",
            active.contains("com.shannon.imsservice"),
        )
        assertFalse(
            "must not disable all of IMS (that kills VoLTE)",
            active.contains("ims disable"),
        )
    }
}
