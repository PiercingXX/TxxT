package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.ConversationList
import com.piercingxx.txxt.core.ConversationSortOrder
import com.piercingxx.txxt.data.ConversationDao
import com.piercingxx.txxt.data.Mappers.toConversation
import com.piercingxx.txxt.data.Mappers.toMessage
import com.piercingxx.txxt.data.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Loads the launcher's conversation list as a [Flow] of pure `core`
 * [Conversation]s, live from Room.
 *
 * The [ThreadMessageLoader] precedent, one level up: it owns the
 * observe-everything → group → map → sort pipeline so a plain JVM unit test
 * can drive it over fake DAOs without a device, and [MainActivity] only
 * collects the result.
 *
 * The pipeline:
 *  - combines the conversations table with the messages table, so a new
 *    inbound message re-emits the list (snippet, ordering, and unread badge
 *    stay live);
 *  - drops archived conversations — archiving hides a thread from the main
 *    list, it does not delete it;
 *  - attaches each conversation's messages in chronological order (the model
 *    derives its own latest-message and unread-count views from them);
 *  - orders the result through [ConversationList.sorted] with the persisted
 *    pinned flags: pinned first, then newest activity first.
 */
class ConversationListLoader(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    /** Emits the sorted, archive-filtered conversation list, live. */
    fun conversations(): Flow<List<Conversation>> =
        combine(
            conversationDao.observeAll(),
            messageDao.observeAll(),
        ) { conversationEntities, messageEntities ->
            val byConversation = messageEntities.groupBy { it.conversationId }
            val visible = conversationEntities.filterNot { it.isArchived }
            val pinnedIds = visible.filter { it.isPinned }.map { it.id }.toSet()
            val conversations = visible.map { entity ->
                entity.toConversation(
                    byConversation[entity.id]
                        .orEmpty()
                        .map { it.toMessage() }
                        .sortedBy { it.timestampMillis }
                )
            }
            ConversationList.sorted(
                conversations = conversations,
                order = ConversationSortOrder.PINNED_FIRST,
                pinnedIds = pinnedIds,
            )
        }
}
