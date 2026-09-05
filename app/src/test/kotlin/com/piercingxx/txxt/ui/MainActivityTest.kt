package com.piercingxx.txxt.ui

import com.piercingxx.txxt.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies `MainActivity`'s launcher grant-request decisions (corrective
 * T1). `MainActivity.onCreate` drives the default-SMS-handler role request and the
 * runtime-permission prompts from the pure companion functions `shouldRequestRole`,
 * `needsNotificationPermission`, `needsContactsPermission`, and
 * `neededRuntimePermissions` (MainActivity.kt companion object). These are the
 * ask/no-ask rules the launcher actually applies: the SMS role is requested exactly
 * when a role intent exists, POST_NOTIFICATIONS only on API 33+ when not granted,
 * READ_CONTACTS whenever not held, and the combined list is built from those two
 * decisions.
 *
 * The assertions drive the real production functions over their inputs and assert
 * the outputs — a regression in any decision rule fails here. The framework-bound
 * `MainActivity` (an Activity) cannot be instantiated in a plain JVM unit test (no
 * Robolectric in the offline cache), so the pure decision seams the launcher calls
 * are the JVM-testable surface, the same pattern `MainActivityWiringTest` uses.
 */
class MainActivityTest {

    @Test
    fun `shouldRequestRole asks exactly when a role intent exists`() {
        // A non-null role intent means there is something to ask for; null means
        // nothing to request (already held / unavailable / API <29 no RoleManager).
        assertTrue(MainActivity.shouldRequestRole(android.content.Intent()))
        assertFalse(MainActivity.shouldRequestRole(null))
    }

    @Test
    fun `needsNotificationPermission prompts only on API 33+ when not granted`() {
        // Below TIRAMISU (API 33) the permission is auto-granted — never prompt.
        assertFalse(MainActivity.needsNotificationPermission(32, granted = true))
        assertFalse(MainActivity.needsNotificationPermission(32, granted = false))
        // On API 33+ prompt only when the current check says not granted.
        assertFalse(MainActivity.needsNotificationPermission(33, granted = true))
        assertTrue(MainActivity.needsNotificationPermission(33, granted = false))
    }

    @Test
    fun `needsContactsPermission prompts whenever contacts are not held`() {
        // READ_CONTACTS is a runtime permission since API 23 (minSdk 24), so the
        // only question is whether it is already held.
        assertFalse(MainActivity.needsContactsPermission(granted = true))
        assertTrue(MainActivity.needsContactsPermission(granted = false))
    }

    @Test
    fun `neededRuntimePermissions asks both missing grants in one list`() {
        val both = MainActivity.neededRuntimePermissions(
            sdkInt = 33,
            notificationsGranted = false,
            contactsGranted = false,
        )
        assertEquals(
            listOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_CONTACTS,
            ),
            both,
        )
    }

    @Test
    fun `neededRuntimePermissions asks only the grants still missing`() {
        // API 33, notifications already held: only READ_CONTACTS is asked.
        assertEquals(
            listOf(android.Manifest.permission.READ_CONTACTS),
            MainActivity.neededRuntimePermissions(
                sdkInt = 33,
                notificationsGranted = true,
                contactsGranted = false,
            ),
        )
        // API 32 (< 33): notifications are auto-granted, so only contacts can be asked.
        assertEquals(
            listOf(android.Manifest.permission.READ_CONTACTS),
            MainActivity.neededRuntimePermissions(
                sdkInt = 32,
                notificationsGranted = false,
                contactsGranted = false,
            ),
        )
    }

    @Test
    fun `neededRuntimePermissions asks nothing when every grant is held`() {
        assertTrue(
            MainActivity.neededRuntimePermissions(
                sdkInt = 33,
                notificationsGranted = true,
                contactsGranted = true,
            ).isEmpty(),
        )
        assertTrue(
            MainActivity.neededRuntimePermissions(
                sdkInt = 32,
                notificationsGranted = false,
                contactsGranted = true,
            ).isEmpty(),
        )
    }
}