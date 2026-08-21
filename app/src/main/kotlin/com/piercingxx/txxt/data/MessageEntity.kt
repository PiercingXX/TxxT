package com.piercingxx.txxt.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single SMS/MMS message.
 *
 * Mirrors the fields of the pure `core/Message` model (id, conversationId,
 * direction, transport, body, timestampMillis, senderAddress, isRead). The
 * `direction` and `transport` enums are stored as their `core` enum names
 * because Room cannot persist enums without a TypeConverter; the pure mapper
 * (T2) converts between these strings and the `core` enums.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val conversationId: Long,
    /** Direction of the message, as a `core` MessageDirection enum name. */
    val direction: String,
    /** Transport the message travels over, as a `core` MessageTransport enum name. */
    val transport: String,
    val body: String,
    /** Epoch milliseconds. */
    val timestampMillis: Long,
    /** Address of the remote participant; null for an outgoing message. */
    val senderAddress: String? = null,
    /** Whether the local user has read this message. */
    val isRead: Boolean = false,
)