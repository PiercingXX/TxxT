package com.piercingxx.txxt.block

import com.piercingxx.txxt.ui.SettingsBlocking
import com.piercingxx.txxt.ui.SettingsStarred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WS12-corrective T1 — the [LiveInboundFilter] wiring seam.
 *
 * Answers Nagatha's BLOCK finding: `SettingsActivity` never invoked
 * `SettingsBlocking`/`SettingsStarred` to configure the running app's blocking
 * logic, and the inbound receivers defaulted to an empty `InboundFilter()`. This
 * test drives the seam [LiveInboundFilter.apply] **by name** and asserts the
 * stored filter's behaviour, plus a source assertion that the settings screen's
 * blocking button routes through the seam — so the seam is not dead code.
 *
 * This would have failed before the fix: [LiveInboundFilter] did not exist
 * (compile error) and `SettingsActivity` never applied the settings to any
 * filter.
 */
class LiveInboundFilterTest {

    // ---- Behavioural: apply() stores the filter built from the settings ----

    @Test
    fun `apply with a blocked address blocks that sender`() {
        LiveInboundFilter.apply(
            SettingsBlocking().blockAddress("+1 555 8888"),
            SettingsStarred(),
        )
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
        assertEquals(BlockReason.Type.BLOCKED_LIST, reason!!.type)
    }

    @Test
    fun `apply with a starred contact bypasses a blocked address`() {
        LiveInboundFilter.apply(
            SettingsBlocking().blockAddress("+1 555 1000"),
            SettingsStarred().star("+1 555 1000"),
        )
        val (disposition, reason) = LiveInboundFilter.current.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.DELIVER, disposition)
        assertEquals(BlockReason.Type.STARRED_CONTACT_RULE, reason!!.type)
        assertTrue(reason.canOverride)
    }

    @Test
    fun `apply with an unstarred contact keeps the block`() {
        LiveInboundFilter.apply(
            SettingsBlocking().blockAddress("+1 555 1000"),
            SettingsStarred().star("+1 555 2000"),
        )
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 1000", "Hello")
        assertEquals(MessageDisposition.BLOCK, disposition)
    }

    @Test
    fun `apply replaces the previously stored filter`() {
        LiveInboundFilter.apply(
            SettingsBlocking().blockAddress("+1 555 8888"),
            SettingsStarred(),
        )
        LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())
        val (disposition, _) = LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")
        // No longer blocked after a fresh (empty) apply.
        assertEquals(MessageDisposition.QUARANTINE, disposition)
    }

    // ---- Wiring: the settings screen routes through the seam ----

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    @Test
    fun `SettingsActivity blocking button routes through LiveInboundFilter apply`() {
        val settingsActivity = sourceText("ui/SettingsActivity.kt")
        assertTrue(
            "SettingsActivity must call LiveInboundFilter.apply from the blocking button",
            settingsActivity.contains("LiveInboundFilter.apply"),
        )
        assertTrue(
            "SettingsActivity must no longer show the placeholder blocking Toast",
            !settingsActivity.contains("coming with WS12 T3"),
        )
    }
}