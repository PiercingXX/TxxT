package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.ConversationPin as PinCap

/**
 * Persist pin/unpin for a conversation, refusing a new pin once the cap
 * ([PinCap.MAX_PINNED]) is already full. Unpin is always allowed.
 */
object ConversationPin {

    const val MAX_PINNED: Int = PinCap.MAX_PINNED

    enum class Result { PINNED, UNPINNED, CAP_REACHED, MISSING }

    /**
     * Toggles [conversationId]'s pinned flag. Unpin succeeds even at the
     * cap; a new pin at the cap returns [Result.CAP_REACHED] and writes
     * nothing.
     */
    suspend fun toggle(dao: ConversationDao, conversationId: Long): Result {
        val entity = dao.getById(conversationId) ?: return Result.MISSING
        if (entity.isPinned) {
            dao.update(entity.copy(isPinned = false))
            return Result.UNPINNED
        }
        val pinnedCount = dao.getAll().count { it.isPinned }
        if (!PinCap.canPin(pinnedCount, alreadyPinned = false)) {
            return Result.CAP_REACHED
        }
        dao.update(entity.copy(isPinned = true))
        return Result.PINNED
    }
}
