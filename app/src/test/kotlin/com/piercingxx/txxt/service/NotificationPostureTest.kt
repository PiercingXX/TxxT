package com.piercingxx.txxt.service

import com.piercingxx.txxt.service.NotificationPosture.Override
import com.piercingxx.txxt.service.NotificationPosture.Posture
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPostureTest {

    @Test
    fun `starred contacts always get NOTIFY regardless of global posture`() {
        assertEquals(
            Posture.NOTIFY,
            NotificationPosture.decide(
                sender = "alice",
                starred = true,
                globalPosture = Posture.SUPPRESS,
            ),
        )
        assertEquals(
            Posture.NOTIFY,
            NotificationPosture.decide(
                sender = "alice",
                starred = true,
                globalPosture = Posture.REDACTED,
            ),
        )
    }

    @Test
    fun `starred contacts always get NOTIFY even with a per-contact override`() {
        assertEquals(
            Posture.NOTIFY,
            NotificationPosture.decide(
                sender = "alice",
                starred = true,
                globalPosture = Posture.REDACTED,
                overrides = mapOf("alice" to Override.SUPPRESS),
            ),
        )
    }

    @Test
    fun `per-contact override beats the global posture for unstarred senders`() {
        val overrides = mapOf(
            "alice" to Override.NOTIFY,
            "bob" to Override.SUPPRESS,
        )
        assertEquals(
            Posture.NOTIFY,
            NotificationPosture.decide(
                sender = "alice",
                starred = false,
                globalPosture = Posture.REDACTED,
                overrides = overrides,
            ),
        )
        assertEquals(
            Posture.SUPPRESS,
            NotificationPosture.decide(
                sender = "bob",
                starred = false,
                globalPosture = Posture.REDACTED,
                overrides = overrides,
            ),
        )
    }

    @Test
    fun `unstarred senders without an override fall back to the global posture`() {
        assertEquals(
            Posture.REDACTED,
            NotificationPosture.decide(
                sender = "charlie",
                starred = false,
                globalPosture = Posture.REDACTED,
            ),
        )
        assertEquals(
            Posture.SUPPRESS,
            NotificationPosture.decide(
                sender = "charlie",
                starred = false,
                globalPosture = Posture.SUPPRESS,
            ),
        )
    }

    @Test
    fun `sender not in the override map uses the global posture`() {
        assertEquals(
            Posture.REDACTED,
            NotificationPosture.decide(
                sender = "unknown",
                starred = false,
                globalPosture = Posture.REDACTED,
                overrides = mapOf("alice" to Override.NOTIFY),
            ),
        )
    }
}
