package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the quarantine review surface and the unknown-sender toggle to the
 * running app (todo.md T2): the hold is persistable, the toggle is reachable,
 * and the review activity is declared.
 */
class QuarantineWiringTest {

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

    @Test
    fun `the manifest declares the quarantine activity`() {
        assertTrue(manifestText.contains(".ui.QuarantineActivity"))
        Class.forName("com.piercingxx.txxt.ui.QuarantineActivity")
    }

    @Test
    fun `the blocking screen exposes the hold toggle and review`() {
        val blocking = sourceText("ui/BlockingActivity.kt")
        assertTrue(blocking.contains("quarantine_unknown_switch"))
        assertTrue(blocking.contains("KEY_QUARANTINE_UNKNOWN"))
        assertTrue(blocking.contains("QuarantineActivity::class.java"))
    }

    @Test
    fun `deliver receivers persist a hold instead of dropping it`() {
        val sms = sourceText("service/SmsDeliverReceiver.kt")
        val mms = sourceText("service/MmsDeliverReceiver.kt")
        assertTrue(sms.contains("QuarantineStore.persistInboundSms"))
        assertTrue(mms.contains("QuarantineStore.persistInboundMmsMetadata"))
        assertTrue(sms.contains("MessageDisposition.QUARANTINE"))
    }

    @Test
    fun `the launcher banner opens the review surface`() {
        val main = sourceText("MainActivity.kt")
        assertTrue(main.contains("quarantine_banner"))
        assertTrue(main.contains("QuarantineActivity::class.java"))
    }
}
