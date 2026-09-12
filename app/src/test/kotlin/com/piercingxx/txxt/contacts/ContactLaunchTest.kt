package com.piercingxx.txxt.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContactLaunchTest {

    private val source: String
        get() = sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/contacts/ContactLaunch.kt"),
            File("app/src/main/kotlin/com/piercingxx/txxt/contacts/ContactLaunch.kt"),
        ).first { it.exists() }.readText()

    @Test
    fun savedLookupKey_opensDetail() {
        assertTrue(ContactLaunch.hasSavedContact("0r1-ABCDEF"))
        assertTrue(source.contains("EXTRA_LOOKUP_KEY"))
        assertTrue(source.contains("DETAIL_ACTIVITY"))
        assertTrue(source.contains("setClassName(CONTACTS_PACKAGE, DETAIL_ACTIVITY)"))
    }

    @Test
    fun unknownNumber_opensShowOrCreate() {
        assertFalse(ContactLaunch.hasSavedContact(null))
        assertFalse(ContactLaunch.hasSavedContact("  "))
        assertTrue(source.contains("SHOW_OR_CREATE_CONTACT"))
        assertTrue(source.contains("EDIT_ACTIVITY"))
        assertTrue(source.contains("tel:"))
    }

    @Test
    fun targetsXxContacts() {
        assertTrue(source.contains("\"com.piercingxx.xxcontacts\""))
        assertTrue(source.contains("ContactDetailActivity"))
        assertTrue(source.contains("ContactEditActivity"))
    }
}
