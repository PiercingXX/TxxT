package com.piercingxx.txxt.block

import com.piercingxx.txxt.ui.SettingsBlocking
import com.piercingxx.txxt.ui.SettingsStarred

/**
 * Process-wide holder for the live inbound filter (WS12-corrective T1).
 *
 * This is the wiring seam that carries the blocking/starred settings the user
 * edits on the settings screen into the running inbound path. The settings
 * screen's blocking button calls [apply] with the current
 * [SettingsBlocking]/[SettingsStarred] models; [SettingsBlocking.filter] builds
 * the [InboundFilter] from those rules, and [current] hands that filter to the
 * manifest-declared inbound receivers (SmsReceiver/MmsReceiver, WS12-corrective
 * T2). Before this seam existed the settings screen never instantiated the
 * blocking/starred models and the receivers defaulted to an empty
 * [InboundFilter], so the user's edits never reached the app (Nagatha's BLOCK
 * finding).
 *
 * An `object` (mirroring the singleton-holder note in
 * `data/TxxTDatabase.kt:32`) so the receivers read the same process-wide filter
 * the settings screen last stored. Pure Kotlin with zero `android.*` imports —
 * JVM-testable without a device, mirroring the WS11-corrective
 * `ThreadMessageLoader` pure-seam pattern.
 */
object LiveInboundFilter {

    @Volatile
    private var liveFilter: InboundFilter = InboundFilter()

    /**
     * Stores the [InboundFilter] built from [blocking] and [starred] as the
     * process-wide current filter. This is the *only* path the settings screen
     * uses to apply blocking/starred changes, so its behaviour is the wiring's
     * behaviour.
     */
    fun apply(blocking: SettingsBlocking, starred: SettingsStarred) {
        liveFilter = blocking.filter(starred)
    }

    /** The current process-wide [InboundFilter] the inbound receivers apply. */
    val current: InboundFilter
        get() = liveFilter
}