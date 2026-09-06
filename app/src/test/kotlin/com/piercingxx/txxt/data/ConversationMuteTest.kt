package com.piercingxx.txxt.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMuteTest {

    private class FakeConversationDao : ConversationDao {
        val rows = LinkedHashMap<Long, ConversationEntity>()
        override suspend fun upsert(conversation: ConversationEntity) {
            rows[conversation.id] = conversation
        }
        override suspend fun upsertAll(conversations: List<ConversationEntity>) =
            conversations.forEach { upsert(it) }
        override suspend fun update(conversation: ConversationEntity) {
            rows[conversation.id] = conversation
        }
        override suspend fun delete(conversation: ConversationEntity) = Unit
        override suspend fun getById(id: Long): ConversationEntity? = rows[id]
        override suspend fun getByParticipants(joined: String): ConversationEntity? = null
        override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(null)
        override fun observeAll(): Flow<List<ConversationEntity>> = flowOf(rows.values.toList())
        override suspend fun getAll(): List<ConversationEntity> = rows.values.toList()
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
    }

    @Test
    fun `forever mute stays on`() {
        val entity = ConversationEntity(
            id = 1L,
            participantAddresses = "15551234567",
            isMuted = true,
        )
        assertTrue(ConversationMute.isActive(entity, nowMillis = 1_000L))
        assertTrue(ConversationMute.isActive(entity, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun `mute-until is off after the wall time`() {
        val entity = ConversationEntity(
            id = 1L,
            participantAddresses = "15551234567",
            mutedUntilMillis = 2_000L,
        )
        assertTrue(ConversationMute.isActive(entity, nowMillis = 1_000L))
        assertFalse(ConversationMute.isActive(entity, nowMillis = 2_000L))
        assertFalse(ConversationMute.isActive(entity, nowMillis = 3_000L))
    }

    @Test
    fun `isMuted matches the address and respects expiry`() = runBlocking {
        val dao = FakeConversationDao()
        dao.upsert(
            ConversationEntity(
                id = 1L,
                participantAddresses = "15551234567",
                mutedUntilMillis = 2_000L,
            ),
        )
        assertTrue(ConversationMute.isMuted(dao, "+15551234567", nowMillis = 1_000L))
        assertFalse(ConversationMute.isMuted(dao, "+15551234567", nowMillis = 3_000L))
        assertFalse(ConversationMute.isMuted(dao, "+15559990000", nowMillis = 1_000L))
    }
}
