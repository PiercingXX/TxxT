package com.piercingxx.txxt.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Shrinks a still image so the m-send-req PDU fits the carrier MMS size cap.
 *
 * [MetadataScrubber] never re-encodes; a Pixel camera JPEG is several MB and
 * Verizon rejects it. Bytes already under [maxBytes] pass through untouched
 * so tiny JVM fixtures do not need BitmapFactory.
 */
object MmsImageFit {

    fun constrain(bytes: ByteArray, maxBytes: Int): ByteArray? {
        if (maxBytes <= 0) return bytes
        if (bytes.size <= maxBytes) return bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
            sample *= 2
        }
        var bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        try {
            var quality = 75
            var out = jpeg(bitmap, quality)
            while (out.size > maxBytes && quality > 35) {
                quality -= 10
                out = jpeg(bitmap, quality)
            }
            while (out.size > maxBytes && bitmap.width > 320) {
                val next = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.7).toInt().coerceAtLeast(320),
                    (bitmap.height * 0.7).toInt().coerceAtLeast(320),
                    true,
                )
                if (next != bitmap) bitmap.recycle()
                bitmap = next
                out = jpeg(bitmap, quality)
            }
            return out.takeIf { it.isNotEmpty() }
        } finally {
            bitmap.recycle()
        }
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val buf = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buf)
        return buf.toByteArray()
    }
}
