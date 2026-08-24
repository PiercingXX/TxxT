package com.piercingxx.txxt.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Behaviour-verifies `RespondViaMessageService`'s quick-reply decision.
 *
 * The Android service lifecycle (`onStartCommand`, `stopSelf`) is not
 * JVM-testable without Robolectric (not in the offline cache), so the tests
 * drive the pure decision seam the service actually uses:
 * [RespondViaMessageService.respond]. The send is a counting fake — no
 * SmsManager, no SEND_SMS gate.
 */
class RespondViaMessageServiceTest {

    @Test
    fun `a present recipient and text with an attempted send is RESULT_OK`() {
        var sentTo: String? = null
        val result = RespondViaMessageService.respond(
            recipient = "+15551234567",
            text = "Can't talk — texting now.",
            send = { to, _ -> sentTo = to; true },
        )
        assertEquals(RespondViaMessageService.RESULT_OK, result)
        assertEquals("+15551234567", sentTo)
    }

    @Test
    fun `a blank text is refused without sending`() {
        var sendInvoked = false
        val result = RespondViaMessageService.respond(
            recipient = "+15551234567",
            text = "   ",
            send = { _, _ -> sendInvoked = true; true },
        )
        assertEquals(RespondViaMessageService.RESULT_IO_ERROR, result)
        assertFalse("a blank reply must never reach the send pipeline", sendInvoked)
    }

    @Test
    fun `an empty text is refused without sending`() {
        var sendInvoked = false
        val result = RespondViaMessageService.respond(
            recipient = "+15551234567",
            text = "",
            send = { _, _ -> sendInvoked = true; true },
        )
        assertEquals(RespondViaMessageService.RESULT_IO_ERROR, result)
        assertFalse(sendInvoked)
    }

    @Test
    fun `a null recipient is refused without sending`() {
        var sendInvoked = false
        val result = RespondViaMessageService.respond(
            recipient = null,
            text = "Can't talk — texting now.",
            send = { _: String, _: String -> sendInvoked = true; true },
        )
        assertEquals(RespondViaMessageService.RESULT_IO_ERROR, result)
        assertFalse(sendInvoked)
    }

    @Test
    fun `a blank recipient is refused without sending`() {
        var sendInvoked = false
        val result = RespondViaMessageService.respond(
            recipient = "   ",
            text = "Can't talk — texting now.",
            send = { _, _ -> sendInvoked = true; true },
        )
        assertEquals(RespondViaMessageService.RESULT_IO_ERROR, result)
        assertFalse(sendInvoked)
    }

    @Test
    fun `a refused send is an error`() {
        val result = RespondViaMessageService.respond(
            recipient = "+15551234567",
            text = "Can't talk — texting now.",
            send = { _, _ -> false },
        )
        assertEquals(RespondViaMessageService.RESULT_IO_ERROR, result)
    }

    @Test
    fun `the outcome constants keep their pinned values`() {
        // RESULT_OK mirrors Activity.RESULT_OK (-1); RESULT_IO_ERROR sits at
        // Activity.RESULT_FIRST_USER (1) — there is no dedicated platform
        // constant on this path. Pin both so a drift is caught here.
        assertEquals(-1, RespondViaMessageService.RESULT_OK)
        assertEquals(1, RespondViaMessageService.RESULT_IO_ERROR)
    }
}
