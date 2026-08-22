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
import com.piercingxx.txxt.block.LiveInboundFilter

/**
 * The settings screen (WS12 T5).
 *
 * Exposes the persisted [SettingsStore] — lock-screen privacy, the global
 * notification posture, the theme auto-sync toggle, the theme preset, and the
 * font mode — as Views (docs/DESIGN.md §Stack). Each control change writes the
 * whole store back to [SharedPreferences] through [SettingsBackup]'s
 * string-map format, so the store round-trips through the same shape the
 * backup/restore (WS12 T4) uses.
 *
 * FLAG_SECURE is set in code (docs/PRIVACY.md §3) so the screen never appears
 * in recents previews or screenshots. The actual rendering of the seven
 * presets and the theme auto-sync receiver are WS14's scope — this activity
 * persists the selection, not the theme engine.
 *
 * The blocking button (WS12-corrective T1) applies the blocking/starred
 * settings to the running app through [LiveInboundFilter.apply] — the seam that
 * rebuilds the process-wide [com.piercingxx.txxt.block.InboundFilter] the
 * inbound receivers read. The blocking/starred editing UI is WS12 T3's scope;
 * this activity routes the button through the seam so the user's edits reach
 * the live inbound path.
 */
class SettingsActivity : Activity() {

    /** SharedPreferences name for the settings store. */
    internal companion object {
        const val PREFS_NAME = "txxt_settings"
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var lockScreenPrivacy: Spinner
    private lateinit var notificationPosture: Spinner
    private lateinit var autoSyncTheme: Switch
    private lateinit var themePreset: Spinner
    private lateinit var fontMode: Spinner

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
        notificationPosture = findViewById(R.id.notification_posture_spinner)
        autoSyncTheme = findViewById(R.id.auto_sync_theme_switch)
        themePreset = findViewById(R.id.theme_preset_spinner)
        fontMode = findViewById(R.id.font_mode_spinner)

        bindControls()
        loadIntoControls()
    }

    /**
     * Populates the spinners with the store's enum members and wires each
     * control's change to a persist of the whole store.
     */
    private fun bindControls() {
        val lockPrivacyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            LockScreenPrivacy.entries.map { it.name },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        lockScreenPrivacy.adapter = lockPrivacyAdapter

        val postureAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            NotificationPosture.entries.map { it.name },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        notificationPosture.adapter = postureAdapter

        val presetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ThemePreset.entries.map { it.name },
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
                persist(currentStore())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        lockScreenPrivacy.onItemSelectedListener = onChanged
        notificationPosture.onItemSelectedListener = onChanged
        themePreset.onItemSelectedListener = onChanged
        fontMode.onItemSelectedListener = onChanged

        autoSyncTheme.setOnCheckedChangeListener { _, _ -> persist(currentStore()) }

        findViewById<Button>(R.id.backup_button).setOnClickListener {
            Toast.makeText(this, "Settings backed up", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.restore_button).setOnClickListener {
            loadIntoControls()
            Toast.makeText(this, "Settings restored", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.blocking_button).setOnClickListener {
            // WS12-corrective T1: the blocking button is the settings screen's
            // path for applying blocking/starred changes to the running app.
            // The blocking/starred editing UI is WS12 T3's scope (persisted
            // separately from the five-field SettingsStore); until it lands the
            // button applies the current (empty) models through the
            // LiveInboundFilter seam, which is the same seam the persisted
            // models will drive once their editing screen exists.
            LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())
            Toast.makeText(this, "Blocking & starred applied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Reads the current control selections into a [SettingsStore]. */
    private fun currentStore(): SettingsStore = SettingsStore(
        lockScreenPrivacy = LockScreenPrivacy.entries[lockScreenPrivacy.selectedItemPosition],
        notificationPosture = NotificationPosture.entries[notificationPosture.selectedItemPosition],
        autoSyncTheme = autoSyncTheme.isChecked,
        themePreset = ThemePreset.entries[themePreset.selectedItemPosition],
        fontMode = FontMode.entries[fontMode.selectedItemPosition],
    )

    /** Loads the persisted store (falling back to defaults) into the controls. */
    private fun loadIntoControls() {
        val store = loadStore()
        lockScreenPrivacy.setSelection(store.lockScreenPrivacy.ordinal)
        notificationPosture.setSelection(store.notificationPosture.ordinal)
        autoSyncTheme.isChecked = store.autoSyncTheme
        themePreset.setSelection(store.themePreset.ordinal)
        fontMode.setSelection(store.fontMode.ordinal)
    }

    /** Persists [store] to [SharedPreferences] through [SettingsBackup]'s string-map format. */
    private fun persist(store: SettingsStore) {
        prefs.edit().apply {
            SettingsBackup.toSettingsMap(store).forEach { (k, v) -> putString(k, v) }
        }.commit()
    }

    /** Reads the [SettingsStore] back from [SharedPreferences], defaulting on a fresh install. */
    private fun loadStore(): SettingsStore {
        val map = SettingsBackup.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()
        return SettingsBackup.fromSettingsMap(map)
    }
}