package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the F5 fix: sender comparisons are format-tolerant, not
 * trim/lowercase-only. Every variant a carrier or contact book might produce
 * for the same number must match; short codes must NOT suffix-match; email
 * gateway addresses must match exactly and never cross-match numbers.
 */
class PhoneNumbersTest {

    // ---- normalize ----

    @Test
    fun `identical formats normalize to their digits`() {
        assertEquals("15551234567", PhoneNumbers.normalize("+15551234567"))
        assertEquals("15551234567", PhoneNumbers.normalize("  +15551234567  "))
        assertEquals("15551234567", PhoneNumbers.normalize("+15551234567".uppercase()))
        // The '+' itself is not a digit: country-coded and bare forms
        // normalize to the same digits, which is what makes them comparable.
        assertEquals(PhoneNumbers.normalize("+1 555 1234567"), PhoneNumbers.normalize("+15551234567"))
    }

    @Test
    fun `punctuation variants strip to digits`() {
        assertEquals("5551234567", PhoneNumbers.normalize("555-123-4567"))
        assertEquals("5551234567", PhoneNumbers.normalize("(555) 123 4567"))
        assertEquals("5551234567", PhoneNumbers.normalize("(555)-123.4567"))
    }

    @Test
    fun `email addresses keep their verbatim trimmed-lowercase form`() {
        assertEquals("alice@carrier.example.com", PhoneNumbers.normalize(" Alice@Carrier.Example.Com "))
    }

    @Test
    fun `garbage without digits or at-sign normalizes to empty`() {
        assertEquals("", PhoneNumbers.normalize("no-digits-here"))
    }

    // ---- matches: identical and formatting variance ----

    @Test
    fun `identical formats match`() {
        assertTrue(PhoneNumbers.matches("+15551234567", "+15551234567"))
    }

    @Test
    fun `plus one matches bare ten digit number`() {
        assertTrue(PhoneNumbers.matches("+15551234567", "5551234567"))
        assertTrue(PhoneNumbers.matches("5551234567", "+15551234567"))
    }

    @Test
    fun `punctuation variants all refer to one number`() {
        val variants = listOf(
            "+15551234567",
            "555-123-4567",
            "(555) 123 4567",
            "+1 555 1234567",
            "  +1 (555) 123-4567 ",
        )
        for (a in variants) {
            for (b in variants) {
                assertTrue("$a vs $b must match", PhoneNumbers.matches(a, b))
            }
        }
    }

    @Test
    fun `matching is case-insensitive and trims whitespace`() {
        assertTrue(PhoneNumbers.matches("  +1 555 1234 ", "+15551234"))
    }

    // ---- matches: country-code suffix tolerance ----

    @Test
    fun `country code suffix matches in both directions`() {
        assertTrue(PhoneNumbers.matches("+15551234567", "5551234567"))
        assertTrue(PhoneNumbers.matches("5551234567", "+15551234567"))
    }

    @Test
    fun `different numbers do not match even with shared suffix digits`() {
        assertFalse(PhoneNumbers.matches("+15551234567", "+15551299999"))
        assertFalse(PhoneNumbers.matches("5551234567", "5551234568"))
    }

    // ---- matches: the >=7-digit threshold prevents short-code collisions ----

    @Test
    fun `short codes below seven digits are not suffix-matched`() {
        // A stored short/emergency code must not match longer numbers that
        // happen to end in those digits.
        assertFalse(PhoneNumbers.matches("911", "5551234911"))
        assertFalse(PhoneNumbers.matches("40404", "155512340404"))
        assertFalse(PhoneNumbers.matches("123456", "+1555123123456"))
    }

    @Test
    fun `exactly seven digits can suffix-match a country-coded form`() {
        assertTrue(PhoneNumbers.matches("+15551234567", "1234567"))
        assertTrue(PhoneNumbers.matches("1234567", "+15551234567"))
        // Six digits stays below the threshold even when it is a real suffix.
        assertFalse(PhoneNumbers.matches("+15551234567", "234567"))
    }

    // ---- matches: email addresses ----

    @Test
    fun `email addresses match exactly ignoring case`() {
        assertTrue(PhoneNumbers.matches("Alice@Carrier.Example.COM", "alice@carrier.example.com"))
    }

    @Test
    fun `different email addresses do not match`() {
        assertFalse(PhoneNumbers.matches("alice@carrier.example.com", "bob@carrier.example.com"))
        assertFalse(PhoneNumbers.matches("alice@carrier.example.com", "alice@other.example.com"))
    }

    @Test
    fun `an email address never matches a phone number`() {
        assertFalse(PhoneNumbers.matches("5551234567@carrier.example.com", "5551234567"))
        assertFalse(PhoneNumbers.matches("alice@example.com", "1234567890"))
    }

    // ---- matches: garbage passthrough ----

    @Test
    fun `garbage without identity never matches anything`() {
        assertFalse(PhoneNumbers.matches("!!!", "???"))
        assertFalse(PhoneNumbers.matches("!!!", "!!!"))
        assertFalse(PhoneNumbers.matches("", ""))
        assertFalse(PhoneNumbers.matches("!!!", "5551234567"))
    }
}
