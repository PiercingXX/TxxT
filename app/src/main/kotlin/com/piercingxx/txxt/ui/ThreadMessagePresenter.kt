package com.piercingxx.txxt.ui

import com.piercingxx.txxt.core.Message
import com.piercingxx.txxt.core.MessageDirection
import com.piercingxx.txxt.core.MessageTransport

/**
 * Horizontal alignment of a message row within the thread.
 *
 * Per DESIGN.md §"Conversation thread": inbound left, outbound right — the
 * thread is text-first lines, never chat bubbles (PRIVACY.md §2).
 */
enum class ThreadAlignment {
    /** Inbound (received) messages sit on the left. */
    LEFT,

    /** Outbound (sent) messages sit on the right. */
    RIGHT,
}

/**
 * Visual emphasis of a message row, from the brand's white-opacity ramp
 * (DESIGN.md §"Brand system"): sent = `signal`-white text on black (inverted
 * emphasis); received = `slate`/`graphite` text on black. Hierarchy comes from
 * the ramp, never hue.
 */
enum class ThreadEmphasis {
    /** Received message: muted `slate`/`graphite` text. */
    RECEIVED,

    /** Sent message: `signal`-white inverted emphasis. */
    SENT,
}

/**
 * Presenter for a single message row in a conversation thread.
 *
 * Pure Kotlin with zero `android.*` imports so the mapping is JVM-testable
 * without a device. Maps a `core.Message` to the view state a row layout
 * binds: horizontal alignment (inbound left / outbound right) and emphasis
 * (sent inverted / received muted). The row renders as a text-first line, so
 * no bubble/card state is produced — the no-bubble constraint is structural
 * (DESIGN.md §"Conversation thread", PRIVACY.md §2).
 */
object ThreadMessagePresenter {

    /**
     * Maps a message to its thread-row view state.
     *
     * The mapping is direction-driven: OUTGOING → right-aligned, SENT emphasis;
     * INCOMING → left-aligned, RECEIVED emphasis. Body and timestamp pass
     * through unchanged — the presenter owns layout semantics, not content.
     */
    fun present(message: Message): ThreadRow {
        val (alignment, emphasis) = when (message.direction) {
            MessageDirection.OUTGOING -> ThreadAlignment.RIGHT to ThreadEmphasis.SENT
            MessageDirection.INCOMING -> ThreadAlignment.LEFT to ThreadEmphasis.RECEIVED
        }
        return ThreadRow(
            alignment = alignment,
            emphasis = emphasis,
            body = bodyFor(message),
            timestampMillis = message.timestampMillis,
            mediaPath = message.mediaPath,
        )
    }

    /**
     * The text a row shows.
     *
     * Bodies pass through unchanged with exactly one substitution: an
     * **outgoing MMS with a blank body** — a photo the operator sent with no
     * caption — renders as the text-first `[photo]` marker rather than as an
     * empty line. Without it, sending a photo produces a row of nothing at all
     * and the thread looks like the send vanished.
     *
     * Deliberately narrow. It is scoped to OUTGOING because that is the row
     * this app creates and therefore the only one whose media it knows to be a
     * photo. Inbound MMS is not invented here: empty inbound MMS is not a
     * message (see [Message.isUnshownInboundMms]) and is dropped before bind.
     */
    private fun bodyFor(message: Message): String =
        when {
            message.direction == MessageDirection.OUTGOING &&
                message.transport == MessageTransport.MMS &&
                message.body.isBlank() -> PhotoAttachment.PHOTO_ROW_PLACEHOLDER
            else -> message.body
        }
}

/**
 * View state for one thread row, produced by [ThreadMessagePresenter].
 */
data class ThreadRow(
    val alignment: ThreadAlignment,
    val emphasis: ThreadEmphasis,
    val body: String,
    val timestampMillis: Long,
    val mediaPath: String? = null,
)