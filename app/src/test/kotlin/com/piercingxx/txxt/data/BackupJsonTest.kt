package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.BACKUP_VERSION
import com.piercingxx.txxt.core.BackupData
import com.piercingxx.txxt.core.BackupException
import com.piercingxx.txxt.core.BackupMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the backup adapter (WS6 T3): [BackupJson] delegates to the
 * validating core [com.piercingxx.txxt.core.BackupSerializer], so a payload
 * serialized with [BackupJson] and deserialized back must reproduce the
 * messages/settings/blocklist/starred sections exactly, while payloads that
 * fail the serializer's version gate, range gate or JSON syntax are rejected
 * with [BackupException] instead of being leniently defaulted.
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

    // ---- round trip ----

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

    @Test
    fun `serialize emits the canonical validating format shape`() {
        val json = BackupJson.serialize(sampleData())
        assertTrue(json.contains("\"version\":$BACKUP_VERSION"))
        assertTrue(json.contains("\"messages\":[{\"id\":1,\"threadId\":10"))
        assertTrue(json.contains("\"settings\":{\"quietHoursStart\":\"22:00\""))
        assertTrue(json.contains("\"blocklist\":[\"+15559998888\",\"spam\"]"))
        assertTrue(json.contains("\"starred\":[\"+15551234567\"]"))
    }

    // ---- version gate ----

    @Test
    fun `wrong version is rejected`() {
        val json = BackupJson.serialize(sampleData()).replace("\"version\":$BACKUP_VERSION", "\"version\":${BACKUP_VERSION + 1}")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("newer backup version must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("not supported"))
        }
    }

    @Test
    fun `missing version is rejected`() {
        val json = BackupJson.serialize(sampleData()).replace("\"version\":$BACKUP_VERSION,", "")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("backup without a version must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("version"))
        }
    }

    // ---- range gate ----

    @Test
    fun `negative message id is rejected`() {
        val json = BackupJson.serialize(sampleData()).replaceFirst("\"id\":1,", "\"id\":-1,")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("negative message id must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("message.id"))
        }
    }

    @Test
    fun `negative thread id is rejected`() {
        val json = BackupJson.serialize(sampleData()).replaceFirst("\"threadId\":10,", "\"threadId\":-10,")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("negative thread id must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("message.threadId"))
        }
    }

    @Test
    fun `negative date is rejected`() {
        val json = BackupJson.serialize(sampleData()).replaceFirst("\"date\":1700000000000", "\"date\":-1700000000000")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("negative date must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("message.date"))
        }
    }

    @Test
    fun `non-numeric id is rejected`() {
        val json = BackupJson.serialize(sampleData()).replaceFirst("\"id\":1,", "\"id\":\"1\",")
        try {
            BackupJson.deserialize(json)
            throw AssertionError("string-typed id must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("message.id"))
        }
    }

    // ---- strict parsing ----

    @Test
    fun `malformed json is rejected`() {
        val malformed = "{not json"
        try {
            BackupJson.deserialize(malformed)
            throw AssertionError("malformed JSON must be refused")
        } catch (expected: BackupException) {
            // rejected on syntax
        }
    }

    @Test
    fun `trailing garbage after the payload is rejected`() {
        val json = BackupJson.serialize(sampleData()) + " trailing"
        try {
            BackupJson.deserialize(json)
            throw AssertionError("trailing characters must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("trailing"))
        }
    }

    @Test
    fun `non-object root is rejected`() {
        try {
            BackupJson.deserialize("[1,2,3]")
            throw AssertionError("array root must be refused")
        } catch (expected: BackupException) {
            assertTrue(expected.message!!.contains("root"))
        }
    }

    @Test
    fun `rejections are IllegalArgumentException subtypes`() {
        try {
            BackupJson.deserialize("{}")
            throw AssertionError("payload without sections must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected is BackupException)
        }
    }
}
