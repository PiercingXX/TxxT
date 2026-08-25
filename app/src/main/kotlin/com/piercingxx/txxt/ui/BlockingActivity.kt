package com.piercingxx.txxt.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.piercingxx.txxt.R

/**
 * The blocking & starred editor (docs/FEATURES.md §Privacy/blocking).
 *
 * Edits the four persisted rule sets — blocked numbers, keyword rules, phrase
 * rules, starred contacts — that drive the process-wide inbound filter. Every
 * edit goes through the pure [BlockingRules] transforms, is persisted to the
 * same SharedPreferences string-map the settings screen and backup (WS12 T4)
 * read, and is applied to the live filter immediately via
 * [SettingsBlockingStore.loadAndApply] — a change here blocks (or stars) the
 * very next inbound message, no restart, no extra "apply" step.
 *
 * FLAG_SECURE is set in code (docs/PRIVACY.md §3), like every other screen.
 */
class BlockingActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocking)

        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        wireSection(
            key = SettingsBlockingStore.KEY_BLOCKED_ADDRESSES,
            containerId = R.id.blocked_container,
            inputId = R.id.blocked_input,
            addId = R.id.blocked_add,
        )
        wireSection(
            key = SettingsBlockingStore.KEY_KEYWORDS,
            containerId = R.id.keyword_container,
            inputId = R.id.keyword_input,
            addId = R.id.keyword_add,
        )
        wireSection(
            key = SettingsBlockingStore.KEY_PHRASES,
            containerId = R.id.phrase_container,
            inputId = R.id.phrase_input,
            addId = R.id.phrase_add,
        )
        wireSection(
            key = SettingsBlockingStore.KEY_STARRED_CONTACTS,
            containerId = R.id.starred_container,
            inputId = R.id.starred_input,
            addId = R.id.starred_add,
        )
    }

    /** Wires one rule section: renders its entries and hooks its ADD button. */
    private fun wireSection(key: String, containerId: Int, inputId: Int, addId: Int) {
        val container = findViewById<LinearLayout>(containerId)
        val input = findViewById<EditText>(inputId)
        renderEntries(key, container)
        findViewById<Button>(addId).setOnClickListener {
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) return@setOnClickListener
            persist(BlockingRules.withEntry(currentMap(), key, value))
            input.setText("")
            renderEntries(key, container)
        }
    }

    /** Renders [key]'s current entries as tap-to-remove rows into [container]. */
    private fun renderEntries(key: String, container: LinearLayout) {
        container.removeAllViews()
        BlockingRules.entries(currentMap(), key).sorted().forEach { value ->
            container.addView(TextView(this).apply {
                text = value
                typeface = Typeface.MONOSPACE
                textSize = 14f
                setTextColor(0xB3FFFFFF.toInt())
                setPadding(8, 12, 8, 12)
                setOnClickListener { confirmRemove(key, value, container) }
            })
        }
    }

    /** Confirms, then removes [value] from [key]'s set and re-applies. */
    private fun confirmRemove(key: String, value: String, container: LinearLayout) {
        AlertDialog.Builder(this)
            .setTitle("Remove \"$value\"?")
            .setPositiveButton("Remove") { _, _ ->
                persist(BlockingRules.withoutEntry(currentMap(), key, value))
                renderEntries(key, container)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** The persisted blocking/starred string map, straight from prefs. */
    private fun currentMap(): Map<String, String> =
        SettingsBlockingStore.KEY_NAMES
            .mapNotNull { key -> prefs.getString(key, null)?.let { key to it } }
            .toMap()

    /**
     * Persists [map] and applies it to the running inbound filter — the edit
     * is live for the very next inbound message.
     */
    private fun persist(map: Map<String, String>) {
        prefs.edit().apply {
            map.forEach { (key, value) -> putString(key, value) }
        }.apply()
        SettingsBlockingStore.fromMap(map).loadAndApply()
    }
}
