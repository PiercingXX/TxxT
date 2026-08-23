package com.piercingxx.txxt.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the launcher's conversation-list layout wiring (WS10 corrective-corrective
 * T2): `MainActivity` inflates `activity_main.xml` and resolves its RecyclerView by
 * the runtime view ID `R.id.recyclerView`.
 *
 * The assertions check the RUNTIME view ID — the compiled `R.id.recyclerView`
 * constant referenced from `MainActivity`'s Kotlin source and resolved by
 * `findViewById` at runtime — not the XML attribute string `@+id/recyclerView`.
 * The screen *rendering* on-device is the operator's (see the plan's deferred
 * verification), and instantiating `MainActivity` (an Activity) is not JVM-testable
 * without Robolectric (not in the offline cache). Following the established
 * source-reading pattern (ThreadWiringTest, ThreadLayoutTest), this locks the
 * runtime-ID wiring the launcher drives: if `onCreate` never inflates the layout
 * or never resolves the RecyclerView by `R.id.recyclerView`, the assertions fail.
 */
class MainActivityTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private fun layoutText(name: String): String {
        val file = sequenceOf(
            File("src/main/res/layout/$name"),
            File("app/src/main/res/layout/$name"),
        ).first { it.exists() }
        // Strip XML comments so the assertions check actual layout elements, not
        // the documentation prose that names the constraints.
        return file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
    private val activityMain: String by lazy { layoutText("activity_main.xml") }

    @Test
    fun `MainActivity inflates the launcher layout by its runtime resource`() {
        assertTrue(
            "MainActivity.onCreate must inflate the launcher layout via setContentView(R.layout.activity_main)",
            mainActivity.contains("setContentView(R.layout.activity_main)"),
        )
    }

    @Test
    fun `MainActivity resolves the RecyclerView by the runtime view ID`() {
        assertTrue(
            "MainActivity must resolve the conversation list by the runtime view ID R.id.recyclerView",
            mainActivity.contains("findViewById<RecyclerView>(R.id.recyclerView)"),
        )
    }

    @Test
    fun `MainActivity drives the swipe helper from the resolved RecyclerView`() {
        assertTrue(
            "MainActivity must attach the swipe helper to the resolved RecyclerView",
            mainActivity.contains("attachSwipeHelper(findViewById<RecyclerView>(R.id.recyclerView))"),
        )
    }

    @Test
    fun `the launcher layout hosts the RecyclerView the runtime ID resolves`() {
        assertTrue(
            "activity_main.xml must host a RecyclerView conversation list",
            activityMain.contains("RecyclerView"),
        )
        assertTrue(
            "activity_main.xml must identify the list as recyclerView",
            activityMain.contains("@+id/recyclerView"),
        )
    }
}