package com.piercingxx.txxt.service

/**
 * Pure receive-policy slice of the service layer, consumed by the
 * `MmsReceiver`/`SmsReceiver` (T3). Zero `android.*` imports so the decision
 * logic is JVM-testable in the app module's unit tests.
 *
 * Two decisions, both from `docs/PRIVACY.md:91-101`:
 *  - **audio-MMS drop**: an inbound MMS whose attachment is audio is not
 *    downloaded and not stored — dropped at the inbox boundary
 *    (`docs/PRIVACY.md:91-92`);
 *  - **auto-reply gate**: the optional auto-reply SMS telling the sender voice
 *    messages aren't accepted is off by default and per-contact overridable
 *    (`docs/PRIVACY.md:96-97`).
 */
object ReceivePolicy {

    /** The auto-reply SMS body (`docs/PRIVACY.md:97`). */
    const val AUTO_REPLY_BODY: String =
        "Voice messages aren't accepted. Send text or a photo."

    /**
     * Decision for an inbound MMS attachment part.
     *
     * [DROP_UNSTORED] means the attachment (and its message) is dropped at the
     * inbox boundary — never downloaded, never stored. [STORE] means the
     * attachment is a normal (non-audio) part and the message may be persisted.
     */
    enum class AttachmentDecision {
        /** Drop at the inbox boundary; do not download and do not store. */
        DROP_UNSTORED,

        /** A normal attachment; the message may be stored. */
        STORE,
    }

    /**
     * Decides what to do with an inbound MMS attachment given its MIME/content
     * type. Audio parts are dropped un-stored (`docs/PRIVACY.md:91-92`);
     * everything else passes through for storage.
     */
    fun decideAttachment(contentType: String?): AttachmentDecision =
        if (contentType != null && contentType.startsWith("audio/")) {
            AttachmentDecision.DROP_UNSTORED
        } else {
            AttachmentDecision.STORE
        }

    /**
     * Per-contact override for the auto-reply SMS. [UNSET] leaves the global
     * setting to decide; [ON]/[OFF] force the decision for a specific sender.
     */
    enum class AutoReplyOverride { ON, OFF, UNSET }

    /**
     * Decides whether to send the auto-reply SMS to [sender].
     *
     * The auto-reply is off by default (`docs/PRIVACY.md:96`): when [enabled]
     * is false the reply never fires, regardless of any per-contact override.
     * When [enabled] is true, a per-contact [overrides] entry can keep a
     * specific sender off ([AutoReplyOverride.OFF]) or leave the global-on
     * behaviour ([AutoReplyOverride.ON] / [AutoReplyOverride.UNSET]).
     */
    fun shouldAutoReply(
        enabled: Boolean,
        sender: String,
        overrides: Map<String, AutoReplyOverride> = emptyMap(),
    ): Boolean {
        if (!enabled) return false
        val override = overrides[sender] ?: AutoReplyOverride.UNSET
        return override != AutoReplyOverride.OFF
    }
}