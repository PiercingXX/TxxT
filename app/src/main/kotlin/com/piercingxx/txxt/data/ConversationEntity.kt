package com.piercingxx.txxt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a conversation (thread).
 *
 * Mirrors the pure `core/Conversation` model (id + participant addresses) plus
 * the persisted conversation flags and the starred-contact flag the data layer
 * owns. `participantAddresses` is stored as a single delimiter-joined string
 * because Room cannot persist a `Set<String>` without a TypeConverter; the pure
 * mapper (T2) converts between this string and the `core` set.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    /** Delimiter-joined remote participant addresses; empty for a self-only thread. */
    val participantAddresses: String,
    /** Whether this contact is starred (the goal's starred-contact flag). */
    val isStarred: Boolean = false,
    /** Whether the conversation is pinned to the top of the list. */
    val isPinned: Boolean = false,
    /** Whether the conversation is archived (hidden from the main list). */
    val isArchived: Boolean = false,
    /** Whether notifications for this thread are suppressed (starred still notifies). */
    val isMuted: Boolean = false,
    /**
     * Whether this thread is held in quarantine (hidden from the main list).
     * Unknown-sender holds land here unread until the operator delivers,
     * blocks, or deletes them.
     */
    val isQuarantined: Boolean = false,
    /**
     * Epoch millis until which notifications stay off. `0` means no
     * time-based mute; forever-mute is [isMuted].
     */
    val mutedUntilMillis: Long = 0L,
    /** The sort order applied to the conversation list, as a `core` enum name. */
    val sortOrder: String = "PINNED_FIRST",
)