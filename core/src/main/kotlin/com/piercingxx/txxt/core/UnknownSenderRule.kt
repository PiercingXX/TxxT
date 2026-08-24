package com.piercingxx.txxt.core

/**
 * Pure-Kotlin rule that decides whether an incoming message sender is unknown
 * and should therefore be blocked (routed to quarantine) rather than shown in
 * the main thread list.
 *
 * A sender is **unknown** when their address is not among the known contacts.
 * Matching goes through [PhoneNumbers.matches]: case-insensitive, whitespace-
 * trimmed, digit-normalised (so `"  +1 555 1234 "`, `"+15551234"`, and
 * `"(+1) 555-1234"` all refer to the same sender), with country-code suffix
 * tolerance for phone numbers and exact equality for email-gateway addresses.
 *
 * Zero `android.*` imports so the decision logic is JVM-testable without a
 * device.
 */
class UnknownSenderRule(
    private val knownContacts: Set<String> = emptySet(),
) {

    /** True when [sender] is not a known contact. */
    fun isUnknown(sender: String): Boolean = !isKnown(sender)

    /** True when [sender] is a known contact (format-tolerant; see [PhoneNumbers.matches]). */
    fun isKnown(sender: String): Boolean = knownContacts.any { PhoneNumbers.matches(sender, it) }

    /**
     * The reason a sender is treated as unknown, or null when they are known.
     * Returns a deterministic message so a surfaced reason is stable.
     */
    fun reason(sender: String): String? =
        if (isUnknown(sender)) "Sender is not a known contact" else null
}