package com.piercingxx.txxt.ui

import com.piercingxx.txxt.MainActivity
import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.service.NotificationPolicy
import com.piercingxx.txxt.service.NotificationService
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asserts contact-name resolution is actually WIRED into every surface that
 * shows a sender, not merely available.
 *
 * The reported symptom was that saved contacts rendered as bare numbers
 * everywhere, and the root cause was that nothing in the app ever consulted the
 * contacts provider. So the risk this box guards is a resolver that exists and
 * is unit-tested while some surface still prints the raw address. Each surface
 * is covered at the strongest level available in a JVM test without Robolectric
 * (not in the offline cache): the presenter and the adapter behaviourally
 * (drive the real path, assert on the produced row), the activities by reading
 * their source (the established [ThreadWiringTest] / manifest-test pattern,
 * because Android view inflation and intent dispatch are not drivable here).
 */
class ContactNameWiringTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    private fun layoutText(name: String): String =
        sequenceOf(
            File("src/main/res/layout/$name"),
            File("app/src/main/res/layout/$name"),
        ).first { it.exists() }.readText()

    private val mainActivity: String by lazy { sourceText("MainActivity.kt") }
    private val threadActivity: String by lazy { sourceText("ui/ThreadActivity.kt") }
    private val resolver: String by lazy { sourceText("contacts/ContactNameResolver.kt") }

    /** A fake directory standing in for the operator's saved contacts. */
    private val saved = mapOf(
        "+15550001111" to "Ada Lovelace",
        "+15550002222" to "Grace Hopper",
    )

    private fun displayName(address: String): String = saved[address] ?: address

    // ---- The permission ----

    @Test
    fun `the manifest declares READ_CONTACTS`() {
        // Without the declaration the runtime request is refused outright and
        // every lookup fails closed — which is exactly the reported bug.
        assertTrue(
            "AndroidManifest.xml must declare READ_CONTACTS to resolve contact names",
            manifestText.contains("android.permission.READ_CONTACTS"),
        )
    }

    @Test
    fun `the manifest declares no CONTACTS write permission`() {
        // The permission posture is read-only: TxxT reads the operator's
        // contacts to name a number and never modifies the contacts database.
        assertFalse(
            "TxxT must never declare WRITE_CONTACTS — the lookup is read-only",
            manifestText.contains("android.permission.WRITE_CONTACTS"),
        )
    }

    @Test
    fun `the launcher requests READ_CONTACTS at runtime`() {
        // A manifest declaration alone grants nothing on API 23+; the launcher
        // is where the app already asks for its other grants.
        assertTrue(
            "MainActivity must request READ_CONTACTS at runtime",
            mainActivity.contains("Manifest.permission.READ_CONTACTS"),
        )
        assertTrue(
            "the contacts-prompt decision must go through needsContactsPermission",
            mainActivity.contains("needsContactsPermission("),
        )
        assertTrue(
            "the request must carry its own request code",
            mainActivity.contains("REQUEST_READ_CONTACTS"),
        )
    }

    @Test
    fun `needsContactsPermission asks exactly when the grant is missing`() {
        assertTrue("not granted must prompt", MainActivity.needsContactsPermission(granted = false))
        assertFalse(
            "already granted must not re-prompt",
            MainActivity.needsContactsPermission(granted = true),
        )
    }

    @Test
    fun `the launcher repaints once the permission answer arrives`() {
        // A grant that only takes effect after the next inbound message would
        // look, to the operator, exactly like the permission having done nothing.
        assertTrue(
            "MainActivity must handle the permission result",
            mainActivity.contains("onRequestPermissionsResult"),
        )
        assertTrue(
            "the result must drop labels cached under the previous answer",
            mainActivity.contains("contactNames.clearCache()"),
        )
    }

    @Test
    fun `the resolver uses PhoneLookup rather than a hand-rolled number match`() {
        // PhoneLookup performs the carrier-specific number matching inside the
        // provider; comparing normalised strings against Phone.NUMBER
        // re-implements it badly and fails on country-code variance.
        assertTrue(
            "the lookup must go through ContactsContract.PhoneLookup's filter URI",
            resolver.contains("ContactsContract.PhoneLookup.CONTENT_FILTER_URI"),
        )
        assertFalse(
            "the resolver must not scan the Phone table itself",
            resolver.contains("ContactsContract.CommonDataKinds.Phone"),
        )
    }

    // ---- Surface 1: the conversation list ----

    @Test
    fun `the list presenter titles a row with the saved contact name`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 1L, participantAddresses = setOf("+15550001111")),
            ::displayName,
        )
        assertEquals("Ada Lovelace", row.title)
    }

    @Test
    fun `an unsaved number still titles the row with the number`() {
        val row = ConversationListPresenter.present(
            Conversation(id = 1L, participantAddresses = setOf("+15559998888")),
            ::displayName,
        )
        assertEquals("+15559998888", row.title)
    }

    @Test
    fun `a group thread names every participant it can`() {
        val title = ConversationListPresenter.title(
            listOf("+15550001111", "+15559998888", "+15550002222"),
            ::displayName,
        )
        assertEquals("Ada Lovelace, +15559998888, Grace Hopper", title)
    }

    @Test
    fun `a resolver that returns blank falls back to the address`() {
        // Belt and braces over ContactNameResolver's own never-blank contract:
        // the presenter must not be the thing that renders an empty row.
        val title = ConversationListPresenter.title(listOf("+15550001111")) { "" }
        assertEquals("+15550001111", title)
    }

    @Test
    fun `the untitled marker survives contact resolution`() {
        assertEquals(
            ConversationListPresenter.UNTITLED,
            ConversationListPresenter.title(emptyList(), ::displayName),
        )
    }

    @Test
    fun `the list adapter hands its resolver to the presenter`() {
        // Read rather than driven: ConversationListAdapter is not constructible
        // in a plain JVM test — its `init` calls setHasStableIds, which reaches
        // into RecyclerView.Adapter's observer list, and the mockable
        // android.jar never initialises it (the same limitation the adapter's
        // own `attached` guard exists for). The presenter half of the path IS
        // driven, above; this locks the one line that joins them.
        val adapter = sourceText("ui/ConversationListAdapter.kt")
        assertTrue(
            "ConversationListAdapter must accept a displayName resolver",
            adapter.contains("displayName: (String) -> String"),
        )
        assertTrue(
            "submit must hand the resolver to the presenter, not drop it",
            adapter.contains("ConversationListPresenter.present(it, displayName)"),
        )
        assertTrue(
            "MainActivity must give the adapter the real contacts resolver",
            mainActivity.contains("displayName = { address -> contactNames.labelFor(address) }"),
        )
        assertTrue(
            "MainActivity must hold ONE resolver instance so its cache survives refreshes",
            mainActivity.contains("ContactNameResolver(this)"),
        )
    }

    // ---- Surface 2: the thread screen header ----

    @Test
    fun `the thread layout carries a title line`() {
        val layout = layoutText("activity_thread.xml")
        assertTrue(
            "activity_thread.xml must declare the thread title view",
            layout.contains("@+id/thread_title"),
        )
        val titleIndex = layout.indexOf("@+id/thread_title")
        val listIndex = layout.indexOf("@+id/message_list")
        assertTrue(
            "the title must live in the top bar, above the message list",
            titleIndex in 0 until listIndex,
        )
    }

    @Test
    fun `the thread screen fills its header through the resolver`() {
        assertTrue(
            "ThreadActivity must build a ContactNameResolver",
            threadActivity.contains("ContactNameResolver(this)"),
        )
        assertTrue(
            "ThreadActivity must set the header text",
            threadActivity.contains("threadTitle.text"),
        )
        assertTrue(
            "the header must reuse the list's title function, not re-derive one",
            threadActivity.contains("ConversationListPresenter.title("),
        )
        assertTrue(
            "the header must resolve addresses through the resolver",
            threadActivity.contains("contactNames.labelFor("),
        )
    }

    // ---- Surface 3: notifications ----

    @Test
    fun `the notification policy names a saved contact`() {
        assertEquals(
            "Ada Lovelace",
            NotificationPolicy.notificationTitle("+15550001111", ::displayName),
        )
        assertEquals(
            "Ada Lovelace",
            NotificationPolicy.redactedContent("+15550001111", ::displayName),
        )
    }

    @Test
    fun `the notification policy falls back to the raw sender`() {
        assertEquals(
            "+15559998888",
            NotificationPolicy.notificationTitle("+15559998888", ::displayName),
        )
        assertEquals(
            "a blank resolution must never title a notification with nothing",
            "+15559998888",
            NotificationPolicy.notificationTitle("+15559998888") { "" },
        )
    }

    @Test
    fun `a posted notification is titled with the contact name`() {
        // Behavioural over the real NotificationService: the injected
        // displayName seam must reach both the title and the redacted text.
        var title: CharSequence? = null
        var text: CharSequence? = null
        val builder = mockk<androidx.core.app.NotificationCompat.Builder>(relaxed = true)
        io.mockk.every { builder.setSmallIcon(any<Int>()) } answers { builder }
        io.mockk.every { builder.setContentTitle(any()) } answers {
            title = firstArg()
            builder
        }
        io.mockk.every { builder.setContentText(any()) } answers {
            text = firstArg()
            builder
        }
        io.mockk.every { builder.setContentIntent(any()) } answers { builder }
        io.mockk.every { builder.setAutoCancel(any()) } answers { builder }
        io.mockk.every { builder.addAction(any()) } answers { builder }

        val service = NotificationService(
            context = mockk(relaxed = true),
            contentIntent = { _, _ -> mockk(relaxed = true) },
            quickReplyAction = { _, _ -> mockk(relaxed = true) },
            makeBuilder = { _ -> builder },
            displayName = ::displayName,
            postNotification = { _, _ -> },
        )

        service.postMessageNotification(sender = "+15550001111", body = "Hello")

        assertEquals("Ada Lovelace", title)
        assertEquals(
            "the REDACTED default shows the contact name, still never the body",
            "Ada Lovelace",
            text,
        )
    }
}
