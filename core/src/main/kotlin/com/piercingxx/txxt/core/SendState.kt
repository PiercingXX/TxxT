package com.piercingxx.txxt.core

/**
 * Lifecycle of an outgoing message on its way to the carrier.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the state machine is
 * JVM-testable without a device. Delivery reports are OFF by design
 * (`docs/PRIVACY.md:23`), so there is no delivery-confirmed state — the machine
 * ends at [SENT] once the message is handed to the carrier.
 */
enum class SendState {
    /** Being composed; not yet submitted for sending. */
    DRAFT,

    /** Waiting to be sent (e.g. scheduled or delayed). */
    QUEUED,

    /** Handed to the carrier and in flight. */
    SENDING,

    /** Handed to the carrier successfully; no delivery report is requested. */
    SENT,

    /** The last send attempt failed; the message may be retried. */
    FAILED,
}

/**
 * A minimal, deterministic state machine over [SendState] for an outgoing
 * message. Each transition is validated so an impossible jump (e.g. straight
 * from [SendState.DRAFT] to [SendState.SENT]) is rejected rather than silently
 * accepted.
 */
object SendStateMachine {

    /** The state a freshly composed outgoing message starts in. */
    val initial: SendState = SendState.DRAFT

    /** True when [from] may legally transition to [to]. */
    fun canTransition(from: SendState, to: SendState): Boolean =
        when (from) {
            SendState.DRAFT -> to == SendState.QUEUED || to == SendState.SENDING
            SendState.QUEUED -> to == SendState.SENDING || to == SendState.DRAFT
            SendState.SENDING -> to == SendState.SENT || to == SendState.FAILED
            SendState.SENT -> false
            SendState.FAILED -> to == SendState.SENDING
        }

    /**
     * Applies [to] from [from], returning the new state, or throws
     * [IllegalStateException] when the transition is not allowed.
     */
    fun transition(from: SendState, to: SendState): SendState {
        check(canTransition(from, to)) {
            "illegal send-state transition: $from -> $to"
        }
        return to
    }

    /** True when [state] is a terminal state that cannot transition onward. */
    fun isTerminal(state: SendState): Boolean = state == SendState.SENT
}