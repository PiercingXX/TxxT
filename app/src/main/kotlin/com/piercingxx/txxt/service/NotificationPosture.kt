package com.piercingxx.txxt.service

/**
 * Pure notification-posture policy. Zero `android.*` imports so the decision
 * logic is JVM-testable in the app module's unit tests.
 *
 * Three decisions, all from `docs/PRIVACY.md` §3 and §6:
 *  - **Starred contacts always notify** — starred contacts bypass every
 *    suppression (`docs/PRIVACY.md:112-115`);
 *  - **Per-contact override beats global** — a per-contact setting takes
 *    precedence over the global posture default
 *    (`docs/PRIVACY.md:167`);
 *  - **Redacted by default** — unstarred contacts without an override get the
 *    global posture, which defaults to redacted (sender-name-only)
 *    (`docs/PRIVACY.md:60-61`).
 */
object NotificationPosture {

    /**
     * The posture decision for a single incoming message.
     */
    enum class Posture {
        /** Full notification — sender name and content visible. */
        NOTIFY,

        /** Redacted notification — sender name only, no message body. */
        REDACTED,

        /** No notification posted at all. */
        SUPPRESS,
    }

    /**
     * Per-contact notification override. [UNSET] leaves the global posture
     * to decide; [NOTIFY] / [REDACTED] / [SUPPRESS] force the decision for
     * a specific sender.
     */
    enum class Override { NOTIFY, REDACTED, SUPPRESS, UNSET }

    /**
     * Decide the notification posture for a message from [sender].
     *
     * Priority (highest first):
     * 1. **Starred** — always [Posture.NOTIFY] (`docs/PRIVACY.md:112-115`).
     * 2. **Per-contact override** — if set, it beats the global default
     *    (`docs/PRIVACY.md:167`).
     * 3. **Global posture** — the default when nothing else applies.
     */
    fun decide(
        sender: String,
        starred: Boolean,
        globalPosture: Posture,
        overrides: Map<String, Override> = emptyMap(),
        muted: Boolean = false,
    ): Posture {
        if (starred) return Posture.NOTIFY
        if (muted) return Posture.SUPPRESS
        val override = overrides[sender] ?: Override.UNSET
        return if (override != Override.UNSET) {
            Posture.valueOf(override.name)
        } else {
            globalPosture
        }
    }
}
