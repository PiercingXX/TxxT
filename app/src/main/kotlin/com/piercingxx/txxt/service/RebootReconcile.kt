package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.UnreadCount
import com.piercingxx.txxt.data.ConversationEntity
import com.piercingxx.txxt.data.MessageEntity
import com.piercingxx.txxt.data.Mappers.toConversation
import com.piercingxx.txxt.data.Mappers.toMessage
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.runBlocking

/**
 * Reconciles app state across a device reboot (T2).
 *
 * The data layer persists messages (with their `isRead` flag) in Room, which
 * survives a reboot. [reconcile] rebuilds the in-memory state the running app
 * needs from that persisted data so nothing is lost when the device restarts:
 *
 *  1. **Unread counts survive** — per-conversation unread counts are recomputed
 *     from the persisted `isRead` flags via the pure `core` derivation
 *     ([UnreadCount] over [Message.isUnread]). Because they are re-derived from
 *     the on-disk messages table, they are exactly what they were before the
 *     reboot.
 *  2. **Pending sends survive** — any outgoing message persisted as not-yet-sent
 *     (`MessageEntity.sent == false`) is re-driven through the send pipeline so
 *     a message interrupted mid-send by the reboot is not silently dropped.
 *
 * The component is pure over its three seams — [loadMessages], [loadConversations]
 * and [resendPending] — so the reconcile logic is JVM-testable without a device;
 * the running call site ([BootReceiver]) supplies the real DAOs and send pipeline.
 */
class RebootReconcile(
    /** Loads every persisted message. Defaults to the real Room message DAO. */
    private val loadMessages: suspend () -> List<MessageEntity>,
    /** Loads every persisted conversation. Defaults to the real Room conversation DAO. */
    private val loadConversations: suspend () -> List<ConversationEntity>,
    /** Re-drives a pending outgoing message through the send pipeline. */
    private val resendPending: suspend (Message) -> Unit,
) {

    /** The outcome of a [reconcile] pass. */
    data class ReconcileResult(
        /** Per-conversation unread counts recomputed from the persisted messages. */
        val unreadByConversation: Map<Long, Int>,
        /** Number of pending outgoing messages re-driven through the send pipeline. */
        val pendingResent: Int,
    )

    /**
     * Reconciles unread counts and pending sends from the persisted data.
     *
     * Loads all messages and conversations, recomputes the per-conversation
     * unread counts from the persisted `isRead` flags (so they survive the
     * reboot), and re-drives every outgoing message that was persisted as
     * not-yet-sent through [resendPending].
     */
    suspend fun reconcile(): ReconcileResult {
        val messages = loadMessages().map { it.toMessage() }
        val conversations = loadConversations().map { it.toConversation() }

        // 1. Unread counts survive: recompute per-conversation counts from the
        //    persisted isRead flags. Messages are grouped onto their conversation
        //    so the pure core derivation sees them the way the thread list does.
        val unreadByConversation = UnreadCount.perConversation(
            conversations.map { conv ->
                conv.copy(messages = messages.filter { it.conversationId == conv.id })
            }
        )

        // 2. Pending sends survive: re-drive outgoing messages persisted as
        //    not-yet-sent. A send interrupted by the reboot is retried, never
        //    silently dropped.
        val pending = messages.filter {
            it.direction == MessageDirection.OUTGOING && !it.isSent
        }
        pending.forEach { resendPending(it) }

        return ReconcileResult(
            unreadByConversation = unreadByConversation,
            pendingResent = pending.size,
        )
    }
}

/**
 * The T2 wire-in: a `BOOT_COMPLETED` receiver that runs [RebootReconcile] when
 * the device finishes booting.
 *
 * Registered in the manifest (`app/src/main/AndroidManifest.xml`) with the
 * `android.intent.action.BOOT_COMPLETED` intent-filter and the
 * `RECEIVE_BOOT_COMPLETED` permission. On boot it builds the Room database and
 * runs the reconcile so unread counts and pending sends survive the reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // T6: warn loudly if the default-SMS-handler role was revoked — if the
        // app is no longer the default SMS app, the receivers won't see inbound
        // messages, so the revocation must not be silent.
        DefaultHandlerMonitor().warnIfRevoked(context)

        val database = TxxTDatabase.build(context)
        val reconcile = RebootReconcile(
            loadMessages = { database.messageDao().getAll() },
            loadConversations = { database.conversationDao().getAll() },
            resendPending = { message ->
                message.senderAddress?.let { SendPipeline.sendSms(context, it, message.body) }
            },
        )
        runBlocking { reconcile.reconcile() }
    }
}