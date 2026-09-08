package com.piercingxx.txxt.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AppLogTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        AppLog.resetForTest()
        dir = File(System.getProperty("java.io.tmpdir"), "txxt-log-${System.nanoTime()}")
        dir.mkdirs()
        AppLog.init(dir)
    }

    @After
    fun tearDown() {
        AppLog.resetForTest()
        dir.deleteRecursively()
    }

    @Test
    fun `ring keeps the newest lines and dump includes them`() {
        AppLog.i("mms", "retrieve start id=1")
        AppLog.e("mms", "retrieve failed", IllegalStateException("timeout"))
        val dump = AppLog.dump()
        assertTrue(dump.contains("I mms retrieve start id=1"))
        assertTrue(dump.contains("E mms retrieve failed"))
        assertTrue(dump.contains("IllegalStateException"))
    }

    @Test
    fun `persistCrash writes last-crash file with stack and recent lines`() {
        AppLog.w("send", "mms dest empty")
        AppLog.persistCrash(IllegalStateException("PlayerInfo"))
        val crash = AppLog.lastCrash()
        assertNotNull(crash)
        assertTrue(crash!!.contains("PlayerInfo"))
        assertTrue(crash.contains("IllegalStateException"))
        assertTrue(crash.contains("W send mms dest empty"))
        assertTrue(AppLog.crashFile()!!.isFile)
    }

    @Test
    fun `ring drops the oldest past RING_SIZE`() {
        repeat(AppLog.RING_SIZE + 5) { i ->
            AppLog.d("x", "n=$i")
        }
        val dump = AppLog.dump()
        val lines = dump.lines().filter { it.contains(" D x ") }
        assertEquals(AppLog.RING_SIZE, lines.size)
        assertTrue(lines.first().contains("n=5"))
        assertTrue(lines.last().contains("n=${AppLog.RING_SIZE + 4}"))
    }

    @Test
    fun `app start and settings logs are wired`() {
        val app = src("TxxtApp.kt")
        assertTrue(app.contains("AppLog.init"))
        assertTrue(app.contains("installCrashHandler"))
        val settings = src("ui/SettingsActivity.kt")
        assertTrue(settings.contains("AppLog.shareText()"))
        assertTrue(settings.contains("logs_button"))
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()
        assertTrue(manifest.contains("android:name=\".TxxtApp\""))
    }

    private fun src(rel: String): String {
        val fromModule = File("src/main/kotlin/com/piercingxx/txxt/$rel")
        if (fromModule.isFile) return fromModule.readText()
        val fromRepo = File("app/src/main/kotlin/com/piercingxx/txxt/$rel")
        assertTrue("missing $rel", fromRepo.isFile)
        return fromRepo.readText()
    }
}
