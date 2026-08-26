package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.PhoneNumbers

/** Whether the conversation for [address] is muted. */
object ConversationMute {
    suspend fun isMuted(dao: ConversationDao, address: String): Boolean =
        dao.getAll().any { entity ->
            entity.isMuted &&
                entity.participantAddresses.split('\u0001')
                    .filter { it.isNotBlank() }
                    .any { PhoneNumbers.matches(it, address) }
        }
}
