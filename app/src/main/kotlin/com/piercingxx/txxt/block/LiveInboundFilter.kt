package com.piercingxx.txxt.block

import android.content.Context
import com.piercingxx.txxt.contacts.ContactDirectory
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
 * Known contacts and block-overrides are rebound on every [ensureLoaded] so a
 * contact saved (or a Deliver-from-quarantine override written) after the
 * first apply is visible to the next inbound without a restart.
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

    @Volatile
    private var lastBlocking: SettingsBlocking = SettingsBlocking()

    @Volatile
    private var lastStarred: SettingsStarred = SettingsStarred()

    @Volatile
    private var lastQuarantine: Boolean = false

    private val DEFAULT_LOADER: (Context) -> SettingsBlockingStore = { context ->
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val map = SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        SettingsBlockingStore.fromMap(map)
    }

    private val DEFAULT_CONTACTS: (Context) -> Set<String> = { context ->
        ContactDirectory(context).all().map { it.number }.toSet()
    }

    private val DEFAULT_OVERRIDES: (Context) -> BlockOverrideStore = { context ->
        BlockOverrideStore(
            SharedPreferencesBlockOverrideKeyValueStore(
                context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE),
            ),
        )
    }

    /**
     * Reads the persisted blocking/starred store. Injectable internal seam so
     * JVM tests can drive [ensureLoaded] without a device; defaults to reading
     * the real SharedPreferences the settings screen persists to.
     */
    internal var blockingStoreLoader: (Context) -> SettingsBlockingStore = DEFAULT_LOADER

    /** Live known-contact lookup. Injectable for JVM tests. */
    internal var knownContactsLoader: (Context) -> Set<String> = DEFAULT_CONTACTS

    /** Per-sender deliver overrides (quarantine review → Deliver). */
    internal var overrideStoreLoader: (Context) -> BlockOverrideStore = DEFAULT_OVERRIDES

    @Volatile
    private var knownContactsProvider: () -> Set<String> = { emptySet() }

    @Volatile
    private var blockOverrideStore: BlockOverrideStore? = null

    /**
     * Stores the [InboundFilter] built from [blocking] and [starred] as the
     * process-wide current filter. This is the path the settings screen uses to
     * apply blocking/starred changes, so its behaviour is the wiring's
     * behaviour. Also marks the store loaded: an explicit in-process
     * application supersedes persisted-state hydration.
     */
    fun apply(
        blocking: SettingsBlocking,
        starred: SettingsStarred,
        quarantineUnknownSenders: Boolean = false,
    ) {
        lastBlocking = blocking
        lastStarred = starred
        lastQuarantine = quarantineUnknownSenders
        rebuild()
        loaded = true
    }

    private fun rebuild() {
        liveFilter = lastBlocking.filter(
            lastStarred,
            quarantineUnknownSenders = lastQuarantine,
            knownContactsProvider = { knownContactsProvider() },
            blockOverrideStore = blockOverrideStore,
        )
    }

    /**
     * Hydrates the persisted blocking/starred rules exactly once per process,
     * before the receivers evaluate anything. Idempotent for the rule sets:
     * after the first load (or an explicit [apply]) subsequent calls do not
     * re-read prefs. Known contacts and block-overrides are rebound every
     * time so a just-saved contact or a just-delivered sender is live.
     */
    fun ensureLoaded(context: Context) {
        val app = context.applicationContext
        knownContactsProvider = { knownContactsLoader(app) }
        if (blockOverrideStore == null) {
            blockOverrideStore = overrideStoreLoader(app)
        }
        if (loaded) {
            rebuild()
            return
        }
        synchronized(this) {
            if (loaded) {
                rebuild()
                return
            }
            val store = blockingStoreLoader(app)
            apply(
                store.buildBlocking(),
                store.buildStarred(),
                quarantineUnknownSenders = store.quarantineUnknownSenders(),
            )
        }
    }

    /**
     * The process-wide override store the quarantine review writes to when
     * the operator taps Deliver. Hydrates via [ensureLoaded] if needed.
     */
    fun overrideStore(context: Context): BlockOverrideStore {
        ensureLoaded(context)
        return blockOverrideStore ?: overrideStoreLoader(context.applicationContext).also {
            blockOverrideStore = it
            rebuild()
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
            knownContactsLoader = DEFAULT_CONTACTS
            overrideStoreLoader = DEFAULT_OVERRIDES
            knownContactsProvider = { emptySet() }
            blockOverrideStore = null
            lastBlocking = SettingsBlocking()
            lastStarred = SettingsStarred()
            lastQuarantine = false
            loaded = false
        }
    }
}
