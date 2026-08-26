package com.piercingxx.txxt.contacts

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [ContactNameResolver]: numbers become saved contact names,
 * every failure mode degrades to the number (never a blank, never a throw), and
 * the provider is consulted once per distinct address rather than once per bind.
 *
 * The `ContactsContract` query itself is not JVM-testable without Robolectric
 * (not in the offline cache), so the two Android-touching decisions — the
 * permission check and the provider lookup — are driven through the class's
 * injected seams (the [com.piercingxx.txxt.service.PermissionGate] precedent).
 * What is under test is everything the resolver decides AROUND that query:
 * when it asks, what it does with silence, and what it remembers.
 */
class ContactNameResolverTest {

    private val context: Context = mockk(relaxed = true)

    /** Counts provider consultations, so caching is observable behaviour. */
    private class RecordingLookup(private val names: Map<String, String?>) :
        (Context, String) -> String? {
        val queried = mutableListOf<String>()
        override fun invoke(context: Context, address: String): String? {
            queried += address
            return names[address]
        }
    }

    private fun resolver(
        lookup: (Context, String) -> String?,
        granted: Boolean = true,
        maxCacheEntries: Int = ContactNameResolver.DEFAULT_CACHE_ENTRIES,
    ) = ContactNameResolver(
        context = context,
        hasReadContacts = { granted },
        queryDisplayName = lookup,
        maxCacheEntries = maxCacheEntries,
    )

    // ---- The happy path ----

    @Test
    fun `a saved contact resolves to its display name`() {
        val resolver = resolver(RecordingLookup(mapOf("+15550001111" to "Ada Lovelace")))
        assertEquals("Ada Lovelace", resolver.labelFor("+15550001111"))
    }

    // ---- Every fallback lands on the number, never a blank ----

    @Test
    fun `an unknown number shows as the number`() {
        val resolver = resolver(RecordingLookup(mapOf("+15550001111" to "Ada")))
        assertEquals("+15559998888", resolver.labelFor("+15559998888"))
    }

    @Test
    fun `a denied permission shows the number and never touches the provider`() {
        // The app must stay fully usable when the operator says no: numbers,
        // not empty rows — and no provider query that would throw SecurityException.
        val lookup = RecordingLookup(mapOf("+15550001111" to "Ada Lovelace"))
        val resolver = resolver(lookup, granted = false)

        assertEquals("+15550001111", resolver.labelFor("+15550001111"))
        assertTrue(
            "a denied permission must not reach the contacts provider",
            lookup.queried.isEmpty(),
        )
    }

    @Test
    fun `a contact with a blank name shows the number`() {
        // A contact row can carry an empty or whitespace display name (a
        // company-only or photo-only entry). Rendering that would blank the row.
        val resolver = resolver(RecordingLookup(mapOf("+15550001111" to "   ")))
        assertEquals("+15550001111", resolver.labelFor("+15550001111"))
    }

    @Test
    fun `a throwing provider shows the number instead of crashing the bind`() {
        // Permission revoked mid-session, a dead provider, a hostile ROM: this
        // runs inside a RecyclerView bind and inside a broadcast receiver, and
        // neither may die because the contacts database was unhappy.
        val resolver = resolver({ _, _ -> throw SecurityException("revoked") })
        assertEquals("+15550001111", resolver.labelFor("+15550001111"))
        assertEquals(
            "a failed consultation is not an answer and must not be cached",
            0,
            resolver.cachedCount(),
        )
    }

    @Test
    fun `a blank address is handed back unchanged`() {
        val lookup = RecordingLookup(emptyMap())
        val resolver = resolver(lookup)
        assertEquals("", resolver.labelFor(""))
        assertEquals("   ", resolver.labelFor("   "))
        assertTrue(
            "there is nothing to look up for a blank address",
            lookup.queried.isEmpty(),
        )
    }

    @Test
    fun `the resolved label is trimmed`() {
        val resolver = resolver(RecordingLookup(mapOf("+15550001111" to "  Ada Lovelace  ")))
        assertEquals("Ada Lovelace", resolver.labelFor("+15550001111"))
    }

    // ---- Caching: the reason this is a class and not a function ----

    @Test
    fun `repeated lookups of the same number query the provider once`() {
        // This is the anti-jank contract: a conversation list re-binds the same
        // addresses on every scroll and every Room emission, and a cross-process
        // Binder round trip per bind is the stutter.
        val lookup = RecordingLookup(mapOf("+15550001111" to "Ada Lovelace"))
        val resolver = resolver(lookup)

        repeat(25) { assertEquals("Ada Lovelace", resolver.labelFor("+15550001111")) }

        assertEquals(listOf("+15550001111"), lookup.queried)
    }

    @Test
    fun `a number with NO contact is cached too`() {
        // The easy caching bug: cache only the hits, and every unsaved number
        // re-queries forever — which is most of a spam-heavy inbox.
        val lookup = RecordingLookup(emptyMap())
        val resolver = resolver(lookup)

        repeat(10) { assertEquals("+15559998888", resolver.labelFor("+15559998888")) }

        assertEquals(1, lookup.queried.size)
        assertEquals(1, resolver.cachedCount())
    }

    @Test
    fun `formatting variance shares one cache entry`() {
        // The provider is asked with the address as delivered, but the CACHE is
        // keyed on the digit-normalised form, so surrounding whitespace and
        // punctuation do not multiply entries for one contact.
        val lookup = RecordingLookup(
            mapOf(
                "+15550001111" to "Ada Lovelace",
                "  +1 (555) 000-1111  " to "Ada Lovelace",
            )
        )
        val resolver = resolver(lookup)

        assertEquals("Ada Lovelace", resolver.labelFor("+15550001111"))
        assertEquals("Ada Lovelace", resolver.labelFor("  +1 (555) 000-1111  "))

        assertEquals("one contact, one query", 1, lookup.queried.size)
        assertEquals(1, resolver.cachedCount())
    }

    @Test
    fun `a denied permission is not cached, so a later grant takes effect`() {
        // Caching the number under a denial would make the grant invisible: the
        // list would stay on numbers until the process restarted.
        var granted = false
        val resolver = ContactNameResolver(
            context = context,
            hasReadContacts = { granted },
            queryDisplayName = { _, _ -> "Ada Lovelace" },
        )

        assertEquals("+15550001111", resolver.labelFor("+15550001111"))
        assertEquals("nothing may be cached while denied", 0, resolver.cachedCount())

        granted = true
        assertEquals("Ada Lovelace", resolver.labelFor("+15550001111"))
    }

    @Test
    fun `clearCache forces a fresh lookup`() {
        // What the permission-result callback calls: every label cached before
        // the answer changed was computed under the old answer.
        val lookup = RecordingLookup(mapOf("+15550001111" to "Ada Lovelace"))
        val resolver = resolver(lookup)

        resolver.labelFor("+15550001111")
        resolver.clearCache()
        assertEquals(0, resolver.cachedCount())
        resolver.labelFor("+15550001111")

        assertEquals(2, lookup.queried.size)
    }

    @Test
    fun `the cache is bounded`() {
        // A spam wave of one-off short codes must not grow the map without
        // limit for the life of the process.
        val resolver = resolver(RecordingLookup(emptyMap()), maxCacheEntries = 4)

        repeat(50) { resolver.labelFor("+1555000${1000 + it}") }

        assertEquals(4, resolver.cachedCount())
    }

    @Test
    fun `a non-numeric sender id keeps its own cache entry`() {
        // Alphanumeric sender ids ("VERIFY", "MYBANK") normalise to no digits
        // at all; keying them off the empty string would collapse every such
        // sender onto one label.
        val lookup = RecordingLookup(mapOf("MYBANK" to "My Bank"))
        val resolver = resolver(lookup)

        assertEquals("My Bank", resolver.labelFor("MYBANK"))
        assertEquals("VERIFY", resolver.labelFor("VERIFY"))
        assertEquals(2, resolver.cachedCount())
    }
}
