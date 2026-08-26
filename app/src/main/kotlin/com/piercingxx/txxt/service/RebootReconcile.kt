package com.piercingxx.txxt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.core.UnreadCount
import com.piercingxx.txxt.data.ConversationEntity
import com.piercingxx.txxt.data.MessageEntity
import com.piercingxx.txxt.data.Mappers.toConversation
import com.piercingxx.txxt.data.Mappers.toMessage
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Delimiter joining participant addresses in a [ConversationEntity] (see [Mappers]). */
private const val ADDRESS_DELIMITER = "\u0001"

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
 *     Its destination comes from its conversation's participant addresses (an
 *     outgoing message carries no `senderAddress` by model contract), and once
 *     it has been re-sent the row is marked sent ([markSent]) so the next boot
 *     never re-drives an already-transmitted message. A pending **MMS** row is
 *     the one exception and is held, never re-driven — see the media rule in
 *     [reconcile].
 *
 * The component is pure over its four seams — [loadMessages], [loadConversations],
 * [resendPending] and [markSent] — so the reconcile logic is JVM-testable without
 * a device; the running call site ([BootReceiver]) supplies the real DAOs and
 * send pipeline.
 */
class RebootReconcile(
    /** Loads every persisted message. Defaults to the real Room message DAO. */
    private val loadMessages: suspend () -> List<MessageEntity>,
    /** Loads every persisted conversation. Defaults to the real Room conversation DAO. */
    private val loadConversations: suspend () -> List<ConversationEntity>,
    /**
     * Re-drives a pending outgoing [Message] through the send pipeline to the
     * given recipient address (resolved from the conversation's participants).
     * Returns true when the platform send was attempted; false when the gate
     * denied (no throw) so the row stays pending.
     */
    private val resendPending: suspend (Message, String) -> Boolean,
    /**
     * Marks a message (by id) as sent after [resendPending] returned without
     * throwing, so the row is not re-driven on the next boot.
     */
    private val markSent: suspend (Long) -> Unit,
) {

    /** The outcome of a [reconcile] pass. */
    data class ReconcileResult(
        /** Per-conversation unread counts recomputed from the persisted messages. */
        val unreadByConversation: Map<Long, Int>,
        /** Number of pending outgoing messages re-driven through the send pipeline. */
        val pendingResent: Int,
        /** Pending outgoing messages skipped because their thread had no participant address. */
        val pendingSkipped: Int = 0,
        /** Pending outgoing messages whose resend threw; the rows stay pending for the next boot. */
        val pendingFailed: Int = 0,
        /**
         * Pending outgoing **MMS** rows deliberately not re-driven. See the
         * media rule in [reconcile]: their media is a staged cache copy the
         * reboot may have reclaimed, and this pipeline's resend seam is the
         * SMS path, so re-driving one would transmit something the operator
         * never composed. The rows stay pending and visibly unsent.
         */
        val pendingMediaHeld: Int = 0,
    )

    /**
     * Reconciles unread counts and pending sends from the persisted data.
     *
     * Loads all messages and conversations, recomputes the per-conversation
     * unread counts from the persisted `isRead` flags (so they survive the
     * reboot), and re-drives every outgoing message that was persisted as
     * not-yet-sent through [resendPending], marking each one sent afterwards.
     */
    suspend fun reconcile(): ReconcileResult {
        val conversationEntities = loadConversations()
        val messages = loadMessages().map { it.toMessage() }
        val conversations = conversationEntities.map { it.toConversation() }

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
        //    silently dropped. The destination cannot come from the message
        //    itself (an outgoing message's senderAddress is null by contract);
        //    it comes from the conversation's participant addresses.
        val conversationById = conversationEntities.associateBy { it.id }
        val pending = messages.filter {
            it.direction == MessageDirection.OUTGOING && !it.isSent
        }
        var pendingResent = 0
        var pendingSkipped = 0
        var pendingFailed = 0
        var pendingMediaHeld = 0
        pending.forEach { message ->
            // The media rule. A pending MMS row is a photo send that did not
            // complete; its media is a staged copy in the cache directory,
            // which the OS may reclaim at any time and which this reconcile
            // has no reference to. [resendPending] is the SMS path, so
            // re-driving the row would put the row's BODY on the wire instead
            // of the photo — and for the common empty-caption photo that means
            // attempting an empty SMS, which the platform rejects, on every
            // boot forever. Hold the row instead: it stays pending, it stays
            // visible in the thread as unsent, and nothing is transmitted that
            // the operator did not compose.
            if (message.transport == MessageTransport.MMS) {
                pendingMediaHeld += 1
                return@forEach
            }
            val recipient = conversationById[message.conversationId]
                ?.participantAddresses
                ?.split(ADDRESS_DELIMITER)
                ?.firstOrNull { it.isNotBlank() }
            if (recipient == null) {
                // Nowhere to send: skip rather than drop the row or crash.
                pendingSkipped += 1
                return@forEach
            }
            // A single platform throw (malformed address, permission race, …)
            // must not abort the whole pass: one bad row would otherwise crash
            // every boot until app data is cleared. Contain the failure to the
            // row — it stays pending (not marked sent) and the loop continues.
            val attempted = try {
                resendPending(message, recipient)
            } catch (_: Exception) {
                false
            }
            if (!attempted) {
                pendingFailed += 1
                return@forEach
            }
            // Mark sent only after a resend the pipeline reported as attempted.
            // A gate deny returns false without throwing — those rows stay
            // pending for the next boot instead of looking sent.
            markSent(message.id)
            pendingResent += 1
        }

        return ReconcileResult(
            unreadByConversation = unreadByConversation,
            pendingResent = pendingResent,
            pendingSkipped = pendingSkipped,
            pendingFailed = pendingFailed,
            pendingMediaHeld = pendingMediaHeld,
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
 *
 * The reconcile loads full tables and drives synchronous SMS sends, so running
 * it inline on the main thread would blow the receiver's ~10 s ANR budget (H5):
 * [goAsync] extends the receiver's lifetime past `onReceive` and the work runs
 * on `Dispatchers.IO`, with `pendingResult.finish()` guaranteed afterwards.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // T6: warn loudly if the default-SMS-handler role was revoked — if the
        // app is no longer the default SMS app, the receivers won't see inbound
        // messages, so the revocation must not be silent. This is quick and
        // stays on the main path before the heavy work moves off-thread.
        DefaultHandlerMonitor().warnIfRevoked(context)

        val pendingResult = goAsync()
        // Backstop: an unexpected throw outside the per-row containment must
        // never crash the boot process — the receiver finishes quietly instead.
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val database = TxxTDatabase.instance(context)
                val reconcile = RebootReconcile(
                    loadMessages = { database.messageDao().getAll() },
                    loadConversations = { database.conversationDao().getAll() },
                    resendPending = { message, recipient ->
                        // The destination is the conversation participant resolved
                        // by the reconcile — never message.senderAddress, which is
                        // null for an outgoing message by model contract.
                        SendPipeline.sendSms(context, recipient, message.body)
                    },
                    markSent = { id -> database.messageDao().markSent(id) },
                )
                reconcile.reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }
}