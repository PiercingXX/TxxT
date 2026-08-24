package com.piercingxx.txxt.service

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport
import com.piercingxx.txxt.data.ConversationEntity
import com.piercingxx.txxt.data.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the T2 reboot reconcile (RebootReconcile.kt).
 *
 * `RebootReconcile.reconcile` is the decision seam the boot path consumes after
 * a device reboot: it recomputes per-conversation unread counts from the
 * persisted `isRead` flags (so unread counts survive the reboot) and re-drives
 * every outgoing message persisted as not-yet-sent through the send pipeline to
 * the conversation's participant address (an outgoing message carries no
 * `senderAddress` by model contract), marking each re-sent row sent afterwards
 * so a successful send is never re-driven on every boot. The component is pure
 * over its four seams (loadMessages / loadConversations / resendPending /
 * markSent), so the reconcile logic is driven directly; the `BootReceiver`
 * wire-in is locked by a source/manifest reading assertion (the established
 * `ThreadWiringTest` / `PermissionGateTest` pattern) plus a `Class.forName`
 * resolution check.
 */
class RebootReconcileTest {

    private fun messageEntity(
        id: Long,
        conversationId: Long,
        direction: MessageDirection,
        isRead: Boolean = false,
        sent: Boolean = true,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        direction = direction.name,
        transport = MessageTransport.SMS.name,
        body = "hello",
        timestampMillis = id * 1000L,
        // Model contract: null for an outgoing message — the destination must
        // come from the conversation's participants.
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
        isRead = isRead,
        sent = sent,
    )

    private fun conversationEntity(id: Long, participants: String = "+15550001111") =
        ConversationEntity(id = id, participantAddresses = participants)

    private fun reconcile(
        messages: List<MessageEntity>,
        conversations: List<ConversationEntity>,
        resend: MutableList<Pair<Message, String>> = mutableListOf(),
        markedSent: MutableList<Long> = mutableListOf(),
    ) = RebootReconcile(
        loadMessages = { messages },
        loadConversations = { conversations },
        resendPending = { message, recipient -> resend.add(message to recipient) },
        markSent = { id -> markedSent.add(id) },
    )

    // ---- Unread counts survive the reboot ----

    @Test
    fun `reconcile recomputes unread counts from the persisted isRead flags`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.INCOMING, isRead = true),
            messageEntity(2L, 1L, MessageDirection.INCOMING, isRead = false),
            messageEntity(3L, 1L, MessageDirection.OUTGOING, isRead = false),
        )
        val resend = mutableListOf<Pair<Message, String>>()

        val result = runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        // Only the unread incoming message counts; the read one and the outgoing
        // one (which is not unread) do not.
        assertEquals(1, result.unreadByConversation[1L])
    }

    @Test
    fun `reconcile reports zero unread when every message is read`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.INCOMING, isRead = true),
            messageEntity(2L, 1L, MessageDirection.OUTGOING),
        )
        val resend = mutableListOf<Pair<Message, String>>()

        val result = runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        assertEquals(0, result.unreadByConversation[1L])
    }

    // ---- Pending sends survive the reboot ----

    @Test
    fun `reconcile re-drives outgoing messages persisted as not-yet-sent`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false),
            messageEntity(2L, 1L, MessageDirection.OUTGOING, sent = true),
            messageEntity(3L, 1L, MessageDirection.INCOMING, sent = false),
        )
        val resend = mutableListOf<Pair<Message, String>>()
        val markedSent = mutableListOf<Long>()

        val result = runBlocking {
            reconcile(messages, listOf(conversationEntity(1L)), resend, markedSent).reconcile()
        }

        // Only the outgoing not-yet-sent message is re-driven; the already-sent
        // outgoing message and the incoming message (sent flag is irrelevant) are not.
        assertEquals(1, result.pendingResent)
        assertEquals(1L, resend.single().first.id)
    }

    @Test
    fun `reconcile re-drives nothing when no message is pending`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = true),
            messageEntity(2L, 1L, MessageDirection.INCOMING),
        )
        val resend = mutableListOf<Pair<Message, String>>()

        val result = runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        assertEquals(0, result.pendingResent)
        assertTrue(resend.isEmpty())
    }

    @Test
    fun `a pending outgoing message is resent to the conversation participant not its null senderAddress`() {
        // The outgoing row carries senderAddress = null by model contract; the
        // destination must come from the conversation's participantAddresses.
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false),
            messageEntity(2L, 1L, MessageDirection.INCOMING, sent = true),
        )
        val resend = mutableListOf<Pair<Message, String>>()

        runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        assertEquals("+15550001111", resend.single().second)
        assertEquals(null, resend.single().first.senderAddress)
        assertEquals(MessageDirection.OUTGOING, resend.single().first.direction)
    }

    @Test
    fun `the first non-blank participant segment is used as the destination`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false),
        )
        val resend = mutableListOf<Pair<Message, String>>()

        runBlocking {
            reconcile(messages, listOf(conversationEntity(1L, participants = "\u0001+15550002222\u0001")), resend)
                .reconcile()
        }

        assertEquals("+15550002222", resend.single().second)
    }

    @Test
    fun `markSent fires exactly for the successfully resended ids`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false),
            messageEntity(4L, 1L, MessageDirection.OUTGOING, sent = false),
            messageEntity(5L, 1L, MessageDirection.OUTGOING, sent = true),
            messageEntity(6L, 1L, MessageDirection.INCOMING, sent = false),
        )
        val resend = mutableListOf<Pair<Message, String>>()
        val markedSent = mutableListOf<Long>()

        runBlocking {
            reconcile(messages, listOf(conversationEntity(1L)), resend, markedSent).reconcile()
        }

        // Every id that went through resendPending is marked sent, so the next
        // boot never re-drives it; already-sent and incoming rows are untouched.
        assertEquals(listOf(1L, 4L), markedSent)
        assertEquals(markedSent, resend.map { it.first.id })
    }

    @Test
    fun `a pending message with no conversation participant is skipped not sent to a null address`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false), // no conversation row at all
            messageEntity(2L, 2L, MessageDirection.OUTGOING, sent = false), // blank participants
            messageEntity(3L, 3L, MessageDirection.OUTGOING, sent = false), // delimiter-only participants
        )
        val conversations = listOf(
            conversationEntity(2L, participants = ""),
            conversationEntity(3L, participants = "\u0001"),
        )
        val resend = mutableListOf<Pair<Message, String>>()
        val markedSent = mutableListOf<Long>()

        val result = runBlocking {
            reconcile(messages, conversations, resend, markedSent).reconcile()
        }

        assertTrue("an unaddressable message must never be handed to the send pipeline", resend.isEmpty())
        assertTrue("a skipped message must not be marked sent", markedSent.isEmpty())
        assertEquals(0, result.pendingResent)
        assertEquals(3, result.pendingSkipped)
    }

    @Test
    fun `a resend that throws is contained to its row and never marks sent`() {
        // A single platform throw (malformed address, permission race, …) must
        // not abort the whole pass — otherwise one bad row crashes every boot
        // until app data is cleared.
        val failing = messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = false)
        val healthy = messageEntity(2L, 1L, MessageDirection.OUTGOING, sent = false)
        val resend = mutableListOf<Pair<Message, String>>()
        val markedSent = mutableListOf<Long>()
        val svc = RebootReconcile(
            loadMessages = { listOf(failing, healthy) },
            loadConversations = { listOf(conversationEntity(1L)) },
            resendPending = { message, recipient ->
                if (message.id == 1L) throw IllegalArgumentException("bad address")
                resend.add(message to recipient)
            },
            markSent = { id -> markedSent.add(id) },
        )

        val result = runBlocking { svc.reconcile() }

        // The healthy row after the throwing one still went out and was marked.
        assertEquals(listOf(2L), markedSent)
        assertEquals(1, resend.size)
        assertEquals("+15550001111", resend.single().second)
        assertEquals(1, result.pendingFailed)
        assertEquals(0, result.pendingSkipped)
        // pendingResent counts successful resends only (the healthy row).
        assertEquals(1, result.pendingResent)
    }

    // ---- Wire-in: the running boot path reaches RebootReconcile ----

    @Test
    fun `the manifest declares the boot receiver and the boot permission`() {
        assertTrue(
            "AndroidManifest.xml must declare the .service.BootReceiver component",
            manifestText.contains(".service.BootReceiver"),
        )
        assertTrue(
            "AndroidManifest.xml must declare the BOOT_COMPLETED intent-filter",
            manifestText.contains("android.intent.action.BOOT_COMPLETED"),
        )
        assertTrue(
            "AndroidManifest.xml must declare RECEIVE_BOOT_COMPLETED",
            manifestText.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
    }

    @Test
    fun `the declared boot receiver name resolves to a class`() {
        Class.forName("com.piercingxx.txxt.service.BootReceiver")
    }

    @Test
    fun `BootReceiver source runs the reboot reconcile on a boot broadcast`() {
        val source = sourceText("service/RebootReconcile.kt")
        assertTrue(
            "BootReceiver must match the BOOT_COMPLETED action",
            source.contains("Intent.ACTION_BOOT_COMPLETED"),
        )
        assertTrue(
            "BootReceiver must construct a RebootReconcile",
            source.contains("RebootReconcile("),
        )
        assertTrue(
            "BootReceiver must run the reconcile",
            source.contains("reconcile.reconcile()"),
        )
    }

    @Test
    fun `BootReceiver source moves the reconcile off the main thread with goAsync`() {
        val source = sourceText("service/RebootReconcile.kt")
        val goAsyncIndex = source.indexOf("goAsync()")
        assertTrue(
            "BootReceiver must call goAsync so the reconcile outlives onReceive without ANR",
            goAsyncIndex >= 0,
        )
        assertTrue(
            "BootReceiver must run the reconcile on Dispatchers.IO",
            source.contains("Dispatchers.IO"),
        )
        assertTrue(
            "goAsync must always be followed by pendingResult.finish(), even on failure",
            Regex("""finally\s*\{\s*pendingResult\.finish\(\)""").containsMatchIn(source),
        )
        assertTrue(
            "BootReceiver must never block the main thread with runBlocking",
            !source.contains("runBlocking"),
        )
    }

    @Test
    fun `BootReceiver source resends to the resolved recipient and marks sends complete`() {
        val source = sourceText("service/RebootReconcile.kt")
        assertTrue(
            "BootReceiver must wire resendPending with an explicit recipient parameter",
            source.contains("resendPending = { message, recipient ->"),
        )
        assertTrue(
            "BootReceiver must send to the resolved recipient, never message.senderAddress",
            source.contains("SendPipeline.sendSms(context, recipient, message.body)") &&
                !source.contains("message.senderAddress?.let"),
        )
        assertTrue(
            "BootReceiver must wire markSent through the message DAO",
            source.contains("database.messageDao().markSent(id)"),
        )
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifestText: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()
}