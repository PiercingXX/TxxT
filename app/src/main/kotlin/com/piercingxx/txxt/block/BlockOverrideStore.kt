package com.piercingxx.txxt.block

import android.content.SharedPreferences
import com.piercingxx.txxt.core.PhoneNumbers

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
 * Overrides are matched like the core rules, through `PhoneNumbers.matches`
 * (core): case-insensitive, whitespace-trimmed, digit-normalised with
 * country-code suffix tolerance — `"  +1 555 1234 "`, `"+15551234"`, and
 * `"(+1) 555-1234"` refer to the same sender; email-gateway addresses compare
 * by exact normalized equality. Values are stored under a stable key so an
 * override survives an app update.
 *
 * [InboundFilter] consults this store before applying any suppression: a sender
 * with an override is delivered even when a block rule or the unknown-sender
 * rule would otherwise stop them.
 */
class BlockOverrideStore(
    private val kv: BlockOverrideKeyValueStore,
) {
    /** True when [sender] has an explicit override (format-tolerant matching). */
    fun hasOverride(sender: String): Boolean =
        kv.getStringSet(KEY_OVERRIDES).any { PhoneNumbers.matches(sender, it) }

    /** Records an override for [sender]; idempotent. */
    fun addOverride(sender: String) {
        val overrides = kv.getStringSet(KEY_OVERRIDES).toMutableSet()
        overrides.add(PhoneNumbers.normalize(sender))
        kv.putStringSet(KEY_OVERRIDES, overrides)
    }

    /** Removes any override for [sender]; idempotent. */
    fun removeOverride(sender: String) {
        // Drop every stored variant that matches the sender, not just one
        // normalized spelling of it.
        val overrides = kv.getStringSet(KEY_OVERRIDES).filterNot { PhoneNumbers.matches(sender, it) }.toSet()
        kv.putStringSet(KEY_OVERRIDES, overrides)
    }

    companion object {
        const val KEY_OVERRIDES = "block_overrides"
    }
}