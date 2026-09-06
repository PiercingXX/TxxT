package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MuteUntil
import com.piercingxx.txxt.core.PhoneNumbers

/**
 * Whether notifications for a conversation are currently suppressed.
 *
 * Forever-mute ([ConversationEntity.isMuted]) stays on until unmuted.
 * Mute-until ([ConversationEntity.mutedUntilMillis]) expires on its own
 * after the wall time, so the next SMS notifies without a second tap.
 */
object ConversationMute {

    fun isActive(entity: ConversationEntity, nowMillis: Long): Boolean =
        entity.isMuted || MuteUntil.isActive(entity.mutedUntilMillis, nowMillis)

    suspend fun isMuted(
        dao: ConversationDao,
        address: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        dao.getAll().any { entity ->
            isActive(entity, nowMillis) &&
                entity.participantAddresses.split('\u0001')
                    .filter { it.isNotBlank() }
                    .any { PhoneNumbers.matches(it, address) }
        }
}
