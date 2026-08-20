package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiveStateTest {

    @Test
    fun `initial state is inbound`() {
        assertEquals(ReceiveState.INBOUND, ReceiveStateMachine.initial)
    }

    @Test
    fun `inbound sms can be received directly`() {
        assertTrue(ReceiveStateMachine.canTransition(ReceiveState.INBOUND, ReceiveState.RECEIVED))
    }

    @Test
    fun `inbound mms notice can await download or be dropped`() {
        assertTrue(
            ReceiveStateMachine.canTransition(ReceiveState.INBOUND, ReceiveState.MMS_DOWNLOAD_PENDING)
        )
        assertTrue(ReceiveStateMachine.canTransition(ReceiveState.INBOUND, ReceiveState.DROPPED))
    }

    @Test
    fun `pending mms can be downloaded or dropped`() {
        assertTrue(
            ReceiveStateMachine.canTransition(
                ReceiveState.MMS_DOWNLOAD_PENDING,
                ReceiveState.DOWNLOADING
            )
        )
        assertTrue(
            ReceiveStateMachine.canTransition(ReceiveState.MMS_DOWNLOAD_PENDING, ReceiveState.DROPPED)
        )
    }

    @Test
    fun `downloading resolves to received or failed`() {
        assertTrue(ReceiveStateMachine.canTransition(ReceiveState.DOWNLOADING, ReceiveState.RECEIVED))
        assertTrue(ReceiveStateMachine.canTransition(ReceiveState.DOWNLOADING, ReceiveState.FAILED))
    }

    @Test
    fun `failed can be retried back to downloading`() {
        assertTrue(ReceiveStateMachine.canTransition(ReceiveState.FAILED, ReceiveState.DOWNLOADING))
    }

    @Test
    fun `received and dropped are terminal`() {
        assertTrue(ReceiveStateMachine.isTerminal(ReceiveState.RECEIVED))
        assertTrue(ReceiveStateMachine.isTerminal(ReceiveState.DROPPED))
        assertFalse(ReceiveStateMachine.isTerminal(ReceiveState.INBOUND))
        assertFalse(ReceiveStateMachine.isTerminal(ReceiveState.MMS_DOWNLOAD_PENDING))
        assertFalse(ReceiveStateMachine.isTerminal(ReceiveState.DOWNLOADING))
        assertFalse(ReceiveStateMachine.isTerminal(ReceiveState.FAILED))
    }

    @Test
    fun `an inbound sms progresses inbound to received`() {
        var state = ReceiveStateMachine.transition(ReceiveState.INBOUND, ReceiveState.RECEIVED)
        assertEquals(ReceiveState.RECEIVED, state)
    }

    @Test
    fun `an inbound mms is downloaded on tap and received`() {
        var state = ReceiveStateMachine.transition(ReceiveState.INBOUND, ReceiveState.MMS_DOWNLOAD_PENDING)
        state = ReceiveStateMachine.transition(state, ReceiveState.DOWNLOADING)
        state = ReceiveStateMachine.transition(state, ReceiveState.RECEIVED)
        assertEquals(ReceiveState.RECEIVED, state)
    }

    @Test
    fun `a failed mms download can be retried and succeed`() {
        var state = ReceiveStateMachine.transition(ReceiveState.INBOUND, ReceiveState.MMS_DOWNLOAD_PENDING)
        state = ReceiveStateMachine.transition(state, ReceiveState.DOWNLOADING)
        state = ReceiveStateMachine.transition(state, ReceiveState.FAILED)
        state = ReceiveStateMachine.transition(state, ReceiveState.DOWNLOADING)
        state = ReceiveStateMachine.transition(state, ReceiveState.RECEIVED)
        assertEquals(ReceiveState.RECEIVED, state)
    }

    @Test
    fun `an inbound audio mms is dropped un-stored`() {
        var state = ReceiveStateMachine.transition(ReceiveState.INBOUND, ReceiveState.DROPPED)
        assertEquals(ReceiveState.DROPPED, state)
    }

    @Test(expected = IllegalStateException::class)
    fun `inbound cannot jump straight to downloading without a pending notice`() {
        ReceiveStateMachine.transition(ReceiveState.INBOUND, ReceiveState.DOWNLOADING)
    }

    @Test(expected = IllegalStateException::class)
    fun `pending mms cannot be received without downloading`() {
        ReceiveStateMachine.transition(ReceiveState.MMS_DOWNLOAD_PENDING, ReceiveState.RECEIVED)
    }

    @Test(expected = IllegalStateException::class)
    fun `received cannot transition onward`() {
        ReceiveStateMachine.transition(ReceiveState.RECEIVED, ReceiveState.DROPPED)
    }

    @Test(expected = IllegalStateException::class)
    fun `dropped cannot transition onward`() {
        ReceiveStateMachine.transition(ReceiveState.DROPPED, ReceiveState.RECEIVED)
    }

    @Test(expected = IllegalStateException::class)
    fun `failed cannot jump straight to received`() {
        ReceiveStateMachine.transition(ReceiveState.FAILED, ReceiveState.RECEIVED)
    }
}