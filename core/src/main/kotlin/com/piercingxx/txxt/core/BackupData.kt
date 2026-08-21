package com.piercingxx.txxt.core

/**
 * Pure-JVM backup model (WS5). The backup is local JSON covering the four
 * sections the goal names — messages, settings, blocklist, starred — plus a
 * schema version so a backup written by a newer app build can be detected and
 * refused rather than silently mis-imported.
 *
 * No android.* imports: this is the message/state-machine domain that must be
 * JVM-testable without a device. Field names follow Gson's lowerCamelCase
 * conventions so the shape is consistent with the launcher's Gson-based export;
 * byte-for-byte compatibility with the launcher's literal file is a cross-repo
 * check owned by the operator (see plan's deferred-verification notes).
 */

/** The schema version this build can import. Bump only on a breaking change. */
const val BACKUP_VERSION = 1

/** A single exported message. */
data class BackupMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
)

/** The full on-disk backup payload. */
data class BackupData(
    val version: Int,
    val messages: List<BackupMessage>,
    val settings: Map<String, String>,
    val blocklist: List<String>,
    val starred: List<String>,
)