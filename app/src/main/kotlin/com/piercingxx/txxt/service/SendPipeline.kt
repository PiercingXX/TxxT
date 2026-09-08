package com.piercingxx.txxt.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.piercingxx.txxt.core.MetadataScrubber
import com.piercingxx.txxt.core.MmsSendReq
import com.piercingxx.txxt.log.AppLog
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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
        val manager = resolveSmsManager(context)
        val parts = manager.divideMessage(body)
        if (parts != null && parts.size > 1) {
            // A body over one SMS segment must go through the multipart API —
            // sendTextMessage silently fails at the radio layer for it.
            manager.sendMultipartTextMessage(destination, null, parts, null, null)
        } else {
            manager.sendTextMessage(destination, null, body, null, null)
        }
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
     * for malformed input), composed into an m-send-req, and written to a
     * FileProvider cache file. GrapheneOS 17 treats any `content://mms/…`
     * URI as a persisted message and rebuilds it with PduComposer; that
     * crashed on our outbox rows. FileProvider is read as raw PDU bytes.
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
        destination: String = "",
        caption: String = "",
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
            writePduFile(context, bytes)
        },
        sendMmsPlatform: (Context, Uri) -> Unit = { ctx, uri ->
            // locationUrl / configOverrides null = device defaults. The sent
            // PendingIntent is a local completion signal, not a carrier
            // delivery or read report (`docs/PRIVACY.md:23`).
            if (!sendAndAwait(ctx, uri)) {
                throw IOException("MMS send was not accepted")
            }
        },
        deleteTemp: (Uri) -> Unit = { },
        composePdu: (String, ByteArray, String) -> ByteArray? = { to, image, text ->
            MmsSendReq.compose(to, image, MmsSendReq.mimeOf(image), text)
        },
    ): Boolean {
        if (!gate.canSend(context)) return false
        val bytes = readUriBytes(contentUri) ?: run {
            AppLog.w("send", "mms unreadable uri")
            return false
        }
        val scrubbed = MetadataScrubber.scrub(bytes) ?: run {
            AppLog.w("send", "mms scrub rejected")
            return false
        }
        val image = if (scrubbed.size <= DEFAULT_MMS_MAX_BYTES) {
            scrubbed
        } else {
            MmsImageFit.constrain(scrubbed, maxImageBytes(context)) ?: run {
                AppLog.w("send", "mms image too large bytes=${scrubbed.size}")
                return false
            }
        }
        val payload = if (destination.isNotBlank()) {
            composePdu(destination, image, caption) ?: run {
                AppLog.w("send", "mms compose failed destLen=${destination.length}")
                return false
            }
        } else {
            image
        }
        var tempUri: Uri? = null
        try {
            tempUri = writeTempMedia(payload) ?: return false
            sendMmsPlatform(context, tempUri)
            return true
        } finally {
            tempUri?.let(deleteTemp)
        }
    }

    private fun writePduFile(context: Context, bytes: ByteArray): Uri? {
        return try {
            val dir = File(context.cacheDir, "mms")
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File.createTempFile("send-", ".pdu", dir)
            file.writeBytes(bytes)
            val dest = FileProvider.getUriForFile(
                context,
                "${context.packageName}.mms",
                file,
            )
            MmsUriGrants.grantWrite(context, dest)
            Log.i("TxxT-Mms", "send dest $dest (${bytes.size} bytes)")
            dest
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Hands the FileProvider PDU to [SmsManager.sendMultimediaMessage] and
     * waits for the local sent broadcast. Not a carrier delivery report.
     */
    private fun sendAndAwait(context: Context, uri: Uri): Boolean {
        val requestId = (System.nanoTime() and 0x7FFFFFFF).toInt()
        val waiter = MmsSentWaiters.register(requestId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val pending = PendingIntent.getBroadcast(
            context,
            requestId,
            Intent(context, MmsSentReceiver::class.java)
                .setAction(MmsSentReceiver.ACTION)
                .setData(Uri.parse("txxt-mms://send/$requestId"))
                .putExtra(MmsSentReceiver.EXTRA_REQUEST_ID, requestId),
            flags,
        )
        MmsUriGrants.grantWrite(context, uri)
        return try {
            resolveSmsManager(context).sendMultimediaMessage(context, uri, null, null, pending)
            runBlocking {
                withTimeoutOrNull(SEND_TIMEOUT_MS) { waiter.await() }
            } ?: false
        } catch (t: Exception) {
            Log.w("TxxT-Mms", "platform send failed", t)
            AppLog.e("send", "mms platform send failed", t)
            MmsSentWaiters.complete(requestId, false)
            false
        }
    }

    /** Carrier max PDU minus a small header/SMIL allowance. */
    internal fun maxImageBytes(context: Context): Int {
        val cap = try {
            resolveSmsManager(context).carrierConfigValues
                ?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE) ?: 0
        } catch (_: Exception) {
            0
        }
        val max = if (cap > 32_000) cap else DEFAULT_MMS_MAX_BYTES
        return (max - 24_000).coerceAtLeast(80_000)
    }

    /**
     * SmsManager for the **default SMS subscription**, not an arbitrary SIM.
     * Dual-SIM Pixels otherwise silently send on the wrong radio.
     */
    internal fun resolveSmsManager(context: Context): SmsManager {
        val subId = defaultSmsSubscriptionId()
        if (subId != INVALID_SUBSCRIPTION_ID) {
            @Suppress("DEPRECATION")
            val forSub = SmsManager.getSmsManagerForSubscriptionId(subId)
            if (forSub != null) return forSub
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: legacySmsManager()
        } else {
            legacySmsManager()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySmsManager(): SmsManager = SmsManager.getDefault()

    /** Exposed so a JVM test can lock the dual-SIM choice without telephony. */
    internal fun defaultSmsSubscriptionId(): Int = try {
        SmsManager.getDefaultSmsSubscriptionId()
    } catch (_: Throwable) {
        INVALID_SUBSCRIPTION_ID
    }

    private const val INVALID_SUBSCRIPTION_ID = -1
    private const val DEFAULT_MMS_MAX_BYTES = 300_000
    private const val SEND_TIMEOUT_MS = 90_000L
}

internal object MmsSentWaiters {
    private val waiters = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    fun register(requestId: Int): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        waiters[requestId] = deferred
        return deferred
    }

    fun complete(requestId: Int, ok: Boolean) {
        waiters.remove(requestId)?.complete(ok)
    }
}

/** Completes the waiter for one `sendMultimediaMessage` request. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1).takeIf { it >= 0 }
            ?: intent.data?.lastPathSegment?.toIntOrNull()
            ?: -1
        val conf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val http = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1)
        val ok = resultCode == Activity.RESULT_OK && sendConfAccepted(conf)
        Log.i(
            "TxxT-Mms",
            "sent result=$resultCode http=$http conf=${conf?.joinToString("") { "%02x".format(it) } ?: "none"} ok=$ok",
        )
        MmsSentWaiters.complete(requestId, ok)
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_mms_sent_request_id"
        const val ACTION = "com.piercingxx.txxt.action.MMS_SENT"

        /**
         * True when the MMSC send-conf is missing or reports Response-Status
         * OK (0x80). Must not treat MMS-Version 1.2 (field 0x8D, value 0x92)
         * as Response-Status.
         */
        internal fun sendConfAccepted(conf: ByteArray?): Boolean {
            if (conf == null || conf.isEmpty()) return true
            var i = 0
            var status: Int? = null
            while (i < conf.size) {
                val field = conf[i].toInt() and 0xFF
                i++
                if (field < 0x80) continue
                when (field) {
                    0x92 -> {
                        if (i >= conf.size) return false
                        status = conf[i].toInt() and 0xFF
                        i++
                    }
                    0x8C, 0x8D, 0x86, 0x90, 0x91 -> {
                        if (i < conf.size) i++
                    }
                    0x98, 0x8B, 0x83 -> {
                        while (i < conf.size && conf[i] != 0.toByte()) i++
                        if (i < conf.size) i++
                    }
                    else -> {
                        if (i >= conf.size) break
                        val next = conf[i].toInt() and 0xFF
                        if (next >= 0x80) {
                            i++
                        } else {
                            while (i < conf.size && conf[i] != 0.toByte()) i++
                            if (i < conf.size) i++
                        }
                    }
                }
            }
            return status == null || status == 0x80
        }
    }
}
