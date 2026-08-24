package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.BackupSerializer

/**
 * Adapter for the backup JSON (WS6 T3).
 *
 * Delegates entirely to the validating [BackupSerializer] in `core`, so there
 * is exactly one backup format and one parser for it: every payload that enters
 * the app through [deserialize] passes the serializer's **version gate** (only
 * [com.piercingxx.txxt.core.BACKUP_VERSION] is accepted) and its **range gate**
 * (message ids, thread ids and dates must be non-negative numbers), and strict
 * parsing rejects malformed or trailing input instead of applying lenient
 * defaults. No reflection-based parser sits in this path, so an untrusted or
 * hand-edited backup cannot smuggle attacker-chosen ids past validation into
 * the REPLACE-upsert restore seams.
 *
 * On invalid input [deserialize] throws [com.piercingxx.txxt.core.BackupException]
 * (an [IllegalArgumentException] subtype) describing the violated gate; it never
 * returns a partially defaulted model. Round-tripping a [BackupData] through
 * serialize/deserialize is lossless (verified by [BackupJsonTest]).
 */
object BackupJson {

    /** Renders [data] to the canonical local-JSON backup string. */
    fun serialize(data: BackupData): String = BackupSerializer.serialize(data)

    /**
     * Parses and validates a backup JSON string back into its [BackupData]
     * model, throwing [com.piercingxx.txxt.core.BackupException] when the
     * version gate, range gate or JSON syntax rejects it.
     */
    fun deserialize(json: String): BackupData = BackupSerializer.deserialize(json)
}
