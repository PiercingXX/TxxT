package com.piercingxx.txxt.service

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent
import com.piercingxx.txxt.data.MessageDao
import com.piercingxx.txxt.data.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MmsRetrieveTest {

    private class FakeMessageDao : MessageDao {
        val rows = LinkedHashMap<Long, MessageEntity>()
        override suspend fun upsert(message: MessageEntity) { rows[message.id] = message }
        override suspend fun upsertAll(messages: List<MessageEntity>) = messages.forEach { upsert(it) }
        override suspend fun update(message: MessageEntity) { rows[message.id] = message }
        override suspend fun delete(message: MessageEntity) { rows.remove(message.id) }
        override suspend fun getById(id: Long): MessageEntity? = rows[id]
        override suspend fun getForConversation(conversationId: Long): List<MessageEntity> =
            rows.values.filter { it.conversationId == conversationId }
        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(rows.values.filter { it.conversationId == conversationId })
        override suspend fun getAll(): List<MessageEntity> = rows.values.toList()
        override fun observeAll(): Flow<List<MessageEntity>> = flowOf(rows.values.toList())
        override suspend fun markConversationRead(conversationId: Long) {}
        override suspend fun deleteById(id: Long) { rows.remove(id) }
        override suspend fun markSent(id: Long) {}
        override suspend fun deleteForConversation(conversationId: Long) {}
    }

    private fun pendingMms(id: Long = 1L) = Message(
        id = id,
        conversationId = 1L,
        direction = MessageDirection.INCOMING,
        transport = MessageTransport.MMS,
        body = "",
        timestampMillis = 1L,
        senderAddress = "+15551234567",
        contentLocation = "http://mmsc.example/id",
    )

    @Test
    fun `needsRetrieve is true for inbound MMS with a location and no photo on disk`() {
        assertTrue(MmsRetrieve.needsRetrieve(pendingMms()))
        assertTrue(
            MmsRetrieve.needsRetrieve(
                pendingMms().copy(body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER),
            ),
        )
        assertFalse(MmsRetrieve.needsRetrieve(pendingMms().copy(contentLocation = null)))
        assertFalse(MmsRetrieve.needsRetrieve(pendingMms().copy(mediaPath = "/tmp/1.jpg")))
        assertFalse(
            MmsRetrieve.needsRetrieve(
                pendingMms().copy(transport = MessageTransport.SMS, contentLocation = null),
            ),
        )
    }

    @Test
    fun `applyPdu writes the retrieved body and clears the location`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 1L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = "",
                timestampMillis = 1L,
                senderAddress = "+15551234567",
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "text/plain\u0000retrieved caption\u0000".toByteArray()
        assertTrue(MmsRetrieve.applyPdu(dao, 1L, pdu))
        assertEquals("retrieved caption", dao.rows.getValue(1L).body)
        assertNull(dao.rows.getValue(1L).contentLocation)
    }

    @Test
    fun `applyPdu deletes an audio-only retrieve`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 2L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = "",
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "audio/amr\u0000".toByteArray()
        assertTrue(MmsRetrieve.applyPdu(dao, 2L, pdu))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `applyPdu stores a photo as a collapsed Photo marker`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 4L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0xFF.toByte(), 0xD9.toByte())
        val pdu = "image/jpeg".toByteArray() + byteArrayOf(0x00) + jpeg
        var saved: Pair<ByteArray, String>? = null
        assertTrue(
            MmsRetrieve.applyPdu(dao, 4L, pdu) { bytes, mime ->
                saved = bytes to mime
                "/tmp/4.jpg"
            },
        )
        assertEquals(MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER, dao.rows.getValue(4L).body)
        assertEquals("/tmp/4.jpg", dao.rows.getValue(4L).mediaPath)
        assertNull(dao.rows.getValue(4L).contentLocation)
        assertEquals("image/jpeg", saved?.second)
    }

    @Test
    fun `applyPdu keeps a Photo row when image bytes cannot be saved`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 5L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "image/jpeg\u0000".toByteArray()
        assertFalse(MmsRetrieve.applyPdu(dao, 5L, pdu) { _, _ -> null })
        assertEquals(MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER, dao.rows.getValue(5L).body)
        assertEquals("http://mmsc.example/id", dao.rows.getValue(5L).contentLocation)
    }

    @Test
    fun `applyPdu keeps a caption alongside a saved photo`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 6L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0xFF.toByte(), 0xD9.toByte())
        val pdu = "text/plain\u0000hi there\u0000".toByteArray() + jpeg
        assertTrue(
            MmsRetrieve.applyPdu(dao, 6L, pdu) { _, _ -> "/tmp/6.jpg" },
        )
        assertEquals("hi there", dao.rows.getValue(6L).body)
        assertEquals("/tmp/6.jpg", dao.rows.getValue(6L).mediaPath)
        assertNull(dao.rows.getValue(6L).contentLocation)
    }

    @Test
    fun `the retrieve path grants the MmsService package and reads a written dest file`() {
        val retrieve = sourceText("service/MmsRetrieve.kt")
        val grants = sourceText("service/MmsUriGrants.kt")
        assertTrue(
            "telephony writes through com.android.mms.service, which must be granted",
            grants.contains("com.android.mms.service"),
        )
        assertTrue(
            "the MmsService action must be queried so OEM package names are granted too",
            grants.contains("android.service.mms.MmsService"),
        )
        assertTrue(
            "a dest file with bytes is a successful retrieve even when the callback fails",
            retrieve.contains("file.length() > 0L"),
        )
        assertTrue(
            "auto-fetch and tap must share retrieveAndStore so they do not double-GET",
            retrieve.contains("fun retrieveAndStore"),
        )
        assertTrue(
            "GrapheneOS MmsService rejects FileProvider dests; download must use content://mms",
            retrieve.contains("Telephony.Mms.Inbox.CONTENT_URI") &&
                retrieve.contains("content://mms/"),
        )
        assertTrue(
            "MmsService may write sibling parts; those bytes must be read before the stub is deleted",
            retrieve.contains("fun readTelephonyParts"),
        )
        assertTrue(
            "MmsService persistIfRequired stores a new inbox row; harvest those image parts",
            retrieve.contains("fun readRecentInboxImages"),
        )
        assertTrue(
            "Verizon 3GP picture messages must become a still JPEG",
            retrieve.contains("fun stillFromVideo"),
        )
        val importer = sourceText("service/TelephonyInboxImport.kt")
        assertTrue(
            "already-persisted telephony inbox photos must be copied into TxxT",
            importer.contains("fun importPending"),
        )
    }

    @Test
    fun `pickImagePdu prefers a chunk that actually contains a JPEG`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte())
        val smil = "application/smil\u0000".toByteArray()
        val picked = MmsContentFetcher.pickImagePdu(listOf(smil, jpeg))
        assertArrayEquals(jpeg, picked)
        val video = "video/3gpp".toByteArray() + ByteArray(24)
        val chosen = TelephonyInboxImport.pickPart(
            listOf("application/smil" to smil, "video/3gpp" to video),
        )
        assertEquals("video/3gpp", chosen?.first)
        assertArrayEquals(video, chosen?.second)
    }

    @Test
    fun `retrieve service is a foreground service so the download survives the broadcast`() {
        val service = sourceText("service/MmsRetrieveService.kt")
        assertTrue(
            "enqueue must startForegroundService on O+",
            service.contains("startForegroundService"),
        )
        assertTrue(
            "onStartCommand must call startForeground before the coroutine",
            service.contains("startRetrieveForeground"),
        )
    }

    @Test
    fun `applyPdu keeps a pending Photo row when the retrieve has no image yet`() = runBlocking {
        val dao = FakeMessageDao()
        dao.upsert(
            MessageEntity(
                id = 3L,
                conversationId = 1L,
                direction = MessageDirection.INCOMING.name,
                transport = MessageTransport.MMS.name,
                body = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
                timestampMillis = 1L,
                contentLocation = "http://mmsc.example/id",
            ),
        )
        val pdu = "application/smil\u0000".toByteArray()
        assertFalse(MmsRetrieve.applyPdu(dao, 3L, pdu))
        assertEquals(MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER, dao.rows.getValue(3L).body)
        assertEquals("http://mmsc.example/id", dao.rows.getValue(3L).contentLocation)
    }

    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}
