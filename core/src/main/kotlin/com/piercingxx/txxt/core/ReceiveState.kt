package com.piercingxx.txxt.core

/**
 * Lifecycle of an inbound message arriving from the carrier.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the state machine is
 * JVM-testable without a device. MMS auto-download is OFF by design
 * (`docs/PRIVACY.md:151`): remote MMS content is fetched only on explicit tap,
 * so an inbound MMS sits in [MMS_DOWNLOAD_PENDING] until the user asks for it.
 * An inbound audio MMS is dropped at the inbox boundary, never downloaded and
 * never stored (`docs/PRIVACY.md:91`).
 */
enum class ReceiveState {
    /** The message arrived; content is available (an SMS) or a bare MMS notice. */
    INBOUND,

    /** An MMS notice arrived but its remote content is not yet downloaded. */
    MMS_DOWNLOAD_PENDING,

    /** The user tapped to download the MMS content; it is being fetched. */
    DOWNLOADING,

    /** The message content is available and stored. */
    RECEIVED,

    /** The last download attempt failed; the message may be retried. */
    FAILED,

    /** The message was dropped at the inbox boundary (e.g. inbound audio MMS). */
    DROPPED,
}

/**
 * A minimal, deterministic state machine over [ReceiveState] for an inbound
 * message. Each transition is validated so an impossible jump (e.g. straight
 * from [ReceiveState.INBOUND] to [ReceiveState.RECEIVED] for an MMS that was
 * never downloaded) is rejected rather than silently accepted.
 */
object ReceiveStateMachine {

    /** The state a freshly arrived inbound message starts in. */
    val initial: ReceiveState = ReceiveState.INBOUND

    /** True when [from] may legally transition to [to]. */
    fun canTransition(from: ReceiveState, to: ReceiveState): Boolean =
        when (from) {
            ReceiveState.INBOUND ->
                to == ReceiveState.MMS_DOWNLOAD_PENDING ||
                    to == ReceiveState.RECEIVED ||
                    to == ReceiveState.DROPPED
            ReceiveState.MMS_DOWNLOAD_PENDING ->
                to == ReceiveState.DOWNLOADING || to == ReceiveState.DROPPED
            ReceiveState.DOWNLOADING ->
                to == ReceiveState.RECEIVED || to == ReceiveState.FAILED
            ReceiveState.FAILED -> to == ReceiveState.DOWNLOADING
            ReceiveState.RECEIVED -> false
            ReceiveState.DROPPED -> false
        }

    /**
     * Applies [to] from [from], returning the new state, or throws
     * [IllegalStateException] when the transition is not allowed.
     */
    fun transition(from: ReceiveState, to: ReceiveState): ReceiveState {
        check(canTransition(from, to)) {
            "illegal receive-state transition: $from -> $to"
        }
        return to
    }

    /** True when [state] is a terminal state that cannot transition onward. */
    fun isTerminal(state: ReceiveState): Boolean =
        state == ReceiveState.RECEIVED || state == ReceiveState.DROPPED
}