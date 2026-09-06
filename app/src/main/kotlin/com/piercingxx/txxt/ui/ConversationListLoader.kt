package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Conversation
import com.piercingxx.txxt.core.ConversationList
import com.piercingxx.txxt.core.ConversationSortOrder
import com.piercingxx.txxt.data.ConversationDao
import com.piercingxx.txxt.data.ConversationEntity
import com.piercingxx.txxt.data.ConversationMute
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
 *  - drops archived **and quarantined** conversations — both hide a thread
 *    from the main list without deleting it;
 *  - attaches each conversation's messages in chronological order (the model
 *    derives its own latest-message and unread-count views from them);
 *  - orders the result through [ConversationList.sorted] with the persisted
 *    pinned flags: pinned first, then newest activity first.
 */
class ConversationListLoader(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    /** Emits the sorted, archive-and-quarantine-filtered conversation list, live. */
    fun conversations(): Flow<List<Conversation>> =
        load { entity -> !entity.isArchived && !entity.isQuarantined }

    /** Emits only quarantined threads for the review surface. */
    fun quarantined(): Flow<List<Conversation>> =
        load { entity -> entity.isQuarantined }

    private fun load(
        visible: (ConversationEntity) -> Boolean,
    ): Flow<List<Conversation>> =
        combine(
            conversationDao.observeAll(),
            messageDao.observeAll(),
        ) { conversationEntities, messageEntities ->
            val byConversation = messageEntities.groupBy { it.conversationId }
            val shown = conversationEntities.filter(visible)
            val now = nowMillis()
            val pinnedIds = shown.filter { it.isPinned }.map { it.id }.toSet()
            val conversations = shown.map { entity ->
                entity.toConversation(
                    byConversation[entity.id]
                        .orEmpty()
                        .map { it.toMessage() }
                        .filterNot { it.isUnshownInboundMms }
                        .sortedBy { it.timestampMillis }
                ).copy(isMuted = ConversationMute.isActive(entity, now))
            }
            ConversationList.sorted(
                conversations = conversations,
                order = ConversationSortOrder.PINNED_FIRST,
                pinnedIds = pinnedIds,
            )
        }
}
