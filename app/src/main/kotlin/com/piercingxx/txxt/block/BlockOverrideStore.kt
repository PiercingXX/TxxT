package com.piercingxx.txxt.block

import android.content.SharedPreferences

/**
 * Key-value persistence backing [BlockOverrideStore].
 *
 * Abstracted behind this interface so the store's logic is JVM-testable without
 * a device or Robolectric (which is not in the offline cache) — the test injects
 * an in-memory fake, while the app wires [SharedPreferencesBlockOverrideKeyValueStore]
 * at the call site. The keys are opaque to callers; only [BlockOverrideStore] reads them.
 */
interface BlockOverrideKeyValueStore {
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

/**
 * [BlockOverrideKeyValueStore] backed by Android [SharedPreferences].
 */
class SharedPreferencesBlockOverrideKeyValueStore(
    private val prefs: SharedPreferences,
) : BlockOverrideKeyValueStore {
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()
    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}

/**
 * Persists the user's per-sender override decisions: a sender the user has
 * explicitly allowed through the block/quarantine rules.
 *
 * Overrides are matched like the core rules (case-insensitive, trimmed) so
 * `"  +1 555 1234 "` and `"+1 555 1234"` refer to the same sender. Values are
 * stored under a stable key so an override survives an app update.
 *
 * [InboundFilter] consults this store before applying any suppression: a sender
 * with an override is delivered even when a block rule or the unknown-sender
 * rule would otherwise stop them.
 */
class BlockOverrideStore(
    private val kv: BlockOverrideKeyValueStore,
) {
    /** True when [sender] has an explicit override (case-insensitive, trimmed). */
    fun hasOverride(sender: String): Boolean = normalize(sender) in kv.getStringSet(KEY_OVERRIDES)

    /** Records an override for [sender]; idempotent. */
    fun addOverride(sender: String) {
        val overrides = kv.getStringSet(KEY_OVERRIDES).toMutableSet()
        overrides.add(normalize(sender))
        kv.putStringSet(KEY_OVERRIDES, overrides)
    }

    /** Removes any override for [sender]; idempotent. */
    fun removeOverride(sender: String) {
        val overrides = kv.getStringSet(KEY_OVERRIDES).toMutableSet()
        overrides.remove(normalize(sender))
        kv.putStringSet(KEY_OVERRIDES, overrides)
    }

    private fun normalize(address: String): String = address.trim().lowercase()

    companion object {
        const val KEY_OVERRIDES = "block_overrides"
    }
}