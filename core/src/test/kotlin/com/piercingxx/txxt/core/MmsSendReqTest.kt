package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
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
    fun `compose includes the mandatory From insert-address token`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val pdu = MmsSendReq.compose("+15551234567", jpeg)!!
        // 0x89 From, value-length 1, 0x81 insert-address-token — AOSP PduParser
        // rejects m-send-req without From.
        var found = false
        for (i in 0..pdu.size - 3) {
            if (pdu[i] == 0x89.toByte() && pdu[i + 1] == 0x01.toByte() && pdu[i + 2] == 0x81.toByte()) {
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test
    fun `wireAddress keeps email and tags phone numbers`() {
        assertTrue(MmsSendReq.wireAddress("+15551234567")!!.endsWith("/TYPE=PLMN"))
        assertTrue(MmsSendReq.wireAddress("alice@carrier.example")!!.contains("@"))
    }

    @Test
    fun `wireAddress gives ten-digit thread keys a US country code`() {
        assertEquals("+15551234567/TYPE=PLMN", MmsSendReq.wireAddress("5551234567"))
        assertEquals("+15551234567/TYPE=PLMN", MmsSendReq.wireAddress("(555) 123-4567"))
        assertEquals("+15551234567/TYPE=PLMN", MmsSendReq.wireAddress("15551234567"))
        assertEquals("+15551234567/TYPE=PLMN", MmsSendReq.wireAddress("+15551234567"))
    }

    @Test
    fun `wireAddress does not invent a country for short codes`() {
        assertEquals("24242", MmsSendReq.wireAddress("24242"))
    }
}
