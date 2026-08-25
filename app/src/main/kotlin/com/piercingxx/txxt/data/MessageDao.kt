package com.piercingxx.txxt.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MessageEntity].
 *
 * Exposes suspend/Flow CRUD over the `messages` table. The pure mapper (T2)
 * converts between these entities and the `core` model; this DAO deals only in
 * Room entities.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC")
    suspend fun getForConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<MessageEntity>

    /** Live view of every message — drives the launcher's conversation list. */
    @Query("SELECT * FROM messages")
    fun observeAll(): Flow<List<MessageEntity>>

    /**
     * Marks a conversation's incoming messages read (opening its thread reads
     * them). The `isRead = 0` clause makes the write a no-op — and stops the
     * Room invalidation cycle — once nothing is unread.
     */
    @Query(
        "UPDATE messages SET isRead = 1 " +
            "WHERE conversationId = :conversationId AND direction = 'INCOMING' AND isRead = 0"
    )
    suspend fun markConversationRead(conversationId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Marks an outgoing message transmitted, clearing its pending-send state. */
    @Query("UPDATE messages SET sent = 1 WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)
}