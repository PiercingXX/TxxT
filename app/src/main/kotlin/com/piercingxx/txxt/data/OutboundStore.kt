package com.piercingxx.txxt.data

import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.MmsRetrievedContent

/**
 * Persistence entry point for outgoing sends — the store every outbound SMS
 * path (the compose screen's hand-off, the notification quick reply) persists
 * through BEFORE dispatching through the send pipeline.
 *
 * The contract that makes it a real pipeline stage, not a side note: the row
 * lands with `sent = false` first, so an interrupted send (process death,
 * reboot between persist and transmit) leaves a pending-send row behind that
 * [com.piercingxx.txxt.service.RebootReconcile] re-drives on next boot. Only
 * after the send reports success does the caller `markSent` the id returned
 * here — exactly the compose path's persist-then-send flow
 * (`ThreadActivity.sendComposed`).
 *
 * Pure-ish over its DAO parameters: zero `android.*` imports, DAOs injected —
 * JVM-testable with hand-rolled fakes ([InboundStore]'s established style).
 *
 * **Concurrency:** both phases (conversation find-or-create + message-id
 * allocation and upsert) run under [InboundStore.withPersistenceLock] — the
 * SAME mutex inbound delivery uses, acquired once for the whole sequence so
 * the pair is atomic against concurrent writes and deletes. No second lock is
 * added anywhere.
 */
object OutboundStore {

    /**
     * Persists an outgoing SMS as a read, not-yet-sent row in the recipient's
     * (find-or-created) conversation and returns the message id.
     *
     * Field contract (mirrors `ThreadActivity.sendComposed`):
     *  - `direction = OUTGOING`, `transport = SMS`;
     *  - `senderAddress = null` — model contract: null for outgoing messages,
     *    the destination lives on the conversation's participants;
     *  - `isRead = true` — the user wrote this message; it never counts as
     *    unread;
     *  - **`sent = false`** — the pending-send flag: `RebootReconcile`
     *    re-drives unsent rows through the send pipeline, so a crash or
     *    reboot between this persist and the platform send cannot silently
     *    drop the reply. Callers MUST mark the returned id sent only after
     *    the send succeeded (`MessageDao.markSent`).
     */
    suspend fun persistOutgoingSms(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        body: String,
    ): Long = InboundStore.withPersistenceLock {
        // Both phases under ONE lock acquisition: find-or-create + insert are
        // check-then-act as a pair, and a lock released between them would let
        // a concurrent delete path orphan the message onto a vanished
        // conversation (the mutex is not reentrant, so the public
        // findOrCreateConversation — which self-locks — must NOT be called
        // in here; the internal unlocked variant is exactly for this).
        val conversationId = InboundStore.findOrCreateConversationLocked(conversations, address)
        // Sending into an archived or quarantined thread surfaces it.
        InboundStore.surfaceConversation(conversations, conversationId)
        val message = MessageEntity(
            id = InboundStore.freshMessageId(messages),
            conversationId = conversationId,
            direction = MessageDirection.OUTGOING.name,
            transport = MessageTransport.SMS.name,
            body = body,
            timestampMillis = System.currentTimeMillis(),
            senderAddress = null,
            isRead = true,
            sent = false,
        )
        messages.upsert(message)
        message.id
    }

    /**
     * Persists an outgoing **MMS** (a photo attachment) as a read,
     * not-yet-sent row, and returns the message id.
     *
     * Identical in shape to [persistOutgoingSms] — same lock, same
     * find-or-create, same `sent = false` pending-send contract — except the
     * transport, which is what makes this a separate entry point rather than a
     * boolean parameter: the transport is the one field downstream code
     * branches on. Two places read it:
     *
     *  - [com.piercingxx.txxt.service.RebootReconcile] does **not** re-drive a
     *    pending MMS row. The media lives in a staged cache copy that a reboot
     *    may legitimately have reclaimed, and re-driving the row through the
     *    SMS path would transmit a *different message* than the operator
     *    composed (an empty-caption photo would go out as an empty SMS). The
     *    row is left pending, visibly unsent, rather than turned into
     *    something it is not.
     *  - [com.piercingxx.txxt.ui.ThreadMessagePresenter] renders an outgoing
     *    MMS row with a blank body as `[Photo]`, matching inbound photos, so
     *    a photo sent with no caption is a visible, tappable row.
     *
     * [body] defaults to `[Photo]`: a caption composed alongside a photo
     * travels as its own SMS row (`PhotoAttachment.plan`), because the send
     * pipeline's MMS entry point carries media only. The parameter exists so
     * this store stays a faithful persist of whatever the caller actually sent.
     */
    suspend fun persistOutgoingMms(
        conversations: ConversationDao,
        messages: MessageDao,
        address: String,
        body: String = MmsRetrievedContent.COLLAPSED_PHOTO_PLACEHOLDER,
        mediaPath: String? = null,
    ): Long = InboundStore.withPersistenceLock {
        val conversationId = InboundStore.findOrCreateConversationLocked(conversations, address)
        // Sending into an archived or quarantined thread surfaces it.
        InboundStore.surfaceConversation(conversations, conversationId)
        val message = MessageEntity(
            id = InboundStore.freshMessageId(messages),
            conversationId = conversationId,
            direction = MessageDirection.OUTGOING.name,
            transport = MessageTransport.MMS.name,
            body = body,
            timestampMillis = System.currentTimeMillis(),
            senderAddress = null,
            isRead = true,
            sent = false,
            mediaPath = mediaPath,
        )
        messages.upsert(message)
        message.id
    }
}
