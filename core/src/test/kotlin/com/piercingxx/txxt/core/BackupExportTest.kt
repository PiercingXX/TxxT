package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Verifies the backup model's version gate, range gate and round-trip/no-op
 * properties (WS5). Feeds synthetic but structurally valid payloads constructed
 * in Kotlin: a supported-version payload, a newer-version payload, and
 * out-of-range value payloads.
 */
class BackupExportTest {

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
    fun `round trip preserves the model`() {
        val data = sampleData()
        val json = BackupSerializer.serialize(data)
        val back = BackupSerializer.deserialize(json)
        assertEquals(data, back)
    }

    @Test
    fun `re-import of the same payload is a no-op`() {
        val json = BackupSerializer.serialize(sampleData())
        val first = BackupSerializer.deserialize(json)
        val second = BackupSerializer.deserialize(json)
        assertEquals(first, second)
    }

    @Test
    fun `supported version imports`() {
        val data = BackupSerializer.deserialize(BackupSerializer.serialize(sampleData()))
        assertEquals(BACKUP_VERSION, data.version)
    }

    @Test
    fun `newer version is rejected`() {
        val newer = sampleData().copy(version = BACKUP_VERSION + 1)
        val json = BackupSerializer.serialize(newer)
        assertThrows(BackupException::class.java) { BackupSerializer.deserialize(json) }
    }

    @Test
    fun `missing version is rejected`() {
        val json = """{"messages":[],"settings":{},"blocklist":[],"starred":[]}"""
        assertThrows(BackupException::class.java) { BackupSerializer.deserialize(json) }
    }

    @Test
    fun `negative message date is rejected (range gate)`() {
        val json = """{"version":1,"messages":[{"id":1,"threadId":10,"address":"+1","body":"x","date":-1}],"settings":{},"blocklist":[],"starred":[]}"""
        assertThrows(BackupException::class.java) { BackupSerializer.deserialize(json) }
    }

    @Test
    fun `negative message id is rejected (range gate)`() {
        val json = """{"version":1,"messages":[{"id":-5,"threadId":10,"address":"+1","body":"x","date":100}],"settings":{},"blocklist":[],"starred":[]}"""
        assertThrows(BackupException::class.java) { BackupSerializer.deserialize(json) }
    }

    @Test
    fun `negative thread id is rejected (range gate)`() {
        val json = """{"version":1,"messages":[{"id":1,"threadId":-10,"address":"+1","body":"x","date":100}],"settings":{},"blocklist":[],"starred":[]}"""
        assertThrows(BackupException::class.java) { BackupSerializer.deserialize(json) }
    }

    @Test
    fun `escaped characters round trip`() {
        val data = sampleData().copy(
            messages = listOf(
                BackupMessage(id = 7L, threadId = 1L, address = "a\"b\\c", body = "line1\nline2\ttab", date = 1L),
            ),
        )
        val json = BackupSerializer.serialize(data)
        assertEquals(data, BackupSerializer.deserialize(json))
    }
}