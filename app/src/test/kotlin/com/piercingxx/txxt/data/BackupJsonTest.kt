package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.BackupMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Gson-backed backup adapter (WS6 T3): a [BackupData] serialized
 * with [BackupJson] and deserialized back must reproduce the
 * messages/settings/blocklist/starred sections exactly. A JVM test that runs in
 * the app module, proving the data layer is "wired to Gson for the backup JSON".
 */
class BackupJsonTest {

    private fun sampleData() = BackupData(
        version = BACKUP_VERSION,
        messages = listOf(
            BackupMessage(id = 1L, threadId = 10L, address = "+15551234567", body = "hello", date = 1700000000000L),
            BackupMessage(id = 2L, threadId = 10L, address = "+15551234567", body = "world", date = 1700000001000L),
        ),
        settings = mapOf("quietHoursStart" to "22:00", "quietHoursEnd" to "07:00"),
        blocklist = listOf("+15559998888", "spam"),
        starred = listOf("+15551234567"),
    )

    @Test
    fun `round trip reproduces the full backup payload`() {
        val data = sampleData()
        val json = BackupJson.serialize(data)
        val back = BackupJson.deserialize(json)
        assertEquals(data, back)
    }

    @Test
    fun `round trip preserves each section exactly`() {
        val data = sampleData()
        val back = BackupJson.deserialize(BackupJson.serialize(data))
        assertEquals(data.messages, back.messages)
        assertEquals(data.settings, back.settings)
        assertEquals(data.blocklist, back.blocklist)
        assertEquals(data.starred, back.starred)
    }

    @Test
    fun `empty sections round trip`() {
        val data = BackupData(
            version = BACKUP_VERSION,
            messages = emptyList(),
            settings = emptyMap(),
            blocklist = emptyList(),
            starred = emptyList(),
        )
        val back = BackupJson.deserialize(BackupJson.serialize(data))
        assertEquals(data, back)
    }

    @Test
    fun `escaped characters round trip`() {
        val data = sampleData().copy(
            messages = listOf(
                BackupMessage(id = 7L, threadId = 1L, address = "a\"b\\c", body = "line1\nline2\ttab", date = 1L),
            ),
            settings = mapOf("note" to "quote \" and slash \\"),
        )
        val back = BackupJson.deserialize(BackupJson.serialize(data))
        assertEquals(data, back)
    }
}