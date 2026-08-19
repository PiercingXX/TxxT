package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDelayTest {

    private fun message(id: Long = 1L): Message =
        Message(
            id = id,
            conversationId = 10L,
            direction = MessageDirection.OUTGOING,
            transport = MessageTransport.SMS,
            body = "hello",
            timestampMillis = 0L,
        )

    @Test
    fun `initial scheduled state is pending`() {
        assertEquals(ScheduledState.PENDING, ScheduledMessageStateMachine.initial)
    }

    @Test
    fun `pending can become delayed scheduled or cancelled`() {
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.PENDING, ScheduledState.DELAYED))
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.PENDING, ScheduledState.SCHEDULED))
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.PENDING, ScheduledState.CANCELLED))
    }

    @Test
    fun `delayed can return to pending or be cancelled`() {
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.DELAYED, ScheduledState.PENDING))
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.DELAYED, ScheduledState.CANCELLED))
    }

    @Test
    fun `scheduled can return to pending or be cancelled`() {
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.SCHEDULED, ScheduledState.PENDING))
        assertTrue(ScheduledMessageStateMachine.canTransition(ScheduledState.SCHEDULED, ScheduledState.CANCELLED))
    }

    @Test
    fun `cancelled is terminal`() {
        assertTrue(ScheduledMessageStateMachine.isTerminal(ScheduledState.CANCELLED))
        assertFalse(ScheduledMessageStateMachine.isTerminal(ScheduledState.PENDING))
        assertFalse(ScheduledMessageStateMachine.isTerminal(ScheduledState.DELAYED))
        assertFalse(ScheduledMessageStateMachine.isTerminal(ScheduledState.SCHEDULED))
    }

    @Test
    fun `a delayed message computes its send time from base time plus the delay`() {
        val scheduled = ScheduledMessage(message = message(), delayMillis = 300_000L, baseTimeMillis = 1_000L)
        assertEquals(1_000L + 300_000L, scheduled.sendAtMillis())
    }

    @Test
    fun `a scheduled message uses its absolute time`() {
        val scheduled = ScheduledMessage(message = message(), scheduledAtMillis = 1_000_000L)
        assertEquals(1_000_000L, scheduled.sendAtMillis())
    }

    @Test
    fun `a delayed message is not due before the delay elapses`() {
        val scheduled = ScheduledMessage(message = message(), delayMillis = 300_000L, baseTimeMillis = 1_000L)
        assertFalse(scheduled.isDue(1_000L))
        assertFalse(scheduled.isDue(1_000L + 299_999L))
    }

    @Test
    fun `a delayed message is due once the delay elapses`() {
        val scheduled = ScheduledMessage(message = message(), delayMillis = 300_000L, baseTimeMillis = 1_000L)
        assertTrue(scheduled.isDue(1_000L + 300_000L))
        assertTrue(scheduled.isDue(1_000L + 400_000L))
    }

    @Test
    fun `a scheduled message is due at or after its appointment time`() {
        val scheduled = ScheduledMessage(message = message(), scheduledAtMillis = 1_000_000L)
        assertFalse(scheduled.isDue(999_999L))
        assertTrue(scheduled.isDue(1_000_000L))
        assertTrue(scheduled.isDue(1_000_001L))
    }

    @Test
    fun `a cancelled message is never due`() {
        val scheduled = ScheduledMessage(
            message = message(),
            scheduledAtMillis = 1_000L,
            state = ScheduledState.CANCELLED,
        )
        assertFalse(scheduled.isDue(1_000_000L))
    }

    @Test
    fun `a full happy-path delay resolves to pending at send time`() {
        var state = ScheduledMessageStateMachine.transition(ScheduledState.PENDING, ScheduledState.DELAYED)
        state = ScheduledMessageStateMachine.transition(state, ScheduledState.PENDING)
        assertEquals(ScheduledState.PENDING, state)
    }

    @Test(expected = IllegalStateException::class)
    fun `delayed cannot switch to scheduled without returning to pending`() {
        ScheduledMessageStateMachine.transition(ScheduledState.DELAYED, ScheduledState.SCHEDULED)
    }

    @Test(expected = IllegalStateException::class)
    fun `cancelled cannot transition onward`() {
        ScheduledMessageStateMachine.transition(ScheduledState.CANCELLED, ScheduledState.PENDING)
    }
}