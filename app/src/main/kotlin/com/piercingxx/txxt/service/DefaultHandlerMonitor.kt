package com.piercingxx.txxt.service

import android.content.Context
import android.provider.Telephony
import android.widget.Toast

/**
 * Monitors the default-SMS-handler role and warns loudly when it is revoked (T6).
 *
 * The app's whole reason to exist is being the default SMS handler — it declares
 * `android.permission.role.SMS` (`app/src/main/AndroidManifest.xml:7`) and the
 * `SmsReceiver` / `MmsReceiver` components complete that role. If the role is
 * revoked (the user switches the default SMS app, or a factory reset / app update
 * drops the grant), the receivers no longer see inbound SMS/MMS and the app
 * silently stops working. This monitor makes that revocation **loud** instead of
 * silent: [warnIfRevoked] checks the platform's current default-SMS package and,
 * when it is no longer this app, surfaces a user-visible warning.
 *
 * The two Android-touching decisions are injectable seams (the established
 * `PermissionGate` / `NotificationService` pattern) so the monitor's behaviour is
 * drivable in a plain JVM unit test without Robolectric (not in the offline cache):
 *  - [defaultSmsPackage] — the platform's current default-SMS package, defaulting
 *    to `Telephony.Sms.getDefaultSmsPackage`;
 *  - [onRevoked] — what the app says when the role is gone, defaulting to a
 *    user-visible [Toast] so the revocation is never silent.
 */
class DefaultHandlerMonitor(
    /**
     * The platform's current default-SMS package name, or `null` when no app is
     * the default. Defaults to the real `Telephony.Sms.getDefaultSmsPackage`.
     */
    private val defaultSmsPackage: (Context) -> String? = { context ->
        Telephony.Sms.getDefaultSmsPackage(context)
    },
    /**
     * Called exactly once when [warnIfRevoked] finds this app is no longer the
     * default SMS handler, so the revocation is surfaced instead of silently
     * swallowed. Defaults to a user-visible [Toast].
     */
    private val onRevoked: (Context) -> Unit = { context ->
        Toast.makeText(
            context,
            "TxxT is no longer the default SMS app — messages may not be received.",
            Toast.LENGTH_LONG,
        ).show()
    },
) {

    /**
     * Whether this app is currently the default SMS handler.
     *
     * Returns `true` when the platform's default-SMS package is this app's
     * package name. When it is not (the role was revoked), calls [onRevoked]
     * (so the app "warns loudly") and returns `false`.
     */
    fun warnIfRevoked(context: Context): Boolean {
        val default = defaultSmsPackage(context)
        val isDefault = default == context.packageName
        if (!isDefault) onRevoked(context)
        return isDefault
    }
}