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
     */
    fun redactedContent(sender: String): String = sender

    /**
     * The notification title — always the sender name.
     */
    fun notificationTitle(sender: String): String = sender

    /**
     * Whether the notification should carry bubble data.
     * Always false — nothing ever bubbles (`docs/PRIVACY.md:51-53`).
     */
    fun shouldBubble(): Boolean = false
}
