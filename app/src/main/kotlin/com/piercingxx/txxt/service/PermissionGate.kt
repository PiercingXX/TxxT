package com.piercingxx.txxt.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Guards the outgoing SMS/MMS path against a missing `SEND_SMS` permission (T1).
 *
 * The send pipeline must **never silently fail to send** — if the runtime
 * `SEND_SMS` permission is denied, the gate says so (first launch included)
 * and the send is skipped rather than dropped without a trace. `SendPipeline`
 * consults [canSend] before touching `SmsManager` (`docs/PRIVACY.md` §5), so a
 * denied permission never reaches the carrier.
 *
 * The two Android-touching decisions are injectable seams (the established
 * `NotificationService` / `SmsReceiver` pattern) so the gate's behaviour is
 * drivable in a plain JVM unit test without Robolectric (not in the offline
 * cache):
 *  - [hasSendPermission] — the runtime permission check, defaulting to
 *    `ContextCompat.checkSelfPermission` against `Manifest.permission.SEND_SMS`;
 *  - [onDenied] — what the app says when the permission is missing, defaulting
 *    to a user-visible [Toast] so the denial is never silent.
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
}