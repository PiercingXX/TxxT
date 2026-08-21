package com.piercingxx.txxt.service

/**
 * Pure send-policy slice of the service layer, consumed by the `SendPipeline`
 * (T3). Zero `android.*` imports so the decision logic is JVM-testable in the
 * app module's unit tests.
 *
 * Two decisions, both from `docs/PRIVACY.md`:
 *  - **no delivery/read reports**: an outgoing SMS/MMS never requests a
 *    delivery or read report (`docs/PRIVACY.md:23,32`) — the send pipeline
 *    passes a `null` delivery/read `PendingIntent` to `SmsManager`;
 *  - **MMS no auto-download**: remote MMS content is fetched only on explicit
 *    tap, never automatically (`docs/PRIVACY.md:151-153`).
 */
object SendPolicy {

    /**
     * Whether an outgoing SMS/MMS should request a delivery report. Always
     * `false`: TxxT never requests delivery reports (`docs/PRIVACY.md:23`),
     * so the send pipeline passes a `null` delivery `PendingIntent`.
     */
    fun requestsDeliveryReport(): Boolean = false

    /**
     * Whether an outgoing MMS should request a read report. Always `false`
     * (`docs/PRIVACY.md:23`), so the send pipeline passes a `null` read
     * `PendingIntent`.
     */
    fun requestsReadReport(): Boolean = false

    /**
     * Whether remote MMS content should be downloaded automatically. Always
     * `false`: MMS is fetched only on explicit tap (`docs/PRIVACY.md:151-153`),
     * which prevents IP disclosure, tracking-pixel fetches, and surprise data
     * usage.
     */
    fun autoDownloadMms(): Boolean = false
}