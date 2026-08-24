package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import com.piercingxx.txxt.R
import com.piercingxx.txxt.theme.SharedPreferencesThemeKeyValueStore
import com.piercingxx.txxt.theme.ThemeController
import com.piercingxx.txxt.theme.ThemeStore
import com.piercingxx.txxt.theme.ThemePreset
import java.io.File

/**
 * The settings screen (WS12 T5).
 *
 * Exposes the persisted [SettingsStore] — lock-screen privacy, the global
 * notification alert style, the theme auto-sync toggle, the theme preset, and
 * the font mode — as Views (docs/DESIGN.md §Stack). Each control change writes
 * the whole store back to [SharedPreferences] through [SettingsBackup]'s
 * string-map format, so the store round-trips through the same shape the
 * backup/restore (WS12 T4) uses.
 *
 * The theme controls drive the REAL rendering path, not a shadow copy: every
 * persist also reports the picked preset and auto-sync toggle to a
 * [ThemeController] built over the same `txxt_theme` SharedPreferences the
 * launcher ([com.piercingxx.txxt.MainActivity]) and the thread screen wire —
 * so what the picker shows is what ThemeApplier paints.
 *
 * Backup/restore is honest I/O (WS12 T4): backup serialises BOTH stores
 * (settings + blocking/starred) through [SettingsBackupFile] and writes them
 * to `filesDir/[BACKUP_FILE_NAME]` behind the injectable [writeBackupText]
 * seam; restore reads through [readBackupText], applies every recognised key
 * into prefs, reloads the controls, and reports the real outcome.
 *
 * FLAG_SECURE is set in code (docs/PRIVACY.md §3) so the screen never appears
 * in recents previews or screenshots.
 *
 * The blocking button (WS12-corrective T2) load-and-applies the *persisted*
 * blocking/starred settings to the running app through the
 * [SettingsBlockingStore] seam — [SettingsBlockingStore.loadAndApply] rebuilds
 * the process-wide [com.piercingxx.txxt.block.InboundFilter] the inbound
 * receivers read.
 */
class SettingsActivity : Activity() {

    internal companion object {
        /** SharedPreferences name for the settings store. */
        const val PREFS_NAME = "txxt_settings"

        /**
         * SharedPreferences name for the theme render path's store — the same
         * file [com.piercingxx.txxt.MainActivity] and the thread screen read,
         * so a pick made here is what the UI actually renders.
         */
        const val THEME_PREFS_NAME = "txxt_theme"

        /** Name (inside `filesDir`) of the settings backup file. */
        const val BACKUP_FILE_NAME = "txxt-settings-backup.txt"
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var lockScreenPrivacy: Spinner
    private lateinit var alertStyle: Spinner
    private lateinit var autoSyncTheme: Switch
    private lateinit var themePreset: Spinner
    private lateinit var fontMode: Spinner

    /**
     * The theme controller over the SAME persisted store the render path reads
     * (`txxt_theme`). Lazy so it is built only once the context exists; this is
     * exactly the wiring ThreadActivity uses for its own controller.
     */
    private val themeController: ThemeController by lazy {
        ThemeController(
            ThemeStore(
                SharedPreferencesThemeKeyValueStore(
                    getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                )
            )
        )
    }

    /**
     * Init guard (WS12 corrective): spinners fire an initial selection event as
     * soon as their adapters populate, before the persisted store has been
     * loaded. Persisting on those events would write back a partially-loaded
     * store. Both guards below suppress them:
     *
     *  - [controlsReady] blocks events that arrive while [loadIntoControls] is
     *    still mid-flight (the Switch fires synchronously during load).
     *  - [lastLoadedStore] swallows the spinner callbacks Android posts AFTER
     *    loading completes: if the reported selection equals what was just
     *    loaded, nothing changed, so there is nothing to persist.
     */
    private var controlsReady = false
    private var lastLoadedStore: SettingsStore? = null

    /**
     * Seam: persists the backup payload. Injectable so a JVM test can capture
     * the written text without touching a filesystem; the default does real
     * file I/O into `filesDir`.
     */
    internal var writeBackupText: (String) -> Unit = ::defaultWriteBackupText

    /**
     * Seam: reads the previously written backup payload, or null when no
     * backup exists. Injectable for JVM tests; the default does real file I/O
     * from `filesDir`.
     */
    internal var readBackupText: () -> String? = ::defaultReadBackupText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        lockScreenPrivacy = findViewById(R.id.lock_screen_privacy_spinner)
        alertStyle = findViewById(R.id.notification_posture_spinner)
        autoSyncTheme = findViewById(R.id.auto_sync_theme_switch)
        themePreset = findViewById(R.id.theme_preset_spinner)
        fontMode = findViewById(R.id.font_mode_spinner)

        bindControls()
        loadIntoControls()
    }

    /**
     * Populates the spinners with human-readable labels and wires each
     * control's change to a persist of the whole store. The preset spinner
     * shows each preset's display name ("AMOLED Night", …) and maps positions
     * back through [ThemePreset.entries]; every other spinner shows enum names.
     */
    private fun bindControls() {
        val lockPrivacyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            LockScreenPrivacy.entries.map { it.name },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        lockScreenPrivacy.adapter = lockPrivacyAdapter

        val styleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            AlertStyle.entries.map { it.name },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        alertStyle.adapter = styleAdapter

        val presetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ThemePreset.entries.map { it.displayName },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        themePreset.adapter = presetAdapter

        val fontAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            FontMode.entries.map { it.name },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        fontMode.adapter = fontAdapter

        val onChanged = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (!controlsReady) return
                val store = currentStore()
                // Initial-selection echo: Android delivers one callback per
                // spinner after load; skip when nothing actually changed.
                if (store == lastLoadedStore) return
                persist(store)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        lockScreenPrivacy.onItemSelectedListener = onChanged
        alertStyle.onItemSelectedListener = onChanged
        themePreset.onItemSelectedListener = onChanged
        fontMode.onItemSelectedListener = onChanged

        autoSyncTheme.setOnCheckedChangeListener { _, _ ->
            if (!controlsReady) return@setOnCheckedChangeListener
            persist(currentStore())
        }

        findViewById<Button>(R.id.backup_button).setOnClickListener { runBackup() }
        findViewById<Button>(R.id.restore_button).setOnClickListener { runRestore() }
        findViewById<Button>(R.id.blocking_button).setOnClickListener {
            // WS12-corrective T2: the blocking button load-and-applies the
            // *persisted* blocking/starred settings through the
            // SettingsBlockingStore seam. The store is rebuilt from
            // SharedPreferences (the same backup string-map shape SettingsBackup
            // uses) and loadAndApply() drives the LiveInboundFilter seam that
            // rebuilds the process-wide InboundFilter the inbound receivers read.
            loadBlockingStore().loadAndApply()
            Toast.makeText(this, "Blocking & starred applied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Reads the current control selections into a [SettingsStore]. */
    private fun currentStore(): SettingsStore = SettingsStore(
        lockScreenPrivacy = LockScreenPrivacy.entries[lockScreenPrivacy.selectedItemPosition],
        alertStyle = AlertStyle.entries[alertStyle.selectedItemPosition],
        autoSyncTheme = autoSyncTheme.isChecked,
        themePreset = ThemePreset.entries[themePreset.selectedItemPosition],
        fontMode = FontMode.entries[fontMode.selectedItemPosition],
    )

    /** Loads the persisted store (falling back to defaults) into the controls. */
    private fun loadIntoControls() {
        controlsReady = false
        val store = loadStore()
        lockScreenPrivacy.setSelection(store.lockScreenPrivacy.ordinal)
        alertStyle.setSelection(store.alertStyle.ordinal)
        autoSyncTheme.isChecked = store.autoSyncTheme
        themePreset.setSelection(ThemePreset.entries.indexOf(store.themePreset))
        fontMode.setSelection(store.fontMode.ordinal)
        controlsReady = true
        lastLoadedStore = store
    }

    /**
     * Persists [store] to [SharedPreferences] through [SettingsBackup]'s
     * string-map format AND drives the real theme render path, so the pick the
     * user sees here is what ThemeApplier paints.
     */
    private fun persist(store: SettingsStore) {
        prefs.edit().apply {
            SettingsBackup.toSettingsMap(store).forEach { (k, v) -> putString(k, v) }
        }.apply()
        syncRenderTheme(store)
        lastLoadedStore = store
    }

    /** Reports the theme intent to the render path's controller over `txxt_theme`. */
    private fun syncRenderTheme(store: SettingsStore) {
        themeController.setManualTheme(store.themePreset)
        themeController.setAutoSync(store.autoSyncTheme)
    }

    /** Reads the [SettingsStore] back from [SharedPreferences], defaulting on a fresh install. */
    private fun loadStore(): SettingsStore {
        val map = SettingsBackup.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        return SettingsBackup.fromSettingsMap(map)
    }

    /**
     * Reads the persisted [SettingsBlockingStore] back from [SharedPreferences],
     * defaulting to empty sets on a fresh install. The blocking/starred sets are
     * persisted under the same backup string-map keys [SettingsBlockingStore]
     * round-trips through, so the button's load-and-apply path rebuilds the live
     * filter from exactly what was persisted.
     */
    private fun loadBlockingStore(): SettingsBlockingStore {
        val map = SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        return SettingsBlockingStore.fromMap(map)
    }

    /**
     * Honest backup: serialise BOTH stores into one payload via
     * [SettingsBackupFile] and write it behind the [writeBackupText] seam.
     * The toast reports the real outcome — success names the file.
     */
    private fun runBackup() {
        val payload = SettingsBackupFile.encode(
            settings = SettingsBackup.toSettingsMap(currentStore()),
            blocking = SettingsBlockingStore.toMap(loadBlockingStore()),
        )
        try {
            writeBackupText(payload)
            Toast.makeText(this, "Backed up to $BACKUP_FILE_NAME", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Honest restore: read the payload (null → "No backup found"), parse it,
     * apply every recognised key of BOTH stores into prefs, reload the
     * controls from the restored state, push the restored theme intent to the
     * render path, and report how many keys were restored.
     */
    private fun runRestore() {
        val text = try {
            readBackupText()
        } catch (e: Exception) {
            null
        } ?: run {
            Toast.makeText(this, "No backup found", Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = SettingsBackupFile.decode(text) ?: run {
            Toast.makeText(this, "No backup found", Toast.LENGTH_SHORT).show()
            return
        }
        val (settingsMap, blockingMap) = parsed
        var restoredKeys = 0
        prefs.edit().apply {
            (SettingsBackup.KEY_NAMES + SettingsBlockingStore.KEY_NAMES).forEach { key ->
                val value = settingsMap[key] ?: blockingMap[key]
                if (value != null) {
                    putString(key, value)
                    restoredKeys++
                }
            }
        }.apply()

        loadIntoControls()
        // The restored theme must reach the render path immediately, not just
        // wait for the next control change.
        syncRenderTheme(loadStore())
        // Restored blocking/starred rules must reach the running inbound filter
        // immediately too — otherwise they sit inert in prefs until process
        // death (receiver ensureLoaded) or a manual blocking-button press.
        loadBlockingStore().loadAndApply()

        Toast.makeText(this, "Restored $restoredKeys keys", Toast.LENGTH_SHORT).show()
    }

    /** Default [writeBackupText]: real file I/O into `filesDir`. */
    private fun defaultWriteBackupText(text: String) {
        File(filesDir, BACKUP_FILE_NAME).writeText(text)
    }

    /** Default [readBackupText]: real file I/O from `filesDir`, null when absent. */
    private fun defaultReadBackupText(): String? =
        File(filesDir, BACKUP_FILE_NAME).takeIf { it.exists() }?.readText()
}
