package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.BackupMessage

/**
 * Builds the user-visible conversation export (todo.md T1).
 *
 * The on-disk backup format ([core.BackupData]) carries messages as
 * [BackupMessage]s with a `threadId`; the Room schema stores messages under a
 * `conversationId` foreign key. [buildBackup] maps the live entities onto that
 * format — a message's `conversationId` becomes its `threadId`, its id is kept
 * verbatim (so a restore through [RestoreService]'s REPLACE-upsert overwrites
 * the same primary key instead of duplicating), and its address is the sender's
 * when present, else the conversation's remote participant (an outgoing message
 * in a 1:1 thread has no sender; the thread's other party is the address a
 * restore should key the conversation on).
 *
 * **MMS photos are honest, not silent.** The backup format has no photo field,
 * so a message's photo bytes are not exported. Instead the exporter counts every
 * message that carries a [MessageEntity.mediaPath] and returns that count in
 * [Export.photoCount]; the calling UI surfaces "N photos not in this JSON" so a
 * user is never left believing their photos travelled with the export
 * (todo.md T1 "include MMS photo references honestly — export the files or write
 * 'photos not in this JSON'"; the JSON path is chosen, and said so in the UI).
 *
 * Pure Kotlin with zero `android.*` imports, mirroring [RestoreService], so the
 * mapping and the photo count are JVM-testable without a device.
 */
object ConversationExporter {

    /** The export payload plus the honest photo count the UI must say out loud. */
    data class Export(
        val backup: BackupData,
        /** Messages whose photo was NOT included in the JSON (their bytes live only on-device). */
        val photoCount: Int,
    )

    /**
     * Maps the live conversation/message entities onto the backup payload.
     *
     * [settings], [blocklist] and [starred] are the string-map shapes the backup
     * format expects (the settings-screen shapes from [SettingsBackup] /
     * [SettingsBlockingStore]); the caller reads them from the same stores the
     * settings backup uses, so a message export carries the whole archive, not
     * just the threads.
     */
    fun buildBackup(
        conversations: List<ConversationEntity>,
        messages: List<MessageEntity>,
        settings: Map<String, String>,
        blocklist: List<String>,
        starred: List<String>,
    ): Export {
        val byConversation = conversations.associateBy { it.id }
        var photoCount = 0
        val backupMessages = messages.map { message ->
            if (!message.mediaPath.isNullOrBlank()) photoCount++
            val conversation = byConversation[message.conversationId]
            val address = message.senderAddress
                ?: conversation?.participantAddresses
                    ?.split(ADDRESS_DELIMITER)
                    ?.firstOrNull { it.isNotBlank() }
                ?: ""
            BackupMessage(
                id = message.id,
                threadId = message.conversationId,
                address = address,
                body = message.body,
                date = message.timestampMillis,
            )
        }
        return Export(
            backup = BackupData(
                version = BACKUP_VERSION,
                messages = backupMessages,
                settings = settings,
                blocklist = blocklist,
                starred = starred,
            ),
            photoCount = photoCount,
        )
    }

    /** Delimiter joining participant addresses in the conversations table. */
    private const val ADDRESS_DELIMITER = "\u0001"
}