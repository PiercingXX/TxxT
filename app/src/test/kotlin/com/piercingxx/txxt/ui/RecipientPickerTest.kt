package com.piercingxx.txxt.ui

import com.piercingxx.txxt.contacts.ContactEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [RecipientPicker]: NEW can search the book by name or
 * number, and a typed address still opens a thread when it is not in the
 * book. Pure — no provider, no Activity.
 */
class RecipientPickerTest {

    private val ada = ContactEntry("Ada Lovelace", "+1 555 0100")
    private val grace = ContactEntry("Grace Hopper", "+1 555 0200", starred = true)
    private val adaWork = ContactEntry("Ada Lovelace", "+1 555 0101")
    private val book = listOf(ada, grace, adaWork)

    @Test
    fun `a blank query returns every contact, starred first then by name`() {
        val filtered = RecipientPicker.filter(book, "  ")
        assertEquals(listOf(grace, ada, adaWork), filtered)
    }

    @Test
    fun `query matches a contact name case-insensitively`() {
        assertEquals(listOf(grace), RecipientPicker.filter(book, "HOPPER"))
    }

    @Test
    fun `query matches a partial name`() {
        val matches = RecipientPicker.filter(book, "Ada")
        assertEquals(listOf(ada, adaWork), matches)
    }

    @Test
    fun `query matches a formatted number by digits`() {
        val matches = RecipientPicker.filter(book, "5550100")
        assertEquals(listOf(ada), matches)
    }

    @Test
    fun `query matches punctuation in the stored number`() {
        val matches = RecipientPicker.filter(listOf(ada), "(555) 0100")
        assertEquals(listOf(ada), matches)
    }

    @Test
    fun `a non-matching query returns nothing`() {
        assertTrue(RecipientPicker.filter(book, "zzz").isEmpty())
    }

    @Test
    fun `typedAddress keeps a phone number the operator typed`() {
        assertEquals("+1 555 0199", RecipientPicker.typedAddress("  +1 555 0199 "))
        assertEquals("5550199", RecipientPicker.typedAddress("5550199"))
    }

    @Test
    fun `typedAddress accepts an email-gateway address`() {
        assertEquals("ada@carrier.example.com", RecipientPicker.typedAddress("ada@carrier.example.com"))
    }

    @Test
    fun `typedAddress rejects a name`() {
        assertNull(RecipientPicker.typedAddress("Ada"))
        assertNull(RecipientPicker.typedAddress("Grace Hopper"))
    }

    @Test
    fun `typedAddress rejects blank and punctuation-only input`() {
        assertNull(RecipientPicker.typedAddress("   "))
        assertNull(RecipientPicker.typedAddress("---"))
        assertNull(RecipientPicker.typedAddress("+"))
    }

    @Test
    fun `rows prepend a use-this-number row when the query is a sendable address`() {
        val rows = RecipientPicker.rows(book, "5550199")
        assertEquals(RecipientRow.UseNumber("5550199"), rows.first())
        assertTrue(rows.drop(1).all { it is RecipientRow.Contact })
    }

    @Test
    fun `a typed number that also matches a contact still leads with use-this-number`() {
        val rows = RecipientPicker.rows(book, "5550100")
        assertEquals(RecipientRow.UseNumber("5550100"), rows.first())
        assertTrue(rows.any { it is RecipientRow.Contact && it.entry == ada })
    }

    @Test
    fun `rows are contacts only when the query is a name`() {
        val rows = RecipientPicker.rows(book, "Ada")
        assertEquals(
            listOf(RecipientRow.Contact(ada), RecipientRow.Contact(adaWork)),
            rows,
        )
    }

    @Test
    fun `a blank query has no use-this-number row`() {
        val rows = RecipientPicker.rows(book, "")
        assertTrue(rows.none { it is RecipientRow.UseNumber })
        assertEquals(3, rows.size)
    }

    @Test
    fun `submit of a typed number opens that number even when contacts also match`() {
        assertEquals(
            "5550100",
            RecipientPicker.addressOnSubmit("5550100", book),
        )
    }

    @Test
    fun `submit of a unique name opens that contact`() {
        assertEquals(
            grace.number,
            RecipientPicker.addressOnSubmit("Hopper", book),
        )
    }

    @Test
    fun `submit of an ambiguous name does not guess`() {
        assertNull(RecipientPicker.addressOnSubmit("Ada", book))
    }

    @Test
    fun `submit of a non-matching name does not invent an address`() {
        assertNull(RecipientPicker.addressOnSubmit("zzz", book))
    }
}
