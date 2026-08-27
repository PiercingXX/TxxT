package com.piercingxx.txxt.ui

import com.piercingxx.txxt.contacts.ContactEntry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts the NEW-conversation recipient picker is actually wired: the
 * launcher launches [NewConversationActivity], the screen searches contacts
 * (not a phone-pad), and tapping a row reaches [RecipientPicker] then
 * [ThreadActivity].
 *
 * Instantiating the Activity is not JVM-testable without Robolectric (not
 * in the offline cache), so launch/layout are locked by reading
 * the source — the [ThreadWiringTest] / [SettingsWiringTest] pattern. The
 * adapter's bind seam is driven behaviourally.
 */
class NewConversationWiringTest {

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
        return file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
    private val picker: String by lazy { sourceText("ui/NewConversationActivity.kt") }
    private val layout: String by lazy { layoutText("activity_new_conversation.xml") }
    private val item: String by lazy { layoutText("item_recipient.xml") }

    @Test
    fun `the manifest declares the recipient picker`() {
        assertTrue(
            "AndroidManifest.xml must declare the .ui.NewConversationActivity component",
            manifestText.contains(".ui.NewConversationActivity"),
        )
    }

    @Test
    fun `the declared picker name resolves to a class`() {
        Class.forName("com.piercingxx.txxt.ui.NewConversationActivity")
    }

    @Test
    fun `the picker is not exported`() {
        val regex = Regex(
            """<activity\b[^>]*android:name=".ui.NewConversationActivity"[\s\S]*?</activity>""",
        )
        val block = regex.find(manifestText)?.value.orEmpty()
        assertTrue(block.isNotEmpty())
        assertTrue(
            "NewConversationActivity must not be exported — only NEW launches it",
            block.contains("""android:exported="false""""),
        )
    }

    @Test
    fun `NEW on the launcher opens the recipient picker, not a phone-pad dialog`() {
        assertTrue(
            "MainActivity must start NewConversationActivity from NEW",
            mainActivity.contains("NewConversationActivity::class.java"),
        )
        assertFalse(
            "NEW must not force a phone keypad — that was the bug",
            mainActivity.contains("TYPE_CLASS_PHONE"),
        )
        assertFalse(
            "NEW must not prompt with a Phone number EditText",
            mainActivity.contains("Phone number"),
        )
    }

    @Test
    fun `the picker uses the contacts directory`() {
        assertFalse(
            "FLAG_SECURE blacks screenshots; the operator asked to capture the app",
            picker.contains("FLAG_SECURE"),
        )
        assertTrue(
            "NewConversationActivity must list contacts through ContactDirectory",
            picker.contains("ContactDirectory(this)"),
        )
        assertTrue(
            "the picker must filter through RecipientPicker.rows",
            picker.contains("RecipientPicker.rows("),
        )
        assertTrue(
            "IME submit must go through RecipientPicker.addressOnSubmit",
            picker.contains("RecipientPicker.addressOnSubmit("),
        )
        assertTrue(
            "the search field must be TYPE_CLASS_TEXT so a name can be typed",
            picker.contains("InputType.TYPE_CLASS_TEXT"),
        )
        assertFalse(
            "the picker must not force a phone keypad",
            picker.contains("TYPE_CLASS_PHONE"),
        )
    }

    @Test
    fun `the picker opens a thread through the same find-or-create path as SENDTO`() {
        assertTrue(
            "NewConversationActivity must find or create via InboundStore",
            picker.contains("InboundStore.findOrCreateConversation"),
        )
        assertTrue(
            "NewConversationActivity must open the thread via ThreadActivity.launchIntent",
            picker.contains("ThreadActivity.launchIntent("),
        )
    }

    @Test
    fun `the picker layout searches by name or number over a contact list`() {
        assertTrue("activity_new_conversation.xml must host a SearchView", layout.contains("SearchView"))
        assertTrue(
            "the search field must identify as recipient_search",
            layout.contains("@+id/recipient_search"),
        )
        assertTrue(
            "the query hint must say Name or number — not Phone number",
            layout.contains("Name or number"),
        )
        assertFalse(
            "the search field must not use a phone inputType",
            layout.contains("inputType=\"phone\""),
        )
        assertTrue(
            "the search field must accept letters, not only digits",
            layout.contains("inputType=\"text\""),
        )
        assertTrue(
            "the search field must stay expanded so typing a name is the first action",
            layout.contains("iconifiedByDefault=\"false\""),
        )
        assertTrue(
            "activity_new_conversation.xml must host a RecyclerView of recipients",
            layout.contains("RecyclerView"),
        )
        assertTrue(layout.contains("@+id/recipient_list"))
    }

    @Test
    fun `the picker activity resolves the search field and the list by runtime id`() {
        assertTrue(
            picker.contains("findViewById<RecyclerView>(R.id.recipient_list)") ||
                picker.contains("findViewById(R.id.recipient_list)"),
        )
        assertTrue(picker.contains("R.id.recipient_search"))
        assertTrue(picker.contains("setOnQueryTextListener"))
    }

    @Test
    fun `a recipient row is a text-first name and number, not a card`() {
        assertTrue(item.contains("@+id/recipient_title"))
        assertTrue(item.contains("@+id/recipient_subtitle"))
        val lower = item.lowercase()
        assertFalse(
            "item_recipient.xml must not draw a bubble or card background",
            lower.contains("cardview") ||
                lower.contains("card_view") ||
                lower.contains("bubble"),
        )
    }

    @Test
    fun `the adapter bind path paints a contact row`() {
        var bound: RecipientRow? = null
        val adapter = RecipientAdapter(bindRow = { _, row -> bound = row })
        val holder = RecipientAdapter.RowHolder(mockk(relaxed = true))
        val entry = ContactEntry("Ada Lovelace", "+15550100")
        adapter.submit(listOf(RecipientRow.Contact(entry)))
        assertEquals(1, adapter.itemCount)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(RecipientRow.Contact(entry), bound)
    }

    @Test
    fun `the adapter bind path paints a typed-number row`() {
        var bound: RecipientRow? = null
        val adapter = RecipientAdapter(bindRow = { _, row -> bound = row })
        val holder = RecipientAdapter.RowHolder(mockk(relaxed = true))
        adapter.submit(listOf(RecipientRow.UseNumber("5550199")))
        adapter.onBindViewHolder(holder, 0)
        assertEquals(RecipientRow.UseNumber("5550199"), bound)
    }

    @Test
    fun `tapping a row fires the recipient callback`() {
        var tapped: RecipientRow? = null
        val adapter = RecipientAdapter(
            bindRow = { _, _ -> },
            onRecipientTap = { tapped = it },
        )
        val itemView = mockk<android.view.View>(relaxed = true)
        var click: android.view.View.OnClickListener? = null
        io.mockk.every { itemView.setOnClickListener(any()) } answers {
            click = firstArg()
        }
        val holder = RecipientAdapter.RowHolder(itemView)
        val row = RecipientRow.UseNumber("5550199")
        adapter.submit(listOf(row))
        adapter.onBindViewHolder(holder, 0)
        click!!.onClick(itemView)
        assertEquals(row, tapped)
    }
}
