package com.piercingxx.txxt.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Guards the outgoing SMS/MMS path against a missing `SEND_SMS` permission (T1)
 * and dictation against a missing `RECORD_AUDIO` permission.
 *
 * The send pipeline must **never silently fail to send** — if the runtime
 * `SEND_SMS` permission is denied, the gate says so (first launch included)
 * and the send is skipped rather than dropped without a trace. `SendPipeline`
 * consults [canSend] before touching `SmsManager` (`docs/PRIVACY.md` §5), so a
 * denied permission never reaches the carrier. The same never-silent rule holds
 * for dictation: [canRecord] reports a missing mic permission so the caller can
 * request the permission instead of listening into silence. The same rule holds
 * on the inbound side: [canNotify] reports a missing `POST_NOTIFICATIONS`
 * permission so the deliver receivers know an arrival will be
 * silent-by-permission instead of posting into the void.
 *
 * The Android-touching decisions are injectable seams (the established
 * `NotificationService` / `SmsReceiver` pattern) so the gate's behaviour is
 * drivable in a plain JVM unit test without Robolectric (not in the offline
 * cache):
 *  - [hasSendPermission] / [hasRecordPermission] / [hasNotifyPermission] — the
 *    runtime permission checks, defaulting to `ContextCompat.checkSelfPermission`
 *    against `Manifest.permission.SEND_SMS` / `Manifest.permission.RECORD_AUDIO` /
 *    `Manifest.permission.POST_NOTIFICATIONS`;
 *  - [onDenied] / [onRecordDenied] / [onNotifyDenied] — what the app says when
 *    the respective permission is missing, defaulting to a user-visible [Toast]
 *    so the denial is never silent.
 */
class PermissionGate(
    /**
     * Whether the app currently holds the `SEND_SMS` runtime permission.
     * Defaults to the real platform check.
     */
    private val hasSendPermission: (Context) -> Boolean = { context ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    },
    /**
     * Called exactly once when [canSend] finds the permission denied, so the
     * denial is surfaced instead of silently swallowed. Defaults to a
     * user-visible [Toast].
     */
    private val onDenied: (Context) -> Unit = { context ->
        Toast.makeText(
            context,
            "SMS permission is off — allow it to send messages.",
            Toast.LENGTH_LONG,
        ).show()
    },
    /**
     * Whether the app currently holds the `RECORD_AUDIO` runtime permission.
     * Defaults to the real platform check.
     */
    private val hasRecordPermission: (Context) -> Boolean = { context ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    },
    /**
     * Called exactly once when [canRecord] finds the permission denied, so the
     * denial is surfaced instead of silently swallowed. Defaults to a
     * user-visible [Toast].
     */
    private val onRecordDenied: (Context) -> Unit = { context ->
        Toast.makeText(
            context,
            "Mic permission is off — allow it to dictate.",
            Toast.LENGTH_LONG,
        ).show()
    },
    /**
     * Whether the app may post notifications right now. `POST_NOTIFICATIONS`
     * exists only from API 33 (Tiramisu); below that the check is trivially
     * granted — notifications are governed by the system-wide app toggle, not
     * a runtime permission. Injectable for JVM tests.
     */
    private val hasNotifyPermission: (Context) -> Boolean = { context ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
    },
    /**
     * Called exactly once when [canNotify] finds the permission denied, so the
     * denial is surfaced instead of silently swallowed. Defaults to a
     * user-visible [Toast].
     */
    private val onNotifyDenied: (Context) -> Unit = { context ->
        Toast.makeText(
            context,
            "Notification permission is off — messages arrive silently.",
            Toast.LENGTH_LONG,
        ).show()
    },
) {

    /**
     * Whether an outgoing message may be sent right now.
     *
     * Returns `true` when the `SEND_SMS` permission is granted. When it is
     * denied, calls [onDenied] (so the app "says so") and returns `false` — the
     * caller must not proceed to send.
     */
    fun canSend(context: Context): Boolean {
        val granted = hasSendPermission(context)
        if (!granted) onDenied(context)
        return granted
    }

    /**
     * Whether speech recognition may start right now.
     *
     * Returns `true` when the `RECORD_AUDIO` permission is granted. When it is
     * denied, calls [onRecordDenied] (so the app "says so") and returns `false`
     * — the caller must request the permission instead of listening silently.
     */
    fun canRecord(context: Context): Boolean {
        val granted = hasRecordPermission(context)
        if (!granted) onRecordDenied(context)
        return granted
    }

    /**
     * Whether an arrival notification may be posted right now.
     *
     * Returns `true` when `POST_NOTIFICATIONS` is granted — which is always
     * the case below API 33, where the permission does not exist. When it is
     * denied (API 33+), calls [onNotifyDenied] (so the app "says so") and
     * returns `false` — the caller must skip posting; delivery then continues,
     * silently-by-permission.
     */
    fun canNotify(context: Context): Boolean {
        val granted = hasNotifyPermission(context)
        if (!granted) onNotifyDenied(context)
        return granted
    }
}