package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the 1.5mm left/right bezel inset on every screen. The inset cannot
 * live on a fitsSystemWindows root — that flag overwrites padding with
 * system insets — so each layout must reference [R.dimen.txxt_edge_inset]
 * on inner content.
 */
class EdgeInsetTest {

    private fun resText(path: String): String {
        val file = sequenceOf(
            File("src/main/res/$path"),
            File("app/src/main/res/$path"),
        ).first { it.exists() }
        return file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    @Test
    fun `the edge inset dimen is one and a half millimetres`() {
        val dimens = resText("values/dimens.xml")
        assertTrue(
            "dimens.xml must declare txxt_edge_inset",
            dimens.contains("name=\"txxt_edge_inset\""),
        )
        assertTrue(
            "txxt_edge_inset must be 1.5mm",
            dimens.contains(">1.5mm<"),
        )
    }

    @Test
    fun `every activity layout applies the edge inset on inner content`() {
        val layouts = listOf(
            "layout/activity_main.xml",
            "layout/activity_thread.xml",
            "layout/activity_new_conversation.xml",
            "layout/activity_settings.xml",
            "layout/activity_blocking.xml",
        )
        for (path in layouts) {
            val xml = resText(path)
            assertTrue(
                "$path must apply @dimen/txxt_edge_inset (not on a fitsSystemWindows root)",
                xml.contains("@dimen/txxt_edge_inset"),
            )
            val rootStart = xml.indexOf('<', xml.indexOf("?>") + 2)
            val rootTag = xml.substring(rootStart, xml.indexOf('>', rootStart))
            assertTrue(
                "$path's fitsSystemWindows root must not carry the edge inset itself",
                !rootTag.contains("txxt_edge_inset"),
            )
        }
    }
}
