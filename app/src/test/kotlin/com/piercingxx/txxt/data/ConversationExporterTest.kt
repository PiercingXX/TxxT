package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the user-visible conversation export (todo.md T1):
 * [ConversationExporter.buildBackup] maps the live conversation/message entities
 * onto the backup format [BackupData] — a message's `conversationId` becomes its
 * `threadId`, its id is preserved verbatim (so the [RestoreService] REPLACE
 * upsert overwrites the same primary key instead of duplicating), and its
 * address is the sender's when present, else the conversation's remote
 * participant. It also counts MMS photos honestly: every message carrying a
 * [MessageEntity.mediaPath] is counted in [ConversationExporter.Export.photoCount]
 * because the backup format has no photo field — the UI must say "N photos not
 * in this JSON" rather than leave the user assuming photos travelled with the
 * export. The MainActivity wire-in is locked by a source-reading assertion (the
 * established `MainActivityWiringTest` pattern).
 */
class ConversationExporterTest {

    private fun conversation(id: Long, participant: String = "+15551234567") =
        ConversationEntity(id = id, participantAddresses = participant)

    private fun message(
        id: Long,
        conversationId: Long,
        body: String = "hello",
        senderAddress: String? = "+15551234567",
        mediaPath: String? = null,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        direction = "INCOMING",
        transport = "SMS",
        body = body,
        timestampMillis = 1700000000000L + id,
        senderAddress = senderAddress,
        mediaPath = mediaPath,
    )

    // ---- mapping is faithful ----

    @Test
    fun `messages map conversationId to threadId and preserve their ids`() {
        val export = ConversationExporter.buildBackup(
            conversations = listOf(conversation(10L), conversation(20L)),
            messages = listOf(
                message(1L, 10L),
                message(2L, 10L, body = "world"),
                message(3L, 20L, senderAddress = "+15559998888"),
            ),
            settings = mapOf("quietHoursStart" to "22:00"),
            blocklist = listOf("spam"),
            starred = listOf("+15551234567"),
        )
        assertEquals(BACKUP_VERSION, export.backup.version)
        assertEquals(3, export.backup.messages.size)
        // id preserved verbatim, conversationId → threadId.
        assertEquals(1L, export.backup.messages[0].id)
        assertEquals(10L, export.backup.messages[0].threadId)
        assertEquals(3L, export.backup.messages[2].id)
        assertEquals(20L, export.backup.messages[2].threadId)
        // settings/blocklist/starred pass through.
        assertEquals(mapOf("quietHoursStart" to "22:00"), export.backup.settings)
        assertEquals(listOf("spam"), export.backup.blocklist)
        assertEquals(listOf("+15551234567"), export.backup.starred)
    }

    @Test
    fun `outgoing message falls back to the conversation participant address`() {
        val export = ConversationExporter.buildBackup(
            conversations = listOf(conversation(10L, participant = "+15551234567")),
            messages = listOf(
                message(1L, 10L, senderAddress = null, body = "sent by me"),
            ),
            settings = emptyMap(),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        // The sender is null (outgoing); the thread's other party is the address.
        assertEquals("+15551234567", export.backup.messages[0].address)
    }

    // ---- MMS photos are counted honestly ----

    @Test
    fun `messages with a media path are counted as photos not in the json`() {
        val export = ConversationExporter.buildBackup(
            conversations = listOf(conversation(10L)),
            messages = listOf(
                message(1L, 10L, mediaPath = "/data/txxt/photo1.jpg"),
                message(2L, 10L, mediaPath = "/data/txxt/photo2.jpg"),
                message(3L, 10L, body = "plain text"),
            ),
            settings = emptyMap(),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        assertEquals(2, export.photoCount)
        // The photo messages' bodies are still exported; only the bytes are not.
        assertEquals(3, export.backup.messages.size)
    }

    @Test
    fun `no media path means zero photos left behind`() {
        val export = ConversationExporter.buildBackup(
            conversations = listOf(conversation(10L)),
            messages = listOf(message(1L, 10L, body = "text only")),
            settings = emptyMap(),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        assertEquals(0, export.photoCount)
    }

    // ---- the export round-trips through the validating serializer ----

    @Test
    fun `exported payload round-trips through BackupJson`() {
        val export = ConversationExporter.buildBackup(
            conversations = listOf(conversation(10L)),
            messages = listOf(message(1L, 10L), message(2L, 10L, body = "world")),
            settings = mapOf("quietHoursStart" to "22:00"),
            blocklist = listOf("spam"),
            starred = listOf("+15551234567"),
        )
        val back = BackupJson.deserialize(BackupJson.serialize(export.backup))
        assertEquals(export.backup, back)
    }

    // ---- wire-in: the running launcher reaches the exporter and the restore ----

    @Test
    fun `MainActivity reaches the exporter and the idempotent restore`() {
        val main = sourceText("MainActivity.kt")
        assertTrue(
            "MainActivity must build the export through ConversationExporter",
            main.contains("ConversationExporter.buildBackup"),
        )
        assertTrue(
            "MainActivity must serialize through BackupJson",
            main.contains("BackupJson.serialize"),
        )
        assertTrue(
            "MainActivity must restore through RoomRestoreService (idempotent REPLACE)",
            main.contains("RoomRestoreService(database).restore"),
        )
        assertTrue(
            "MainActivity must open the SAF create-document picker for export",
            main.contains("ACTION_CREATE_DOCUMENT"),
        )
        assertTrue(
            "MainActivity must open the SAF open-document picker for import",
            main.contains("ACTION_OPEN_DOCUMENT"),
        )
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}