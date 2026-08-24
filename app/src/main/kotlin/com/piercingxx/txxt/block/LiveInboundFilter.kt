package com.piercingxx.txxt.block

import android.content.Context
import com.piercingxx.txxt.ui.SettingsActivity
import com.piercingxx.txxt.ui.SettingsBlocking
import com.piercingxx.txxt.ui.SettingsBlockingStore
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
 * **Process-death hydration (M5):** [apply] only ran when the user pressed the
 * settings blocking button, so after process death or reboot the receivers read
 * an empty [InboundFilter] until Settings was reopened. The inbound receivers
 * now call [ensureLoaded] at the top of `onReceive`: once per process it reads
 * the persisted blocking/starred sets from SharedPreferences (`txxt_settings`,
 * the same keys [SettingsBlockingStore.KEY_NAMES] round-trips) through
 * [SettingsBlockingStore.fromMap] and applies them via the existing [apply]
 * path. An explicit in-process [apply] marks the store loaded, so a later
 * `ensureLoaded` never clobbers fresher in-memory rules with persisted ones.
 *
 * Testability seams: [blockingStoreLoader] substitutes the real prefs reader
 * (JVM tests have no device), and [resetForTest] restores fresh-object state.
 * The loader seam means this object touches Android only through the injected
 * `Context` consumer; the decision logic stays JVM-testable.
 */
object LiveInboundFilter {

    @Volatile
    private var liveFilter: InboundFilter = InboundFilter()

    @Volatile
    private var loaded: Boolean = false

    private val DEFAULT_LOADER: (Context) -> SettingsBlockingStore = { context ->
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val map = SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        SettingsBlockingStore.fromMap(map)
    }

    /**
     * Reads the persisted blocking/starred store. Injectable internal seam so
     * JVM tests can drive [ensureLoaded] without a device; defaults to reading
     * the real SharedPreferences the settings screen persists to.
     */
    internal var blockingStoreLoader: (Context) -> SettingsBlockingStore = DEFAULT_LOADER

    /**
     * Stores the [InboundFilter] built from [blocking] and [starred] as the
     * process-wide current filter. This is the path the settings screen uses to
     * apply blocking/starred changes, so its behaviour is the wiring's
     * behaviour. Also marks the store loaded: an explicit in-process
     * application supersedes persisted-state hydration.
     */
    fun apply(blocking: SettingsBlocking, starred: SettingsStarred) {
        liveFilter = blocking.filter(starred)
        loaded = true
    }

    /**
     * Hydrates the persisted blocking/starred rules exactly once per process,
     * before the receivers evaluate anything. Idempotent: after the first load
     * (or an explicit [apply]) subsequent calls are no-ops, so a receiver can
     * call it on every broadcast without re-reading prefs.
     */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val store = blockingStoreLoader(context)
            apply(store.buildBlocking(), store.buildStarred())
        }
    }

    /** The current process-wide [InboundFilter] the inbound receivers apply. */
    val current: InboundFilter
        get() = liveFilter

    /** Restores fresh-object state (and the default loader) between tests. */
    internal fun resetForTest() {
        synchronized(this) {
            liveFilter = InboundFilter()
            blockingStoreLoader = DEFAULT_LOADER
            loaded = false
        }
    }
}
