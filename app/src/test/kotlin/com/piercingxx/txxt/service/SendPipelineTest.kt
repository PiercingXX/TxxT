package com.piercingxx.txxt.service

import android.content.Context
import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Behaviour-verifies `SendPipeline`'s privacy-critical send path.
 *
 * Two guarantees are exercised:
 *
 *  1. **No delivery/read reports** (T3): the pipeline always passes `null`
 *     `PendingIntent`s to `SmsManager` because
 *     `SendPolicy.requestsDeliveryReport()` / `requestsReadReport()` are always
 *     `false` (`docs/PRIVACY.md:23,32`).
 *  2. **MMS media is always scrubbed** (T2 wiring): [SendPipeline.sendMms] reads
 *     the media, runs it through `MetadataScrubber.scrub`, writes the scrubbed
 *     bytes to a temporary file, and hands the platform that temp URI — never
 *     the original one. A failed read, a corrupt/scrub-null payload, or a failed
 *     temp write aborts the send (fail-closed), and the temp file is deleted
 *     even when the platform send throws.
 *
 * The Android `SmsManager` / content resolver dispatch itself is not
 * JVM-testable without Robolectric (not in the offline cache — see the plan's
 * deferred verification), so everything runs through the pipeline's injectable
 * seams ([readUriBytes], [writeTempMedia], [sendMmsPlatform], [deleteTemp]),
 * mirroring the established `PermissionGateTest` seam style.
 */
class SendPipelineTest {

    private val context: Context = mockk(relaxed = true)
    private val originalUri: Uri = mockk()
    private val tempUri: Uri = mockk()

    private fun allowGate(): PermissionGate =
        PermissionGate(hasSendPermission = { _ -> true }, onDenied = { _ -> })

    private fun denyGate(): PermissionGate =
        PermissionGate(hasSendPermission = { _ -> false }, onDenied = { _ -> })

    // --- SMS policy --------------------------------------------------------

    @Test
    fun `an outgoing SMS never requests a delivery report`() {
        // SendPipeline.sendSms passes a null delivery PendingIntent because the
        // policy never requests a delivery report (docs/PRIVACY.md:23).
        assertFalse(SendPolicy.requestsDeliveryReport())
    }

    @Test
    fun `an outgoing MMS never requests a read report`() {
        // SendPipeline.sendMms passes a null sent PendingIntent because the
        // policy never requests a read report (docs/PRIVACY.md:23).
        assertFalse(SendPolicy.requestsReadReport())
    }

    // --- MMS gating ----------------------------------------------------------

    @Test
    fun `gate-denied mms returns false without reading or sending anything`() {
        var reads = 0

        val result = SendPipeline.sendMms(
            context,
            originalUri,
            denyGate(),
            readUriBytes = { _ -> reads++; fail("media must not be read when the gate denies"); null },
            writeTempMedia = { _ -> fail("temp must not be written when the gate denies"); null },
            sendMmsPlatform = { _, _ -> fail("platform must not be called when the gate denies") },
            deleteTemp = { _ -> fail("nothing to delete when the gate denies") },
        )

        assertFalse(result)
        assertEquals(0, reads)
    }

    // --- MMS happy paths -------------------------------------------------------

    @Test
    fun `mms sends the scrubbed temp uri, never the original uri`() {
        // GIF-header bytes with a non-printable "atom type" region so every
        // format detector declines: the scrubber passes them through unchanged,
        // proving the uniform temp-file path (unrecognized media still goes
        // through it).
        val cleanBytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00) +
            "no-metadata-here".toByteArray()
        val written = mutableListOf<ByteArray>()
        var platformReceived: Uri? = null
        val deleted = mutableListOf<Uri>()

        val result = SendPipeline.sendMms(
            context,
            originalUri,
            allowGate(),
            readUriBytes = { uri ->
                assertSame(originalUri, uri)
                cleanBytes
            },
            writeTempMedia = { bytes -> written += bytes; tempUri },
            sendMmsPlatform = { _, uri -> platformReceived = uri },
            deleteTemp = { deleted += it },
        )

        assertTrue(result)
        assertSame("the platform must receive the temp uri", tempUri, platformReceived)
        assertNotSame("the original uri must never reach the platform", originalUri, platformReceived)
        assertArrayEquals(cleanBytes, written.single())
        assertEquals(listOf(tempUri), deleted)
    }

    @Test
    fun `exif-bearing jpeg media is scrubbed before being handed off`() {
        val exifPayload = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00,
            0x40, 0x71, 0x28, // GPS-ish payload bytes
        )
        val inputJpeg = jpegWithExif(exifPayload)
        val scanPayload = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        lateinit var writtenBytes: ByteArray
        var platformReceived: Uri? = null

        val result = SendPipeline.sendMms(
            context,
            originalUri,
            allowGate(),
            readUriBytes = { _ -> inputJpeg },
            writeTempMedia = { bytes -> writtenBytes = bytes; tempUri },
            sendMmsPlatform = { _, uri -> platformReceived = uri },
            deleteTemp = { _ -> },
        )

        assertTrue(result)
        assertSame(tempUri, platformReceived)
        // The APP1 EXIF segment is gone from exactly what gets written...
        assertFalse(contains(writtenBytes, exifPayload))
        // ...and the image data survives untouched.
        assertTrue(contains(writtenBytes, scanPayload))
    }

    // --- MMS fail-closed aborts ---------------------------------------------

    @Test
    fun `scrub-null corrupt jpeg aborts before any send or temp write`() {
        // SOI followed by garbage: MetadataScrubber returns null (fail-closed).
        val corruptJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42, 0x13, 0x37)

        val result = SendPipeline.sendMms(
            context,
            originalUri,
            allowGate(),
            readUriBytes = { _ -> corruptJpeg },
            writeTempMedia = { _ -> fail("temp must never be written from corrupt media"); null },
            sendMmsPlatform = { _, _ -> fail("corrupt media must never be sent") },
            deleteTemp = { _ -> fail("nothing was written, nothing to delete") },
        )

        assertFalse(result)
    }

    @Test
    fun `unreadable media aborts the mms send`() {
        val result = SendPipeline.sendMms(
            context,
            originalUri,
            allowGate(),
            readUriBytes = { _ -> null },
            writeTempMedia = { _ -> fail("temp must not be written when the read fails"); null },
            sendMmsPlatform = { _, _ -> fail("platform must not be called when the read fails") },
            deleteTemp = { _ -> fail("nothing to delete when the read fails") },
        )

        assertFalse(result)
    }

    @Test
    fun `failed temp write aborts the mms send`() {
        val result = SendPipeline.sendMms(
            context,
            originalUri,
            allowGate(),
            readUriBytes = { _ -> "GIF89a".toByteArray() },
            writeTempMedia = { _ -> null }, // the cache write failed
            sendMmsPlatform = { _, _ -> fail("platform must not be called without a scrubbed copy") },
            deleteTemp = { _ -> fail("nothing was written, nothing to delete") },
        )

        assertFalse(result)
    }

    @Test
    fun `temp file is deleted even when the platform send throws`() {
        val deleted = mutableListOf<Uri>()

        try {
            SendPipeline.sendMms(
                context,
                originalUri,
                allowGate(),
                readUriBytes = { _ -> "GIF89a".toByteArray() },
                writeTempMedia = { _ -> tempUri },
                sendMmsPlatform = { _, _ -> throw IllegalStateException("simulated platform failure") },
                deleteTemp = { deleted += it },
            )
            fail("a platform error must propagate to the caller")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(listOf(tempUri), deleted)
    }

    // --- helpers -------------------------------------------------------------

    /** A minimal EXIF-carrying JPEG: SOI, APP1(EXIF), SOS header, scan, EOI. */
    private fun jpegWithExif(exifPayload: ByteArray): ByteArray {
        val length = 2 + exifPayload.size
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte()) +
            byteArrayOf(((length shr 8) and 0xFF).toByte(), (length and 0xFF).toByte()) +
            exifPayload
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app1 +
            byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02) + // SOS, no header payload
            byteArrayOf(0x11, 0x22, 0x33, 0x44) +          // entropy-coded scan stand-in
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())               // EOI
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
