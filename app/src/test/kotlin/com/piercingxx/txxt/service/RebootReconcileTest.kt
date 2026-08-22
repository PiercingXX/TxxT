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
 * every outgoing message persisted as not-yet-sent through the send pipeline
 * (so pending sends survive). The component is pure over its three seams
 * (loadMessages / loadConversations / resendPending), so the reconcile logic is
 * driven directly; the `BootReceiver` wire-in is locked by a source/manifest
 * reading assertion (the established `ThreadWiringTest` / `PermissionGateTest`
 * pattern) plus a `Class.forName` resolution check.
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
        senderAddress = if (direction == MessageDirection.INCOMING) "+15550001111" else null,
        isRead = isRead,
        sent = sent,
    )

    private fun conversationEntity(id: Long) =
        ConversationEntity(id = id, participantAddresses = "+15550001111")

    private fun reconcile(
        messages: List<MessageEntity>,
        conversations: List<ConversationEntity>,
        resend: MutableList<Message>,
    ) = RebootReconcile(
        loadMessages = { messages },
        loadConversations = { conversations },
        resendPending = { resend.add(it) },
    )

    // ---- Unread counts survive the reboot ----

    @Test
    fun `reconcile recomputes unread counts from the persisted isRead flags`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.INCOMING, isRead = true),
            messageEntity(2L, 1L, MessageDirection.INCOMING, isRead = false),
            messageEntity(3L, 1L, MessageDirection.OUTGOING, isRead = false),
        )
        val resend = mutableListOf<Message>()

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
        val resend = mutableListOf<Message>()

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
        val resend = mutableListOf<Message>()

        val result = runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        // Only the outgoing not-yet-sent message is re-driven; the already-sent
        // outgoing message and the incoming message (sent flag is irrelevant) are not.
        assertEquals(1, result.pendingResent)
        assertEquals(1L, resend.single().id)
    }

    @Test
    fun `reconcile re-drives nothing when no message is pending`() {
        val messages = listOf(
            messageEntity(1L, 1L, MessageDirection.OUTGOING, sent = true),
            messageEntity(2L, 1L, MessageDirection.INCOMING),
        )
        val resend = mutableListOf<Message>()

        val result = runBlocking { reconcile(messages, listOf(conversationEntity(1L)), resend).reconcile() }

        assertEquals(0, result.pendingResent)
        assertTrue(resend.isEmpty())
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