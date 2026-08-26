package com.piercingxx.txxt.ui

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour-verifies `ComposeActivity`'s SENDTO recipient resolution.
 *
 * The Android component lifecycle (`onCreate`, the coroutine hand-off to
 * [ThreadActivity]) is not JVM-testable without Robolectric (not in the offline
 * cache), so the tests drive the pure decision seam the activity actually
 * uses: [ComposeActivity.recipientFrom]. Uri instances are MockK mocks whose
 * `schemeSpecificPart` carries exactly what the platform's
 * `Uri.getSchemeSpecificPart()` contract returns — the **decoded** payload —
 * mirroring the established mockable-android.jar seam style of
 * `SendPipelineTest` / `MmsReceiverBlockingTest`.
 */
class ComposeActivityTest {

    /** A Uri mock standing in for a parsed SENDTO data URI. */
    private fun uri(schemeSpecificPart: String?): Uri {
        val parsed: Uri = mockk()
        every { parsed.schemeSpecificPart } returns schemeSpecificPart
        return parsed
    }

    // The scheme itself is not part of schemeSpecificPart, so every SENDTO
    // scheme (sms:/smsto:/mms:/mmsto:) feeds the seam the same payload — one
    // assertion per scheme pins that none of them changes the rule.

    @Test
    fun `an sms uri yields the recipient`() {
        assertEquals("+15551234567", ComposeActivity.recipientFrom(uri("+15551234567")))
    }

    @Test
    fun `an smsto uri yields the recipient`() {
        assertEquals("+15551234567", ComposeActivity.recipientFrom(uri("+15551234567")))
    }

    @Test
    fun `an mms uri yields the recipient`() {
        assertEquals("+15551234567", ComposeActivity.recipientFrom(uri("+15551234567")))
    }

    @Test
    fun `an mmsto uri yields the recipient`() {
        assertEquals("+15551234567", ComposeActivity.recipientFrom(uri("+15551234567")))
    }

    @Test
    fun `surrounding whitespace is trimmed from the recipient`() {
        // Real SENDTO URIs routinely carry padded payloads.
        assertEquals("+15551234567", ComposeActivity.recipientFrom(uri("  +15551234567\t")))
    }

    @Test
    fun `a url-encoded number arrives decoded and resolves`() {
        // Uri.getSchemeSpecificPart() returns the DECODED form, so the %2B of
        // "sms:%2B15551234567" reaches the seam already as '+'. The seam must
        // pass it through untouched — no second decode, no mangling.
        assertEquals(
            "+15551234567",
            ComposeActivity.recipientFrom(uri("+15551234567")),
        )
        // Encoded padding (%20) decodes to spaces and still trims clean.
        assertEquals(
            "15551234567",
            ComposeActivity.recipientFrom(uri(" 15551234567 ")),
        )
    }

    @Test
    fun `a blank payload yields null - nothing to open`() {
        assertNull(ComposeActivity.recipientFrom(uri("")))
        assertNull(ComposeActivity.recipientFrom(uri("   ")))
    }

    @Test
    fun `a null data uri yields null`() {
        assertNull(ComposeActivity.recipientFrom(null))
    }

    @Test
    fun `a uri with a null scheme-specific part yields null`() {
        assertNull(ComposeActivity.recipientFrom(uri(null)))
    }

    @Test
    fun `a query suffix is stripped from the recipient`() {
        // Browsers and share sheets routinely append ?body=/?sms_body= to
        // SENDTO URIs. Everything from the first '?' is query — it must never
        // become the conversation key or the literal SMS destination.
        assertEquals(
            "5551234",
            ComposeActivity.recipientFrom(uri("5551234?body=hi")),
        )
        assertEquals(
            "+15551234567",
            ComposeActivity.recipientFrom(uri("+15551234567?sms_body=hello%20there")),
        )
        assertEquals(
            "5551234",
            ComposeActivity.recipientFrom(uri("5551234?")),
        )
    }

    @Test
    fun `a body query is extracted and a missing body is null`() {
        assertEquals("hello there", ComposeActivity.bodyFrom(uri("5551234?body=hello%20there")))
        assertEquals("hi", ComposeActivity.bodyFrom(uri("+15551234567?sms_body=hi")))
        assertNull(ComposeActivity.bodyFrom(uri("5551234")))
        assertNull(ComposeActivity.bodyFrom(uri("5551234?subject=only")))
        assertNull(ComposeActivity.bodyFrom(null))
    }
}
