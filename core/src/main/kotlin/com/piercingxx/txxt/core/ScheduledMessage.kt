package com.piercingxx.txxt.core

/**
 * Lifecycle of an outgoing message that is not sent immediately.
 *
 * Pure-Kotlin value type with zero `android.*` imports so the state machine is
 * JVM-testable without a device. A message may be held back from the carrier
 * either by a relative [delay] (a "send later" countdown) or by an absolute
 * wall-clock [scheduledAtMillis] (a "send at this time" appointment). Both
 * resolve to a concrete send time; the message stays pending until that time
 * arrives and the caller hands it to the send state machine
 * ([SendStateMachine]) as [SendState.QUEUED].
 */
enum class ScheduledState {
    /** Waiting for its send time; not yet handed to the send machine. */
    PENDING,

    /** Held for a relative delay (e.g. "send in 5 minutes"). */
    DELAYED,

    /** Held for an absolute appointment time (e.g. "send at 14:00"). */
    SCHEDULED,

    /** The user cancelled the pending send; it will never be dispatched. */
    CANCELLED,
}

/**
 * A message held back from immediate sending, either by a relative delay or an
 * absolute scheduled time.
 *
 * Exactly one of [delayMillis] and [scheduledAtMillis] is set for a real
 * pending message; the other is null. [baseTimeMillis] is the epoch-millis
 * moment the delay was set, so a relative delay resolves to a fixed send time
 * rather than drifting with the moment it is queried. Timestamps are epoch
 * milliseconds.
 */
data class ScheduledMessage(
    val message: Message,
    /** Relative delay in milliseconds before send; null when scheduled absolutely. */
    val delayMillis: Long? = null,
    /** Absolute epoch-millis send time; null when delayed relatively. */
    val scheduledAtMillis: Long? = null,
    /** Epoch-millis moment the delay was set; used only with [delayMillis]. */
    val baseTimeMillis: Long = 0L,
    val state: ScheduledState = ScheduledState.PENDING,
) {
    /** The fixed epoch-millis time this message should be sent. */
    fun sendAtMillis(): Long =
        when {
            scheduledAtMillis != null -> scheduledAtMillis
            delayMillis != null -> baseTimeMillis + delayMillis
            else -> error("scheduled message has neither a delay nor an absolute time")
        }

    /** True when the send time has arrived at [now] and the message may be dispatched. */
    fun isDue(now: Long): Boolean = state != ScheduledState.CANCELLED && sendAtMillis() <= now
}

/**
 * A minimal, deterministic state machine over [ScheduledState] for a held-back
 * outgoing message. Each transition is validated so an impossible jump (e.g.
 * straight from [ScheduledState.PENDING] to [ScheduledState.CANCELLED] for a
 * message that never entered a delay) is rejected rather than silently accepted.
 */
object ScheduledMessageStateMachine {

    /** The state a freshly scheduled or delayed message starts in. */
    val initial: ScheduledState = ScheduledState.PENDING

    /** True when [from] may legally transition to [to]. */
    fun canTransition(from: ScheduledState, to: ScheduledState): Boolean =
        when (from) {
            ScheduledState.PENDING ->
                to == ScheduledState.DELAYED ||
                    to == ScheduledState.SCHEDULED ||
                    to == ScheduledState.CANCELLED
            ScheduledState.DELAYED ->
                to == ScheduledState.PENDING || to == ScheduledState.CANCELLED
            ScheduledState.SCHEDULED ->
                to == ScheduledState.PENDING || to == ScheduledState.CANCELLED
            ScheduledState.CANCELLED -> false
        }

    /**
     * Applies [to] from [from], returning the new state, or throws
     * [IllegalStateException] when the transition is not allowed.
     */
    fun transition(from: ScheduledState, to: ScheduledState): ScheduledState {
        check(canTransition(from, to)) {
            "illegal scheduled-state transition: $from -> $to"
        }
        return to
    }

    /** True when [state] is a terminal state that cannot transition onward. */
    fun isTerminal(state: ScheduledState): Boolean = state == ScheduledState.CANCELLED
}