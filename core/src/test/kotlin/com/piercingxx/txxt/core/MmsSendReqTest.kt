package com.piercingxx.txxt.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendReqTest {

    @Test
    fun `compose rejects an empty destination and empty image`() {
        assertNull(MmsSendReq.compose("", byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(MmsSendReq.compose("+15551234567", byteArrayOf()))
    }

    @Test
    fun `compose embeds the destination and the image bytes`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val pdu = MmsSendReq.compose("+15551234567", jpeg, caption = "hi")
        assertNotNull(pdu)
        val ascii = pdu!!.toString(Charsets.ISO_8859_1)
        assertTrue(ascii.contains("15551234567"))
        assertTrue(ascii.contains("hi"))
        assertTrue(pdu.indexOf(jpeg[0]) >= 0)
    }

    @Test
    fun `wireAddress keeps email and tags phone numbers`() {
        assertTrue(MmsSendReq.wireAddress("+15551234567")!!.endsWith("/TYPE=PLMN"))
        assertTrue(MmsSendReq.wireAddress("alice@carrier.example")!!.contains("@"))
    }
}
