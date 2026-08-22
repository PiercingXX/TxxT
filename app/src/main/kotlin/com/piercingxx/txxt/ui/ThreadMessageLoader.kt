package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.data.Mappers.toMessage
import com.piercingxx.txxt.data.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Loads a single conversation's messages as a [Flow] of pure `core` [Message]s.
 *
 * Extracted from [ThreadActivity.observeMessages] as a JVM-testable seam (T1):
 * it owns the conversationId → DAO query → mapper pipeline, so a plain unit test
 * can verify that a specific conversationId drives the query without a device.
 * [ThreadActivity] only supplies the id and collects the result.
 *
 * The DAO is an interface, so a test can inject a fake and assert the loader
 * passes its conversationId into [MessageDao.observeForConversation] and maps
 * the returned entities through [Mappers.toMessage].
 */
class ThreadMessageLoader(
    private val messageDao: MessageDao,
    private val conversationId: Long,
) {

    /**
     * Emits the conversation's messages as pure [Message]s, newest-last (the DAO
     * orders by timestampMillis ASC).
     */
    fun messages(): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId)
            .map { entities -> entities.map { it.toMessage() } }
}