package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.ConversationFlags
import com.piercingxx.txxt.core.ConversationSortOrder
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport

/**
 * Pure, side-effect-free mappers between the `core` model and the Room storage
 * entities.
 *
 * The core model is JVM-pure (zero `android.*` imports) and the entities are
 * plain data classes, so every mapper here is unit-testable without a device.
 * The only non-trivial conversions are the ones Room cannot persist directly:
 * enums are stored as their `core` enum names (see the entity docstrings), and
 * a conversation's participant addresses are stored as a delimiter-joined
 * string because Room cannot persist a `Set<String>` without a TypeConverter.
 */
object Mappers {

    /** Delimiter joining participant addresses in a [ConversationEntity]. */
    private const val ADDRESS_DELIMITER = "\u0001"

    // ---- Message ----

    /** Converts a stored [MessageEntity] back into a pure [Message]. */
    fun MessageEntity.toMessage(): Message = Message(
        id = id,
        conversationId = conversationId,
        direction = MessageDirection.valueOf(direction),
        transport = MessageTransport.valueOf(transport),
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = senderAddress,
        isRead = isRead,
    )

    /** Converts a pure [Message] into a storable [MessageEntity]. */
    fun Message.toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        direction = direction.name,
        transport = transport.name,
        body = body,
        timestampMillis = timestampMillis,
        senderAddress = senderAddress,
        isRead = isRead,
    )

    // ---- Conversation (messages are stored separately, in the messages table) ----

    /** Converts a stored [ConversationEntity] into a pure [Conversation] with no messages. */
    fun ConversationEntity.toConversation(): Conversation = Conversation(
        id = id,
        participantAddresses = decodeAddresses(participantAddresses),
    )

    /** Converts a stored [ConversationEntity] plus its loaded messages into a pure [Conversation]. */
    fun ConversationEntity.toConversation(messages: List<Message>): Conversation =
        toConversation().copy(messages = messages)

    /** Converts a pure [Conversation] into a storable [ConversationEntity]. */
    fun Conversation.toEntity(
        flags: ConversationFlags = ConversationFlags(),
        isStarred: Boolean = false,
    ): ConversationEntity = ConversationEntity(
        id = id,
        participantAddresses = encodeAddresses(participantAddresses),
        isStarred = isStarred,
        isPinned = flags.isPinned,
        isArchived = flags.isArchived,
        sortOrder = flags.sortOrder.name,
    )

    // ---- ConversationFlags ----

    /** Reads the persisted flag fields of an entity back into a pure [ConversationFlags]. */
    fun ConversationEntity.toFlags(): ConversationFlags = ConversationFlags(
        isPinned = isPinned,
        isArchived = isArchived,
        sortOrder = ConversationSortOrder.valueOf(sortOrder),
    )

    /** Applies pure [ConversationFlags] onto an entity's flag fields. */
    fun ConversationFlags.toEntityPart(entity: ConversationEntity): ConversationEntity =
        entity.copy(
            isPinned = isPinned,
            isArchived = isArchived,
            sortOrder = sortOrder.name,
        )

    private fun encodeAddresses(addresses: Set<String>): String =
        addresses.joinToString(ADDRESS_DELIMITER)

    private fun decodeAddresses(joined: String): Set<String> =
        if (joined.isEmpty()) emptySet() else joined.split(ADDRESS_DELIMITER).toSet()
}