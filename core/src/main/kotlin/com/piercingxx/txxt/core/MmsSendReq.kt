package com.piercingxx.txxt.core

/**
 * Minimal OMA-MMS-ENC m-send-req composer. Pure Kotlin, zero `android.*`.
 *
 * Builds a multipart-related PDU (SMIL + image + optional caption) that
 * `SmsManager.sendMultimediaMessage` can read from a `content://` URI.
 * Fail-closed: returns null when [to] has no identity or [image] is empty.
 */
object MmsSendReq {

    private const val MESSAGE_TYPE = 0x8C
    private const val MSG_SEND_REQ = 0x80
    private const val MMS_VERSION = 0x8D
    private const val VERSION_1_2 = 0x92
    private const val TO = 0x97
    private const val CONTENT_TYPE = 0x84
    private const val TRANSACTION_ID = 0x98
    private const val CT_MULTIPART_RELATED = 0xB3
    private const val PARAM_TYPE = 0x89
    private const val PARAM_START = 0x8A
    private const val LENGTH_QUOTE = 0x1F

    fun compose(
        to: String,
        image: ByteArray,
        mime: String = "image/jpeg",
        caption: String = "",
        transactionId: String = "T${System.currentTimeMillis()}",
    ): ByteArray? {
        val address = wireAddress(to) ?: return null
        if (image.isEmpty()) return null
        val filename = if (mime.contains("png", ignoreCase = true)) "image.png" else "image.jpg"
        val smil = smil(filename, caption.isNotBlank()).toByteArray(Charsets.UTF_8)
        val parts = mutableListOf(
            Part("application/smil", "smil", smil, contentId = "smil"),
            Part(mime, filename, image, contentId = "image"),
        )
        val trimmed = caption.trim()
        if (trimmed.isNotEmpty()) {
            parts += Part("text/plain", "text.txt", trimmed.toByteArray(Charsets.UTF_8), contentId = "text")
        }
        val body = encodeMultipart(parts)
        val headers = encodeHeaders(address, transactionId)
        return headers + body
    }

    fun mimeOf(image: ByteArray): String = when {
        image.size >= 8 &&
            (image[0].toInt() and 0xFF) == 0x89 &&
            image[1] == 'P'.code.toByte() -> "image/png"
        else -> "image/jpeg"
    }

    internal fun wireAddress(to: String): String? {
        val trimmed = to.trim()
        if (trimmed.isEmpty()) return null
        if ('@' in trimmed) return trimmed
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) {
            return PhoneNumbers.conversationKey(trimmed).takeIf { it.isNotEmpty() }
        }
        val withPlus = if (digits.startsWith("+")) digits else "+$digits"
        return "$withPlus/TYPE=PLMN"
    }

    internal fun smil(imageName: String, hasText: Boolean): String {
        val text = if (hasText) """<text src="text.txt" region="Text"/>""" else ""
        return """<smil><head><layout><root-layout/><region id="Image" /><region id="Text" /></layout></head><body><par><img src="$imageName" region="Image"/>$text</par></body></smil>"""
    }

    private fun encodeHeaders(to: String, transactionId: String): ByteArray {
        val out = ByteArrayOutput()
        out.octet(MESSAGE_TYPE)
        out.octet(MSG_SEND_REQ)
        out.octet(TRANSACTION_ID)
        out.textString(transactionId)
        out.octet(MMS_VERSION)
        out.octet(VERSION_1_2)
        out.octet(TO)
        out.textString(to)
        val related = relatedContentType()
        out.octet(CONTENT_TYPE)
        out.append(related)
        return out.toByteArray()
    }

    private fun relatedContentType(): ByteArray {
        val media = ByteArrayOutput()
        media.octet(CT_MULTIPART_RELATED)
        media.octet(PARAM_TYPE)
        media.textString("application/smil")
        media.octet(PARAM_START)
        media.textString("<smil>")
        val payload = media.toByteArray()
        val wrapped = ByteArrayOutput()
        wrapped.valueLength(payload.size)
        wrapped.append(payload)
        return wrapped.toByteArray()
    }

    private fun encodeMultipart(parts: List<Part>): ByteArray {
        val out = ByteArrayOutput()
        out.uintvar(parts.size)
        for (part in parts) {
            val headers = ByteArrayOutput()
            headers.textString(part.mime)
            headers.octet(0xC0) // Content-ID
            headers.quotedString("<${part.contentId}>")
            val headerBytes = headers.toByteArray()
            out.uintvar(headerBytes.size)
            out.uintvar(part.data.size)
            out.append(headerBytes)
            out.append(part.data)
        }
        return out.toByteArray()
    }

    private data class Part(
        val mime: String,
        val name: String,
        val data: ByteArray,
        val contentId: String,
    )

    private class ByteArrayOutput {
        private val buf = ArrayList<Byte>(256)
        fun octet(v: Int) { buf += (v and 0xFF).toByte() }
        fun append(bytes: ByteArray) { bytes.forEach { buf += it } }
        fun textString(s: String) {
            s.toByteArray(Charsets.US_ASCII).forEach { buf += it }
            buf += 0
        }
        fun quotedString(s: String) {
            buf += 0x22
            textString(s)
        }
        fun uintvar(value: Int) {
            require(value >= 0)
            if (value == 0) {
                octet(0)
                return
            }
            val groups = ArrayList<Int>()
            var v = value
            while (v != 0) {
                groups.add(v and 0x7F)
                v = v ushr 7
            }
            for (i in groups.indices.reversed()) {
                val flag = if (i == 0) 0 else 0x80
                octet(groups[i] or flag)
            }
        }
        fun valueLength(length: Int) {
            if (length in 0..30) {
                octet(length)
            } else {
                octet(LENGTH_QUOTE)
                uintvar(length)
            }
        }
        fun toByteArray(): ByteArray = ByteArray(buf.size) { buf[it] }
    }
}
