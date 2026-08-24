package com.piercingxx.txxt.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.piercingxx.txxt.core.MetadataScrubber
import java.io.File
import java.io.IOException

/**
 * The send pipeline: gates, scrubs, then wraps [SmsManager] to send SMS and MMS.
 *
 * Consumes the T2 send policy — an outgoing SMS/MMS **never requests a delivery
 * or read report** (`docs/PRIVACY.md:23`), so the delivery/read `PendingIntent`s
 * are always `null` — and the MMS download policy (no auto-download; remote
 * content is fetched only on explicit tap, `docs/PRIVACY.md:151-153`). Every
 * send is gated by [PermissionGate] (T1): a denied `SEND_SMS` permission is
 * reported and the send is skipped, never silently dropped.
 *
 * Outgoing MMS media is **always scrubbed** before it leaves the device
 * (`docs/PRIVACY.md` metadata-scrub guarantee): [sendMms] reads the media,
 * runs it through [MetadataScrubber.scrub], and hands the platform a temporary
 * file holding the scrubbed bytes. If the media cannot be read, or its format
 * is recognized but malformed ([MetadataScrubber.scrub] returns `null`), the
 * send is aborted — corrupt input never leaves the device with metadata intact.
 */
object SendPipeline {

    /**
     * Sends a text SMS to [destination] with [body].
     *
     * The delivery-report `PendingIntent` is `null` (`SendPolicy.requestsDeliveryReport()`
     * is always `false`), so the carrier is never asked to report delivery.
     *
     * The send is gated by [PermissionGate] (T1): if the `SEND_SMS` permission is
     * denied, the gate says so and the send is skipped — an outgoing message never
     * silently fails to send. [gate] defaults to the real permission check, so the
     * running call sites (`ThreadActivity.sendComposed`, the notification quick
     * reply) reach it automatically.
     *
     * @return `true` when the platform send was attempted; `false` when the gate
     *   denied the send.
     */
    fun sendSms(
        context: Context,
        destination: String,
        body: String,
        gate: PermissionGate = PermissionGate(),
    ): Boolean {
        if (!gate.canSend(context)) return false
        // scAddress null = use the device default; sent/delivery null = never
        // request a delivery or read report (`docs/PRIVACY.md:23`).
        resolveSmsManager(context).sendTextMessage(destination, null, body, null, null)
        return true
    }

    /**
     * Sends an MMS whose body lives at [contentUri], scrubbed.
     *
     * The sent `PendingIntent` is `null` (`SendPolicy.requestsReadReport()` is
     * always `false`), so a read report is never requested. The MMS send itself
     * is initiated only on explicit user action — never automatically.
     *
     * The send is gated by [PermissionGate] (T1), exactly like [sendSms]: a
     * denied `SEND_SMS` permission reports and skips the send instead of
     * silently failing.
     *
     * The media bytes are read from [contentUri], passed through
     * [MetadataScrubber.scrub] (which strips EXIF/XMP/IPTC from images, EXIF and
     * text chunks from PNGs, GPS/device atoms from MP4/MOV — and returns `null`
     * for malformed input), and written to a temporary file in [Context.getCacheDir];
     * the platform send receives that temp URI, never the original one. The temp
     * file is deleted afterwards, including when the platform send throws.
     *
     * The four I/O steps behind the privacy-critical decisions are injectable
     * seams (the established `NotificationService` / `SmsReceiver` pattern) so
     * this pipeline's behaviour is drivable in a plain JVM unit test without
     * Robolectric:
     *  - [readUriBytes] — reads the media at [contentUri]; `null` = unreadable;
     *  - [writeTempMedia] — persists scrubbed bytes and returns their URI;
     *    `null` = the write failed;
     *  - [sendMmsPlatform] — the platform dispatch (defaults to
     *    `SmsManager.sendMultimediaMessage` with a `null` sent intent, exactly
     *    as today);
     *  - [deleteTemp] — removes the temp file.
     *
     * @return `true` when the platform send was attempted with the scrubbed temp
     *   copy; `false` when the gate denied, the media could not be read, the
     *   media was recognized-but-malformed (fail-closed abort), or the temp write
     *   failed. A thrown platform error propagates after the temp file is deleted.
     */
    fun sendMms(
        context: Context,
        contentUri: Uri,
        gate: PermissionGate = PermissionGate(),
        readUriBytes: (Uri) -> ByteArray? = { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        },
        writeTempMedia: (ByteArray) -> Uri? = { bytes ->
            try {
                val file = File.createTempFile("mms-", ".scrubbed", context.cacheDir)
                file.writeBytes(bytes)
                Uri.fromFile(file)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        },
        sendMmsPlatform: (Context, Uri) -> Unit = { ctx, uri ->
            // locationUrl null = use the device default MMSC; configOverrides
            // null = use the device default settings; sentIntent null = never
            // request a read report (`docs/PRIVACY.md:23`).
            resolveSmsManager(ctx).sendMultimediaMessage(ctx, uri, null, null, null)
        },
        deleteTemp: (Uri) -> Unit = { uri ->
            if (uri.scheme == "file") {
                uri.path?.let { path -> File(path).delete() }
            }
        },
    ): Boolean {
        if (!gate.canSend(context)) return false
        val bytes = readUriBytes(contentUri) ?: return false
        // Fail closed: a recognized-but-malformed structure must not be sent —
        // there is no guarantee its metadata is gone.
        val scrubbed = MetadataScrubber.scrub(bytes) ?: return false
        var tempUri: Uri? = null
        try {
            tempUri = writeTempMedia(scrubbed) ?: return false
            sendMmsPlatform(context, tempUri)
            return true
        } finally {
            tempUri?.let(deleteTemp)
        }
    }

    /** The platform SMS manager: the API 31+ service lookup, falling back to
     *  the deprecated static default for older OS versions (or if the lookup
     *  returns nothing). */
    private fun resolveSmsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: legacySmsManager()
        } else {
            legacySmsManager()
        }

    @Suppress("DEPRECATION")
    private fun legacySmsManager(): SmsManager = SmsManager.getDefault()
}
