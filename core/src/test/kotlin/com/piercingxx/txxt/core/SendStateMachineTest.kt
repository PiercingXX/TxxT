package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendStateMachineTest {

    @Test
    fun `initial state is draft`() {
        assertEquals(SendState.DRAFT, SendStateMachine.initial)
    }

    @Test
    fun `draft can be queued or sent directly`() {
        assertTrue(SendStateMachine.canTransition(SendState.DRAFT, SendState.QUEUED))
        assertTrue(SendStateMachine.canTransition(SendState.DRAFT, SendState.SENDING))
    }

    @Test
    fun `queued can move to sending or back to draft`() {
        assertTrue(SendStateMachine.canTransition(SendState.QUEUED, SendState.SENDING))
        assertTrue(SendStateMachine.canTransition(SendState.QUEUED, SendState.DRAFT))
    }

    @Test
    fun `sending resolves to sent or failed`() {
        assertTrue(SendStateMachine.canTransition(SendState.SENDING, SendState.SENT))
        assertTrue(SendStateMachine.canTransition(SendState.SENDING, SendState.FAILED))
    }

    @Test
    fun `failed can be retried back to sending`() {
        assertTrue(SendStateMachine.canTransition(SendState.FAILED, SendState.SENDING))
    }

    @Test
    fun `sent is terminal`() {
        assertTrue(SendStateMachine.isTerminal(SendState.SENT))
        assertFalse(SendStateMachine.isTerminal(SendState.DRAFT))
        assertFalse(SendStateMachine.isTerminal(SendState.QUEUED))
        assertFalse(SendStateMachine.isTerminal(SendState.SENDING))
        assertFalse(SendStateMachine.isTerminal(SendState.FAILED))
    }

    @Test
    fun `a full happy-path send progresses draft to sent`() {
        var state = SendStateMachine.transition(SendState.DRAFT, SendState.SENDING)
        state = SendStateMachine.transition(state, SendState.SENT)
        assertEquals(SendState.SENT, state)
    }

    @Test
    fun `a scheduled send progresses draft queued sending sent`() {
        var state = SendStateMachine.transition(SendState.DRAFT, SendState.QUEUED)
        state = SendStateMachine.transition(state, SendState.SENDING)
        state = SendStateMachine.transition(state, SendState.SENT)
        assertEquals(SendState.SENT, state)
    }

    @Test
    fun `a failed send can be retried and succeed`() {
        var state = SendStateMachine.transition(SendState.DRAFT, SendState.SENDING)
        state = SendStateMachine.transition(state, SendState.FAILED)
        state = SendStateMachine.transition(state, SendState.SENDING)
        state = SendStateMachine.transition(state, SendState.SENT)
        assertEquals(SendState.SENT, state)
    }

    @Test(expected = IllegalStateException::class)
    fun `draft cannot jump straight to sent`() {
        SendStateMachine.transition(SendState.DRAFT, SendState.SENT)
    }

    @Test(expected = IllegalStateException::class)
    fun `sent cannot transition onward`() {
        SendStateMachine.transition(SendState.SENT, SendState.SENDING)
    }

    @Test(expected = IllegalStateException::class)
    fun `queued cannot fail directly without sending`() {
        SendStateMachine.transition(SendState.QUEUED, SendState.FAILED)
    }

    @Test(expected = IllegalStateException::class)
    fun `failed cannot jump straight to sent`() {
        SendStateMachine.transition(SendState.FAILED, SendState.SENT)
    }
}