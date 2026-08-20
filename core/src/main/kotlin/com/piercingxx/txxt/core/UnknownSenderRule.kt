package com.piercingxx.txxt.core

/**
 * Pure-Kotlin rule that decides whether an incoming message sender is unknown
 * and should therefore be blocked (routed to quarantine) rather than shown in
 * the main thread list.
 *
 * A sender is **unknown** when their address is not among the known contacts.
 * Matching is case-insensitive and trims surrounding whitespace so that
 * `"  +1 555 1234 "` and `"+1 555 1234"` refer to the same sender.
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class UnknownSenderRule(
    private val knownContacts: Set<String> = emptySet(),
) {

    /** True when [sender] is not a known contact. */
    fun isUnknown(sender: String): Boolean = !isKnown(sender)

    /** True when [sender] is a known contact (case-insensitive, trimmed). */
    fun isKnown(sender: String): Boolean = normalize(sender) in knownNormalized()

    /**
     * The reason a sender is treated as unknown, or null when they are known.
     * Returns a deterministic message so a surfaced reason is stable.
     */
    fun reason(sender: String): String? =
        if (isUnknown(sender)) "Sender is not a known contact" else null

    private fun knownNormalized(): Set<String> = knownContacts.mapTo(mutableSetOf()) { normalize(it) }

    private fun normalize(address: String): String = address.trim().lowercase()
}