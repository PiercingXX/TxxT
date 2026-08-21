package com.piercingxx.txxt.service

import com.piercingxx.txxt.service.ReceivePolicy.AttachmentDecision
import com.piercingxx.txxt.service.ReceivePolicy.AutoReplyOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivePolicyTest {

    @Test
    fun `an audio attachment yields a drop-un-stored decision`() {
        assertEquals(AttachmentDecision.DROP_UNSTORED, ReceivePolicy.decideAttachment("audio/mp4"))
        assertEquals(AttachmentDecision.DROP_UNSTORED, ReceivePolicy.decideAttachment("audio/3gpp"))
        assertEquals(AttachmentDecision.DROP_UNSTORED, ReceivePolicy.decideAttachment("audio/amr"))
    }

    @Test
    fun `a non-audio attachment yields a store decision`() {
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment("image/jpeg"))
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment("text/plain"))
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment("video/mp4"))
    }

    @Test
    fun `an unknown or absent content type is stored, not dropped`() {
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment(null))
        assertEquals(AttachmentDecision.STORE, ReceivePolicy.decideAttachment("application/octet-stream"))
    }

    @Test
    fun `the auto-reply is a no-op when the setting is off by default`() {
        assertFalse(ReceivePolicy.shouldAutoReply(enabled = false, sender = "alice"))
        assertFalse(
            ReceivePolicy.shouldAutoReply(
                enabled = false,
                sender = "alice",
                overrides = mapOf("alice" to AutoReplyOverride.ON),
            )
        )
    }

    @Test
    fun `the auto-reply fires when enabled`() {
        assertTrue(ReceivePolicy.shouldAutoReply(enabled = true, sender = "alice"))
        assertTrue(
            ReceivePolicy.shouldAutoReply(
                enabled = true,
                sender = "alice",
                overrides = mapOf("alice" to AutoReplyOverride.ON),
            )
        )
    }

    @Test
    fun `a per-contact override turns it on for one sender and off for another`() {
        val overrides = mapOf(
            "alice" to AutoReplyOverride.ON,
            "bob" to AutoReplyOverride.OFF,
        )
        assertTrue(ReceivePolicy.shouldAutoReply(enabled = true, sender = "alice", overrides = overrides))
        assertFalse(ReceivePolicy.shouldAutoReply(enabled = true, sender = "bob", overrides = overrides))
    }

    @Test
    fun `the auto-reply body matches the privacy spec`() {
        assertEquals(
            "Voice messages aren't accepted. Send text or a photo.",
            ReceivePolicy.AUTO_REPLY_BODY,
        )
    }
}