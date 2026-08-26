package com.piercingxx.txxt.service

/**
 * Pure notification-policy slice of the service layer. Zero `android.*` imports
 * so the decision logic is JVM-testable in the app module's unit tests.
 *
 * Two decisions, both from `docs/PRIVACY.md:58-65`:
 *  - **sender-name-only content**: notifications show the sender name as the
 *    title and never include the message body in the visible text
 *    (`docs/PRIVACY.md:60-61`);
 *  - **no bubble / chat-head**: the policy never produces bubble data or
 *    bubble flags — no `BUBBLE_DATA`, no `FLAG_BUBBLE`
 *    (`docs/PRIVACY.md:51-53`).
 */
object NotificationPolicy {

    /**
     * The visible text for a redacted notification.
     * Shows the sender name only, never the message content.
     *
     * [displayName] resolves the raw address to the saved contact name
     * (`contacts/ContactNameResolver`); it defaults to identity, which keeps
     * this object free of `android.*` imports and JVM-testable — the policy
     * decides WHAT is shown (the sender, never the body), the caller supplies
     * the resolution. A resolver that returns blank falls back to the raw
     * sender: a notification must never be titled with nothing.
     */
    fun redactedContent(
        sender: String,
        displayName: (String) -> String = { it },
    ): String = displayName(sender).ifBlank { sender }

    /**
     * The notification title — always the sender, shown as the saved contact
     * name when [displayName] resolves one (see [redactedContent] for the
     * seam's contract).
     */
    fun notificationTitle(
        sender: String,
        displayName: (String) -> String = { it },
    ): String = displayName(sender).ifBlank { sender }

    /**
     * Whether the notification should carry bubble data.
     * Always false — nothing ever bubbles (`docs/PRIVACY.md:51-53`).
     */
    fun shouldBubble(): Boolean = false
}
