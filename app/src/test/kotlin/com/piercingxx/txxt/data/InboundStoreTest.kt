package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.data.Mappers.toMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour-verifies [InboundStore] over hand-rolled fake DAOs (the DAOs are
 * interfaces, so the used methods are directly implementable — no Robolectric,
 * no Room). Proves the find-or-create contract (existing id returned without a
 * second insert; wall-clock id and normalized single-participant address on
 * create — different formats of one number converge on one thread while the
 * message rows keep their RAW delivered addresses), the inbound SMS row shape
 * (INCOMING / unread / sent with every field carried through), and the MMS
 * metadata-only row shape (MMS transport, empty body).
 */
class InboundStoreTest {

    /** REPLACE-by-primary-key conversation DAO fake (Room upsert semantics). */
    private class FakeConversationDao : ConversationDao {
        val rows = LinkedHashMap<Long, ConversationEntity>()
        var upsertCount = 0

        override suspend fun upsert(conversation: ConversationEntity) {
            upsertCount += 1
            rows[conversation.id] = conversation
        }

        override suspend fun upsertAll(conversations: List<ConversationEntity>) =
            conversations.forEach { upsert(it) }

        override suspend fun update(conversation: ConversationEntity) {
            rows[conversation.id] = conversation
        }

        override suspend fun delete(conversation: ConversationEntity) {
            rows.remove(conversation.id)
        }

        override suspend fun getById(id: Long): ConversationEntity? = rows[id]

        override suspend fun getByParticipants(joined: String): ConversationEntity? =
            rows.values.firstOrNull { it.participantAddresses == joined }

        override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(rows[id])

        override fun observeAll(): Flow<List<ConversationEntity>> = flowOf(rows.values.toList())

        override suspend fun getAll(): List<ConversationEntity> = rows.values.toList()

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
    }

    /** REPLACE-by-primary-key message DAO fake (Room upsert semantics). */
    private class FakeMessageDao : MessageDao {
        val rows = LinkedHashMap<Long, MessageEntity>()

        override suspend fun upsert(message: MessageEntity) {
            rows[message.id] = message
        }

        override suspend fun upsertAll(messages: List<MessageEntity>) =
            messages.forEach { upsert(it) }

        override suspend fun update(message: MessageEntity) {
            rows[message.id] = message
        }

        override suspend fun delete(message: MessageEntity) {
            rows.remove(message.id)
        }

        override suspend fun getById(id: Long): MessageEntity? = rows[id]

        override suspend fun getForConversation(conversationId: Long): List<MessageEntity> =
            rows.values.filter { it.conversationId == conversationId }
                .sortedBy { it.timestampMillis }

        override fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>> =
            flowOf(
                rows.values.filter { it.conversationId == conversationId }
                    .sortedBy { it.timestampMillis },
            )

        override suspend fun getAll(): List<MessageEntity> = rows.values.toList()

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override suspend fun markSent(id: Long) {
            rows[id]?.let { rows[id] = it.copy(sent = true) }
        }

        override suspend fun deleteForConversation(conversationId: Long) {
            rows.values.filter { it.conversationId == conversationId }.forEach { rows.remove(it.id) }
        }
    }

    // ---- findOrCreateConversation ----

    @Test
    fun `an existing participant thread is found without inserting again`() = runBlocking {
        val dao = FakeConversationDao().apply {
            // The store keys conversations by the NORMALIZED address.
            rows[42L] = ConversationEntity(id = 42L, participantAddresses = "15551234567")
        }

        val first = InboundStore.findOrCreateConversation(dao, "+15551234567")
        val second = InboundStore.findOrCreateConversation(dao, "+15551234567")

        assertEquals(42L, first)
        assertEquals(42L, second)
        // Hit path: zero writes, the pre-seeded row is untouched.
        assertEquals(0, dao.upsertCount)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `a miss creates a conversation keyed by the normalized address`() = runBlocking {
        val dao = FakeConversationDao()

        val before = System.currentTimeMillis()
        val id = InboundStore.findOrCreateConversation(dao, "+15551234567")
        val after = System.currentTimeMillis()

        assertTrue(id in before..after)
        val stored = dao.rows.getValue(id)
        assertEquals("15551234567", stored.participantAddresses)
    }

    @Test
    fun `a created conversation stores the single participant un-joined`() = runBlocking {
        val dao = FakeConversationDao()

        val id = InboundStore.findOrCreateConversation(dao, "+15551234567")

        // Single-participant convention: the normalized address, never a
        // delimiter join — multi-participant threads are out of this scope.
        val stored = dao.rows.getValue(id)
        assertFalse(stored.participantAddresses.contains('\u0001'))
        assertEquals(listOf("15551234567"), stored.participantAddresses.split('\u0001'))
    }

    @Test
    fun `a wall-clock id collision is bumped past instead of REPLACE-overwriting`() = runBlocking {
        // The DAOs upsert with OnConflictStrategy.REPLACE, and the messages
        // table CASCADE-deletes on conversation replace — a collided id would
        // silently destroy an existing row. The fresh-id probe must bump past.
        val now = System.currentTimeMillis()
        val dao = FakeConversationDao().apply {
            rows[now] = ConversationEntity(id = now, participantAddresses = "+15550001111")
        }

        val id = InboundStore.findOrCreateConversation(dao, "+15551234567")

        assertTrue("created id must not collide with the pre-seeded row", id != now)
        assertEquals("the pre-seeded row must survive untouched", 2, dao.rows.size)
        assertEquals("+15550001111", dao.rows.getValue(now).participantAddresses)
    }

    // ---- persistInboundSms ----

    @Test
    fun `persistInboundSms inserts an incoming unread sent row with the given fields`() =
        runBlocking {
            val conversations = FakeConversationDao()
            val messages = FakeMessageDao()
            val date = 1_700_000_000_000L

            val messageId = InboundStore.persistInboundSms(
                conversations, messages, "+15551234567", "hello there", date,
            )

            val conversationId = conversations.rows.keys.single()
            val stored = messages.rows.getValue(messageId)
            assertEquals(conversationId, stored.conversationId)
            assertEquals(MessageDirection.INCOMING.name, stored.direction)
            assertEquals(MessageTransport.SMS.name, stored.transport)
            assertEquals("hello there", stored.body)
            assertEquals(date, stored.timestampMillis)
            assertEquals("+15551234567", stored.senderAddress)
            assertEquals(false, stored.isRead)
            assertEquals(true, stored.sent)
        }

    @Test
    fun `a persisted SMS derives as unread so unread counts light up`() = runBlocking {
        val messages = FakeMessageDao()

        val messageId = InboundStore.persistInboundSms(
            FakeConversationDao(), messages, "+15551234567", "hello", 1_700_000_000_000L,
        )

        // The UnreadCount derivation reads direction + isRead off the mapped
        // core model — exactly what the thread list sees.
        assertTrue(messages.rows.getValue(messageId).toMessage().isUnread)
    }

    @Test
    fun `two SMS from one sender land on one conversation`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()

        val first = InboundStore.persistInboundSms(
            conversations, messages, "+15551234567", "one", 1_700_000_000_000L,
        )
        val second = InboundStore.persistInboundSms(
            conversations, messages, "+15551234567", "two", 1_700_000_000_001L,
        )

        assertEquals(1, conversations.rows.size)
        assertEquals(2, messages.rows.size)
        assertEquals(
            messages.rows.getValue(first).conversationId,
            messages.rows.getValue(second).conversationId,
        )
    }

    @Test
    fun `different formats of one number land on one conversation but rows keep raw addresses`() =
        runBlocking {
            val conversations = FakeConversationDao()
            val messages = FakeMessageDao()

            // An SMS arriving as "+15551234567" and a SENDTO hand-off as
            // "15551234567": one correspondent, ONE thread.
            val smsArrival = InboundStore.persistInboundSms(
                conversations, messages, "+15551234567", "carrier form", 1_700_000_000_000L,
            )
            val sendtoHandoff = InboundStore.persistInboundSms(
                conversations, messages, "15551234567", "bare-digit form", 1_700_000_000_001L,
            )

            assertEquals(1, conversations.rows.size)
            assertEquals(
                "15551234567",
                conversations.rows.values.single().participantAddresses,
            )
            assertEquals(
                messages.rows.getValue(smsArrival).conversationId,
                messages.rows.getValue(sendtoHandoff).conversationId,
            )
            // Display fidelity: each MESSAGE row keeps its RAW delivered address.
            assertEquals("+15551234567", messages.rows.getValue(smsArrival).senderAddress)
            assertEquals("15551234567", messages.rows.getValue(sendtoHandoff).senderAddress)
        }

    @Test
    fun `email-gateway addresses key by trimmed-lowercase and never digit-strip`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()

        val first = InboundStore.persistInboundSms(
            conversations, messages, " Foo@Carrier.Example.com ", "one", 1_700_000_000_000L,
        )
        InboundStore.persistInboundSms(
            conversations, messages, "foo@carrier.example.com", "two", 1_700_000_000_001L,
        )

        // One gateway identity, stored trimmed + lowercased — punctuation intact.
        assertEquals(1, conversations.rows.size)
        val key = conversations.rows.values.single().participantAddresses
        assertEquals("foo@carrier.example.com", key)
        // The raw (whitespace-carrying) form survives on its message row.
        assertEquals(
            " Foo@Carrier.Example.com ",
            messages.rows.getValue(first).senderAddress,
        )
    }

    // ---- persistInboundMmsMetadata ----

    @Test
    fun `persistInboundMmsMetadata inserts an MMS row with an empty body`() = runBlocking {
        val conversations = FakeConversationDao()
        val messages = FakeMessageDao()
        val date = 1_700_000_000_000L

        val messageId = InboundStore.persistInboundMmsMetadata(
            conversations, messages, "+15559998888", date,
        )

        val stored = messages.rows.getValue(messageId)
        assertEquals(conversations.rows.keys.single(), stored.conversationId)
        assertEquals(MessageDirection.INCOMING.name, stored.direction)
        assertEquals(MessageTransport.MMS.name, stored.transport)
        // Content is NEVER auto-downloaded (docs/PRIVACY.md §8.1): the delivery
        // path stores metadata only.
        assertEquals("", stored.body)
        assertEquals(date, stored.timestampMillis)
        assertEquals("+15559998888", stored.senderAddress)
        assertEquals(false, stored.isRead)
        assertEquals(true, stored.sent)
    }
}
