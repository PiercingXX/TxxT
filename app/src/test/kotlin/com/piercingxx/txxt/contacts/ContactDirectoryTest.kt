package com.piercingxx.txxt.contacts

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies [ContactDirectory]: the NEW picker can list saved
 * numbers when `READ_CONTACTS` is granted, and degrades to an empty list
 * (never a throw) when it is not. The `ContactsContract` query itself is
 * not JVM-testable without Robolectric (not in the offline cache), so the
 * permission check and the provider listing are driven through the class's
 * injected seams.
 */
class ContactDirectoryTest {

    private val context: Context = mockk(relaxed = true)

    private class RecordingList(private val entries: List<ContactEntry>) :
        (Context) -> List<ContactEntry> {
        var calls = 0
        override fun invoke(context: Context): List<ContactEntry> {
            calls += 1
            return entries
        }
    }

    private fun directory(
        listing: (Context) -> List<ContactEntry>,
        granted: Boolean = true,
    ) = ContactDirectory(
        context = context,
        hasReadContacts = { granted },
        queryContacts = listing,
    )

    @Test
    fun `a granted listing is returned as-is`() {
        val book = listOf(ContactEntry("Ada Lovelace", "+15550100"))
        val listing = RecordingList(book)
        assertEquals(book, directory(listing, granted = true).all())
        assertEquals(1, listing.calls)
    }

    @Test
    fun `a denied permission returns empty and never touches the provider`() {
        val listing = RecordingList(listOf(ContactEntry("Ada", "+15550100")))
        val result = directory(listing, granted = false).all()
        assertTrue(result.isEmpty())
        assertEquals(0, listing.calls)
    }

    @Test
    fun `a throwing provider returns empty`() {
        val directory = directory(
            listing = { throw SecurityException("revoked") },
            granted = true,
        )
        assertTrue(directory.all().isEmpty())
    }

    @Test
    fun `entryOf drops a blank number`() {
        assertNull(ContactDirectory.entryOf("Ada", "   "))
        assertNull(ContactDirectory.entryOf("Ada", null))
    }

    @Test
    fun `entryOf drops punctuation-only garbage`() {
        assertNull(ContactDirectory.entryOf("Ada", "---"))
    }

    @Test
    fun `entryOf falls back to the number when the name is blank`() {
        assertEquals(
            ContactEntry("+15550100", "+15550100"),
            ContactDirectory.entryOf("  ", "+15550100"),
        )
    }

    @Test
    fun `entryOf keeps a usable name and number`() {
        assertEquals(
            ContactEntry("Ada Lovelace", "+1 555 0100", starred = true),
            ContactDirectory.entryOf("Ada Lovelace", "+1 555 0100", starred = true),
        )
    }

    @Test
    fun `dedupe collapses the same name stored twice under formatting variants`() {
        val entries = listOf(
            ContactEntry("Ada", "+1 555 0100"),
            ContactEntry("Ada", "555-0100"),
            ContactEntry("Ada", "+15550101"),
        )
        val deduped = ContactDirectory.dedupe(entries)
        assertEquals(listOf(entries[0], entries[2]), deduped)
    }

    @Test
    fun `dedupe keeps two people who share a number`() {
        val entries = listOf(
            ContactEntry("Ada", "+15550100"),
            ContactEntry("Grace", "+15550100"),
        )
        assertEquals(entries, ContactDirectory.dedupe(entries))
    }

    @Test
    fun `the listing goes through CommonDataKinds Phone not PhoneLookup`() {
        // PhoneLookup needs an address in the path and cannot enumerate the
        // book; listing for NEW is a different query from number→name.
        val source = sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/contacts/ContactDirectory.kt"),
            File("app/src/main/kotlin/com/piercingxx/txxt/contacts/ContactDirectory.kt"),
        ).first { it.exists() }.readText()
        assertTrue(
            "ContactDirectory must list CommonDataKinds.Phone.CONTENT_URI",
            source.contains("ContactsContract.CommonDataKinds.Phone.CONTENT_URI"),
        )
        assertTrue(
            "ContactDirectory must not query ContactsContract.PhoneLookup",
            !source.contains("ContactsContract.PhoneLookup"),
        )
    }
}
