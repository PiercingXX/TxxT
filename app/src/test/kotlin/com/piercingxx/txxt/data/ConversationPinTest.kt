package com.piercingxx.txxt.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPinTest {

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
        override suspend fun delete(conversation: ConversationEntity) {
            rows.remove(conversation.id)
        }
        override suspend fun getById(id: Long): ConversationEntity? = rows[id]
        override suspend fun getByParticipants(joined: String): ConversationEntity? = null
        override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(null)
        override fun observeAll(): Flow<List<ConversationEntity>> = flowOf(rows.values.toList())
        override suspend fun getAll(): List<ConversationEntity> = rows.values.toList()
        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
        override suspend fun unarchive(id: Long) = Unit
    }

    @Test
    fun `pin two threads they stay pinned after a second read`() = runBlocking {
        val dao = FakeConversationDao()
        dao.upsert(ConversationEntity(id = 1L, participantAddresses = "a"))
        dao.upsert(ConversationEntity(id = 2L, participantAddresses = "b"))
        dao.upsert(ConversationEntity(id = 3L, participantAddresses = "c"))
        assertEquals(ConversationPin.Result.PINNED, ConversationPin.toggle(dao, 1L))
        assertEquals(ConversationPin.Result.PINNED, ConversationPin.toggle(dao, 2L))
        assertTrue(dao.getById(1L)!!.isPinned)
        assertTrue(dao.getById(2L)!!.isPinned)
        assertFalse(dao.getById(3L)!!.isPinned)
        assertEquals(ConversationPin.Result.UNPINNED, ConversationPin.toggle(dao, 1L))
        assertFalse(dao.getById(1L)!!.isPinned)
    }

    @Test
    fun `a sixth pin is refused and writes nothing`() = runBlocking {
        val dao = FakeConversationDao()
        for (id in 1L..5L) {
            dao.upsert(ConversationEntity(id = id, participantAddresses = "n$id", isPinned = true))
        }
        dao.upsert(ConversationEntity(id = 6L, participantAddresses = "n6"))
        assertEquals(ConversationPin.Result.CAP_REACHED, ConversationPin.toggle(dao, 6L))
        assertFalse(dao.getById(6L)!!.isPinned)
        assertEquals(5, dao.getAll().count { it.isPinned })
    }
}
