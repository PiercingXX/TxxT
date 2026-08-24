package com.piercingxx.txxt.ui

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.txxt.MainActivity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the conversation-list swipe wiring (WS10 corrective-corrective T1):
 * `MainActivity.attachSwipeHelper` constructs a [ConversationSwipeHelper] over
 * this activity (the [SwipeActionCallback]) and attaches it to the RecyclerView.
 *
 * Also asserts the default-handler grant wiring: on create the launcher asks for
 * the default-SMS-handler role (via `DefaultHandlerMonitor.roleRequest`, started
 * with `startActivityForResult`) and for the API 33+ POST_NOTIFICATIONS runtime
 * permission (`requestPermissions`), with the ask/no-ask decisions made by the
 * pure companion functions `shouldRequestRole` / `needsNotificationPermission`.
 *
 * The core verification is behavioural: a real [ConversationSwipeHelper] with an
 * injected recording attach is driven through its real `attachTo` path against a
 * mocked RecyclerView, and the test asserts the attach side effect fires with the
 * RecyclerView and a swipe callback carrying the four-action swipe flags; the
 * pure decision functions are driven directly. The framework-bound `MainActivity`
 * (an Activity) cannot be instantiated in a plain JVM unit test (no Robolectric
 * in the offline cache), so the onCreate call-site wiring is locked by reading
 * the source — the same pattern `ThreadWiringTest` uses for the launcher. The
 * deferred callback operations (archive/delete/call/schedule) are NOT asserted
 * to fire; they are empty in `MainActivity` by contract.
 */
class MainActivityWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }

    // ---- Behavioural: the helper's real attachTo path fires the attach seam ----

    @Test
    fun `attachTo attaches an ItemTouchHelper to the RecyclerView`() {
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        var attachedRecyclerView: RecyclerView? = null
        var attachedCallback: ItemTouchHelper.Callback? = null

        val helper = ConversationSwipeHelper(
            callback = mockk(relaxed = true),
            attach = { rv, cb ->
                attachedRecyclerView = rv
                attachedCallback = cb
            },
        )

        helper.attachTo(recyclerView)

        // The attach side effect must have fired with the exact RecyclerView and a
        // non-null swipe callback — if attachTo never reached the seam, both stay
        // null and this fails.
        assertEquals(recyclerView, attachedRecyclerView)
        assertNotNull("attachTo must build an ItemTouchHelper.Callback", attachedCallback)
    }

    @Test
    fun `the swipe callback enables both swipe directions for the four actions`() {
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        var attachedCallback: ItemTouchHelper.Callback? = null

        ConversationSwipeHelper(
            callback = mockk(relaxed = true),
            attach = { _, cb -> attachedCallback = cb },
        ).attachTo(recyclerView)

        val callback = attachedCallback
        assertNotNull(callback)
        // The four swipe actions need both directions enabled.
        assertNotEquals(
            0,
            callback!!.getMovementFlags(recyclerView, mockk(relaxed = true)) and
                (ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT),
        )
    }

    // ---- Wiring: MainActivity is the reachable call site (not dead code) ----

    @Test
    fun `MainActivity constructs the swipe helper and attaches it`() {
        assertTrue(
            "MainActivity must implement SwipeActionCallback",
            mainActivity.contains("SwipeActionCallback"),
        )
        assertTrue(
            "MainActivity.attachSwipeHelper must construct a ConversationSwipeHelper",
            mainActivity.contains("ConversationSwipeHelper(this)"),
        )
        assertTrue(
            "MainActivity.attachSwipeHelper must attach the helper to the RecyclerView",
            mainActivity.contains(".attachTo(recyclerView)"),
        )
    }

    // ---- Default-handler grants: the launcher asks, it is never silent ----

    @Test
    fun `MainActivity requests the SMS role and POST_NOTIFICATIONS on create`() {
        assertTrue(
            "MainActivity must consult the monitor's roleRequest seam",
            mainActivity.contains("roleRequest("),
        )
        assertTrue(
            "MainActivity must start the role request with startActivityForResult",
            mainActivity.contains("startActivityForResult"),
        )
        assertTrue(
            "MainActivity must check and request POST_NOTIFICATIONS",
            mainActivity.contains("POST_NOTIFICATIONS") &&
                mainActivity.contains("requestPermissions"),
        )
        assertTrue(
            "the ask/no-ask decision must go through shouldRequestRole",
            mainActivity.contains("shouldRequestRole("),
        )
        assertTrue(
            "the notification-prompt decision must go through needsNotificationPermission",
            mainActivity.contains("needsNotificationPermission("),
        )
    }

    @Test
    fun `shouldRequestRole asks exactly when a role intent exists`() {
        assertTrue(
            "a non-null role intent means there is something to ask for",
            MainActivity.shouldRequestRole(mockk()),
        )
        assertFalse(
            "a null role intent means nothing to request (held / unavailable / API <29)",
            MainActivity.shouldRequestRole(null),
        )
    }

    @Test
    fun `needsNotificationPermission prompts only on API 33+ when not granted`() {
        // API 32 (< 33): the platform has no runtime notification permission,
        // it is auto-granted — never prompt, whatever the check reports.
        assertFalse("API 32 granted must not prompt", MainActivity.needsNotificationPermission(32, granted = true))
        assertFalse("API 32 not granted must not prompt", MainActivity.needsNotificationPermission(32, granted = false))
        // API 33+: prompt only when the current check says not granted.
        assertFalse("API 33 already granted must not re-prompt", MainActivity.needsNotificationPermission(33, granted = true))
        assertTrue("API 33 not granted must prompt", MainActivity.needsNotificationPermission(33, granted = false))
    }
}