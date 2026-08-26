package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.PhoneNumbers

/**
 * Pure transforms over the persisted blocking/starred settings map.
 *
 * The blocking editor ([BlockingActivity]) and the launcher's row actions
 * (block sender / star contact) both edit the same persisted state: the
 * backup string-map [SettingsBlockingStore] round-trips through
 * (`fromMap`/`toMap`, newline-joined sets under [SettingsBlockingStore.KEY_NAMES]).
 * These helpers own the map surgery as pure functions — zero `android.*`
 * imports, JVM-testable — so every edit path produces the same shape the
 * store, the backup (WS12 T4), and the live filter re-apply all read.
 *
 * Callers persist the returned map to SharedPreferences and then run
 * `SettingsBlockingStore.fromMap(map).loadAndApply()` so the change reaches
 * the running inbound filter immediately.
 */
object BlockingRules {

    /** Adds [value] to the set stored under [key], returning the new map. */
    fun withEntry(map: Map<String, String>, key: String, value: String): Map<String, String> {
        // Collapse embedded line breaks (a paste can carry them even into a
        // single-line field): the persisted sets are newline-joined, so a raw
        // "\n" inside one value would re-parse as several unintended entries.
        val trimmed = value.trim().replace(Regex("\\R+"), " ")
        if (trimmed.isEmpty()) return map
        val store = SettingsBlockingStore.fromMap(map)
        val sets = mutableSets(store)
        sets.getValue(key).add(trimmed)
        return render(sets)
    }

    /** Removes [value] from the set stored under [key], returning the new map. */
    fun withoutEntry(map: Map<String, String>, key: String, value: String): Map<String, String> {
        val store = SettingsBlockingStore.fromMap(map)
        val sets = mutableSets(store)
        sets.getValue(key).remove(value)
        return render(sets)
    }

    /** The set currently stored under [key]. */
    fun entries(map: Map<String, String>, key: String): Set<String> {
        val store = SettingsBlockingStore.fromMap(map)
        return when (key) {
            SettingsBlockingStore.KEY_KEYWORDS -> store.keywords()
            SettingsBlockingStore.KEY_PHRASES -> store.phrases()
            SettingsBlockingStore.KEY_BLOCKED_ADDRESSES -> store.blockedAddresses()
            SettingsBlockingStore.KEY_STARRED_CONTACTS -> store.starredContacts()
            else -> emptySet()
        }
    }

    /** Whether [address] is in the persisted starred-contacts set. */
    fun isStarred(map: Map<String, String>, address: String): Boolean =
        entries(map, SettingsBlockingStore.KEY_STARRED_CONTACTS)
            .any { PhoneNumbers.matches(address, it) }

    /** Whether [address] is in the persisted blocked-addresses set. */
    fun isBlocked(map: Map<String, String>, address: String): Boolean =
        entries(map, SettingsBlockingStore.KEY_BLOCKED_ADDRESSES)
            .any { PhoneNumbers.matches(address, it) }

    /**
     * Toggles [address] in the starred-contacts set. Returns the new map and
     * whether the address is starred AFTER the toggle.
     */
    fun withStarredToggled(
        map: Map<String, String>,
        address: String,
    ): Pair<Map<String, String>, Boolean> {
        val starredKey = SettingsBlockingStore.KEY_STARRED_CONTACTS
        return if (isStarred(map, address)) {
            withoutEntry(map, starredKey, address) to false
        } else {
            withEntry(map, starredKey, address) to true
        }
    }

    private fun mutableSets(store: SettingsBlockingStore): Map<String, MutableSet<String>> =
        mapOf(
            SettingsBlockingStore.KEY_KEYWORDS to store.keywords().toMutableSet(),
            SettingsBlockingStore.KEY_PHRASES to store.phrases().toMutableSet(),
            SettingsBlockingStore.KEY_BLOCKED_ADDRESSES to store.blockedAddresses().toMutableSet(),
            SettingsBlockingStore.KEY_STARRED_CONTACTS to store.starredContacts().toMutableSet(),
        )

    private fun render(sets: Map<String, Set<String>>): Map<String, String> =
        SettingsBlockingStore.toMap(
            SettingsBlockingStore(
                keywords = sets.getValue(SettingsBlockingStore.KEY_KEYWORDS),
                phrases = sets.getValue(SettingsBlockingStore.KEY_PHRASES),
                blockedAddresses = sets.getValue(SettingsBlockingStore.KEY_BLOCKED_ADDRESSES),
                starredContacts = sets.getValue(SettingsBlockingStore.KEY_STARRED_CONTACTS),
            )
        )
}
