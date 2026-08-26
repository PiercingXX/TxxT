package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
