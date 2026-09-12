package com.piercingxx.txxt.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrievedContentParserTest {

    @Test
    fun `text plain payload becomes the body`() {
        val pdu = "headers\u0000text/plain\u0000hello from the carrier\u0000trailer"
            .toByteArray(Charsets.ISO_8859_1)
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertFalse(parsed.dropUnstored)
        assertEquals("hello from the carrier", parsed.body)
    }

    @Test
    fun `image only becomes a photo placeholder`() {
        val pdu = "application/vnd.wap.multipart.related\u0000image/jpeg\u0000"
            .toByteArray(Charsets.ISO_8859_1)
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertEquals(MmsRetrievedContent.PHOTO_PLACEHOLDER, parsed.body)
        assertFalse(parsed.dropUnstored)
    }

    @Test
    fun `audio only is dropped unstored`() {
        val pdu = "audio/amr\u0000".toByteArray(Charsets.ISO_8859_1)
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertTrue(parsed.dropUnstored)
    }

    @Test
    fun `unknown content is not invented as MMS`() {
        val pdu = "application/vnd.wap.multipart.related\u0000".toByteArray(Charsets.ISO_8859_1)
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertFalse(parsed.dropUnstored)
        assertEquals("", parsed.body)
    }

    @Test
    fun `a JPEG without an EOI is still extracted`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val parsed = MmsRetrievedContentParser.parse("image/jpeg\u0000".toByteArray() + jpeg)
        assertFalse(parsed.dropUnstored)
        assertEquals("image/jpeg", parsed.imageMime)
        assertArrayEquals(jpeg, parsed.imageBytes)
    }

    @Test
    fun `a GIF payload is extracted`() {
        val gif = "GIF89a".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x00, 0x3B)
        val parsed = MmsRetrievedContentParser.parse(gif)
        assertEquals("image/gif", parsed.imageMime)
        assertArrayEquals(gif, parsed.imageBytes)
    }

    @Test
    fun `a HEIC payload is extracted`() {
        val heic = byteArrayOf(0x00, 0x00, 0x00, 0x18) +
            "ftyp".toByteArray(Charsets.ISO_8859_1) +
            "heic".toByteArray(Charsets.ISO_8859_1) +
            ByteArray(8)
        val parsed = MmsRetrievedContentParser.parse(
            "image/heic\u0000".toByteArray(Charsets.ISO_8859_1) + heic,
        )
        assertEquals("image/heic", parsed.imageMime)
        assertEquals(MmsRetrievedContent.PHOTO_PLACEHOLDER, parsed.body)
        assertArrayEquals(heic, parsed.imageBytes)
    }

    @Test
    fun `a composed send PDU yields the JPEG and caption`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte())
        val pdu = MmsSendReq.compose("+15551234567", jpeg, caption = "hi")
        assertNotNull(pdu)
        val parsed = MmsRetrievedContentParser.parse(pdu!!)
        assertEquals("hi", parsed.body)
        assertEquals("image/jpeg", parsed.imageMime)
        assertArrayEquals(jpeg, parsed.imageBytes)
    }

    @Test
    fun `a well known jpeg part is extracted without an ASCII image slash token`() {
        // retrieve-conf: message-type, content-type well-known related, then
        // one part whose Content-Type is well-known image/jpeg (0x9D) and a JPEG body.
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte())
        val pdu = byteArrayOf(
            0x84.toByte(), // m-retrieve-conf
            0x84.toByte(), 0xB3.toByte(), // Content-Type: multipart.related
            0x01, // 1 part
            0x01, // headersLen = 1
            jpeg.size.toByte(),
            0x9D.toByte(), // well-known image/jpeg
        ) + jpeg
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertEquals("image/jpeg", parsed.imageMime)
        assertArrayEquals(jpeg, parsed.imageBytes)
        assertEquals(MmsRetrievedContent.PHOTO_PLACEHOLDER, parsed.body)
    }

    @Test
    fun `charset parameters are not used as a caption`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte())
        val parsed = MmsRetrievedContentParser.parse(
            "text/plain\u0000charset=utf-8\u0000".toByteArray(Charsets.ISO_8859_1) + jpeg,
        )
        assertEquals(MmsRetrievedContent.PHOTO_PLACEHOLDER, parsed.body)
        assertEquals("image/jpeg", parsed.imageMime)
    }

    @Test
    fun `a BMP payload is extracted`() {
        val bmp = "BM".toByteArray(Charsets.ISO_8859_1) + ByteArray(16)
        val parsed = MmsRetrievedContentParser.parse(bmp)
        assertEquals("image/bmp", parsed.imageMime)
        assertArrayEquals(bmp, parsed.imageBytes)
    }

    @Test
    fun `a video 3gpp part is treated as a photo payload`() {
        val video = ByteArray(32) { i -> (i + 1).toByte() }
        val pdu = byteArrayOf(
            0x84.toByte(),
            0x84.toByte(), 0xB3.toByte(),
            0x01,
            0x0B,
            video.size.toByte(),
        ) + "video/3gpp\u0000".toByteArray(Charsets.US_ASCII) + video
        val parsed = MmsRetrievedContentParser.parse(pdu)
        assertEquals("video/3gpp", parsed.imageMime)
        assertArrayEquals(video, parsed.imageBytes)
        assertEquals(MmsRetrievedContent.PHOTO_PLACEHOLDER, parsed.body)
    }
}
