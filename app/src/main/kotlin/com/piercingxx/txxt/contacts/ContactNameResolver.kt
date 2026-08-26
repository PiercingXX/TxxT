package com.piercingxx.txxt.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.piercingxx.txxt.core.PhoneNumbers

/**
 * Turns a raw SMS address (an E.164 number, a national number, a short code, an
 * email-gateway address) into the display name the operator saved for it in the
 * **system contacts provider** — the same store the dialer reads. There is no
 * separate "dialer contact list": `ContactsContract` IS the dialer's contact
 * list, so resolving against it is what makes a saved contact stop rendering as
 * a bare number in the conversation list, the thread header, and notifications.
 *
 * **Why `PhoneLookup.CONTENT_FILTER_URI` and not a hand-rolled comparison.**
 * The obvious implementation — read every `Phone.NUMBER`, normalise both sides,
 * string-compare — is wrong in ways that only show up on a real SIM: country
 * codes present on one side and absent on the other, national trunk prefixes,
 * carrier-specific short codes, and the per-locale matching rules the platform
 * encodes in `PhoneNumberUtils.compare`. `PhoneLookup` is the provider endpoint
 * that already performs exactly that carrier-correct matching inside the
 * contacts database (it maintains its own normalised-number index), so this
 * class hands it the address verbatim and takes the answer. [PhoneNumbers] is
 * used ONLY to key the cache, never to decide whether two numbers match.
 *
 * **Why a cache.** Resolution is consulted once per conversation row and once
 * per notification. A provider query per `RecyclerView` bind is a cross-process
 * Binder round trip on the main thread — that is the jank. The cache is an LRU
 * keyed by the digit-normalised address, and it stores the *resolved label*
 * (never null), so a number with NO matching contact is a cache hit on every
 * subsequent row instead of a repeated miss that re-queries forever.
 *
 * **Only answers are cached, not failures to ask.** "The provider says nobody
 * is saved under this number" is an answer and is cached. "`READ_CONTACTS` is
 * denied" (or the query threw) is not — caching the number there would leave a
 * later grant invisible behind a cache full of numbers. [clearCache] covers the
 * remaining case: the permission-result callback drops answers computed under
 * the previous grant state, so the list repaints the moment the operator says
 * yes.
 *
 * **Never crash, never blank.** Every failure mode — permission denied, no
 * matching contact, a contact with an empty name, a provider that throws
 * (`SecurityException` on a permission revoked mid-session, a dead provider) —
 * degrades to the SAME safe answer: the address itself. A bind must never
 * render an empty row and must never throw.
 *
 * The Android-touching decisions are injectable seams (the established
 * [com.piercingxx.txxt.service.PermissionGate] pattern) so the resolver's
 * behaviour is drivable in a plain JVM unit test without Robolectric (not in
 * the offline cache).
 */
class ContactNameResolver(
    private val context: Context,
    /**
     * Whether the app currently holds the `READ_CONTACTS` runtime permission.
     * Defaults to the real platform check. Consulted on every cache MISS (not
     * once at construction) so a mid-session grant or revoke is honoured.
     */
    private val hasReadContacts: (Context) -> Boolean = { ctx ->
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    },
    /**
     * The provider lookup: `(context, address) -> display name or null`.
     * Defaults to [phoneLookupDisplayName], the real `PhoneLookup` query.
     */
    private val queryDisplayName: (Context, String) -> String? = ::phoneLookupDisplayName,
    /**
     * Upper bound on cached labels. A conversation list plus its notification
     * history is a few hundred distinct addresses at most; the LRU keeps a
     * pathological history (a spam wave of one-off short codes) from growing
     * the map without limit.
     */
    private val maxCacheEntries: Int = DEFAULT_CACHE_ENTRIES,
) {

    /**
     * Access-ordered LRU of `normalised address -> resolved label`. Guarded by
     * its own monitor rather than being a `ConcurrentHashMap`: the resolver is
     * consulted from the main thread (list binds, thread header) AND from
     * `Dispatchers.IO` (the deliver receivers' notification path), and an
     * access-ordered `LinkedHashMap` mutates its ordering on *read*, so even
     * [labelFor]'s lookup is a write that has to be serialised.
     */
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > maxCacheEntries
    }

    /**
     * The label to render for [address]: the saved contact's display name when
     * there is one, otherwise [address] itself.
     *
     * Never throws, and never turns a real address into blank text — every
     * failure mode in the class KDoc degrades to [address] itself. The one
     * blank result is a blank [address], handed back unchanged: there is
     * nothing to look up and nothing better to show.
     */
    fun labelFor(address: String): String {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return address

        val key = cacheKey(trimmed)
        synchronized(cache) { cache[key] }?.let { return it }

        // The distinction that makes the cache correct: "the provider says
        // nobody" is an ANSWER and gets cached (otherwise every unsaved number
        // re-queries on every bind, forever); "we could not ask" is NOT an
        // answer and must not be cached (otherwise a later grant is invisible
        // behind a cache full of numbers).
        return when (val lookup = consultProvider(trimmed)) {
            Lookup.Unavailable -> trimmed
            is Lookup.Answered -> {
                val resolved = lookup.name ?: trimmed
                synchronized(cache) { cache[key] = resolved }
                resolved
            }
        }
    }

    /**
     * Drops every cached label. Called when the `READ_CONTACTS` answer changes
     * (the permission-result callback), because every entry cached before the
     * change was computed under the old answer.
     */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    /** Number of labels currently cached — the cache's observable behaviour. */
    fun cachedCount(): Int = synchronized(cache) { cache.size }

    /**
     * The outcome of one attempted contact lookup.
     *
     * Two failures look identical to the caller (both render the number) but
     * must be remembered differently, which is why this is a type and not a
     * nullable String — see [labelFor].
     */
    private sealed interface Lookup {
        /**
         * The provider was consulted and replied. [name] is the saved display
         * name, or null when no contact matched the address (or the matched
         * contact has no usable name) — a real, cacheable answer either way.
         */
        data class Answered(val name: String?) : Lookup

        /**
         * The provider could not be consulted at all: `READ_CONTACTS` is
         * denied, or the query threw. Not an answer, so not cached.
         */
        data object Unavailable : Lookup
    }

    /**
     * One provider round trip.
     *
     * The catch is deliberately broad and covers the permission check as well
     * as the query: this runs inside a `RecyclerView` bind and inside a
     * broadcast receiver, and neither may die because the contacts database was
     * unhappy. A throw is classified [Lookup.Unavailable] rather than "no
     * contact" — a permission revoked mid-session surfaces as a
     * `SecurityException` here, and caching the number for it would strand the
     * label the same way a cached denial would.
     */
    private fun consultProvider(address: String): Lookup = try {
        if (!hasReadContacts(context)) {
            Lookup.Unavailable
        } else {
            Lookup.Answered(queryDisplayName(context, address)?.trim()?.ifEmpty { null })
        }
    } catch (_: Throwable) {
        Lookup.Unavailable
    }

    /**
     * The cache key for [address]: its digit-normalised form, so the same
     * number stored as `"+1 555-123-4567"` and delivered as `"+15551234567"`
     * shares one entry. An address that normalises to nothing (a name-only
     * sender id like `"VERIFY"`) keys off its lowercased self instead of
     * collapsing every such sender onto one empty key.
     */
    private fun cacheKey(address: String): String =
        PhoneNumbers.normalize(address).ifEmpty { address.lowercase() }

    companion object {

        /** Default LRU bound — see the [maxCacheEntries] KDoc. */
        const val DEFAULT_CACHE_ENTRIES = 512

        /**
         * The real lookup: asks `ContactsContract.PhoneLookup` for the display
         * name saved against [address], or null when no contact matches.
         *
         * `CONTENT_FILTER_URI` takes the address as a path segment (URI-encoded
         * — a raw `+` or `#` in a number would otherwise be swallowed by the
         * URI syntax) and does the carrier-specific number matching inside the
         * provider. `DISPLAY_NAME_PRIMARY` is read rather than the deprecated
         * `DISPLAY_NAME` alias, so the operator's display-order preference
         * (given-name-first vs family-name-first) is respected.
         *
         * The cursor is closed through `use` on every path, including the throw
         * path — an unclosed contacts cursor leaks a Binder-backed window.
         */
        fun phoneLookupDisplayName(context: Context, address: String): String? {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column =
                        cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY)
                    if (column >= 0) return cursor.getString(column)
                }
            }
            return null
        }
    }
}
