package com.piercingxx.txxt.service

import com.piercingxx.txxt.core.RoleSwitchCopy
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the in-app role-switch copy (todo.md T3): first-run and unset warn
 * that the local archive dies unless exported. The system role UI is out of
 * our hands.
 */
class RoleSwitchWarningTest {

    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    @Test
    fun `first-run copy is shown before the role request`() {
        val main = sourceText("MainActivity.kt")
        assertTrue(main.contains("explainThenRequestRole"))
        assertTrue(main.contains("RoleSwitchCopy.FIRST_RUN"))
    }

    @Test
    fun `settings unset warns then opens system default-apps`() {
        val settings = sourceText("ui/SettingsActivity.kt")
        assertTrue(settings.contains("unset_sms_button"))
        assertTrue(settings.contains("RoleSwitchCopy.UNSET"))
        assertTrue(settings.contains("unsetSettingsIntent"))
    }

    @Test
    fun `revocation toast uses the archive-honest copy`() {
        val monitor = sourceText("service/DefaultHandlerMonitor.kt")
        assertTrue(monitor.contains("RoleSwitchCopy.REVOKED"))
        assertTrue(RoleSwitchCopy.REVOKED.contains("archive", ignoreCase = true))
        assertTrue(RoleSwitchCopy.REVOKED.contains("export", ignoreCase = true))
    }
}
