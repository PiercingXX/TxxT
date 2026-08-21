package com.piercingxx.txxt.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.piercingxx.txxt.core.BackupData

/**
 * Gson-backed adapter for the backup JSON (WS6 T3).
 *
 * This is the "wired to Gson for the backup JSON" slice of the data layer: it
 * hands the pure `core` backup model ([BackupData]) to Gson 2.10.1 for the
 * on-disk JSON, matching the Room + Gson stack at `docs/DESIGN.md:86` and the
 * `BackupJson` package entry at `docs/INSPIRATION.md:146`.
 *
 * The adapter is a thin, `TypeToken`-driven wrapper over a shared [Gson]
 * instance so the data layer can serialize and deserialize the backup payload
 * without leaking Gson into the rest of the app. Round-tripping a [BackupData]
 * is lossless for the messages/settings/blocklist/starred sections (verified by
 * [BackupJsonTest]).
 */
object BackupJson {

    /** Shared Gson instance configured for the backup payload. */
    private val gson: Gson by lazy { Gson() }

    /** The [BackupData] type token, so Gson can reflect the generic payload. */
    private val backupDataType: TypeToken<BackupData> = object : TypeToken<BackupData>() {}

    /** Renders [data] to the backup JSON string. */
    fun serialize(data: BackupData): String = gson.toJson(data, backupDataType.type)

    /** Parses a backup JSON string back into its [BackupData] model. */
    fun deserialize(json: String): BackupData =
        gson.fromJson(json, backupDataType.type)
}