package com.piercingxx.txxt.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.widget.Toast

/**
 * Monitors the default-SMS-handler role: warns loudly when it is revoked (T6)
 * and builds the platform request that asks for it.
 *
 * The app's whole reason to exist is being the default SMS handler — it declares
 * `android.permission.role.SMS` (`app/src/main/AndroidManifest.xml:12`) and the
 * `SmsReceiver` / `MmsReceiver` components complete that role. If the role is
 * revoked (the user switches the default SMS app, or a factory reset / app update
 * drops the grant), the receivers no longer see inbound SMS/MMS and the app
 * silently stops working. This monitor makes that revocation **loud** instead of
 * silent: [warnIfRevoked] checks the platform's current default-SMS package and,
 * when it is no longer this app, surfaces a user-visible warning.
 *
 * The Android-touching decisions are injectable seams (the established
 * `PermissionGate` / `NotificationService` pattern) so the monitor's behaviour is
 * drivable in a plain JVM unit test without Robolectric (not in the offline cache):
 *  - [defaultSmsPackage] — the platform's current default-SMS package, defaulting
 *    to `Telephony.Sms.getDefaultSmsPackage`;
 *  - [onRevoked] — what the app says when the role is gone, defaulting to a
 *    user-visible [Toast] so the revocation is never silent;
 *  - [roleRequestIntent] — the system intent that asks the user to grant the
 *    role (see below).
 *
 * [roleRequestIntent]'s default implementation is honest about what the platform
 * offers: on API 29+ it uses `RoleManager` to build a `createRequestRoleIntent`
 * request — but only while the role is available **and not already held**, so a
 * held or unavailable role yields `null` (nothing to ask for). On API <29 there
 * is no RoleManager at all, so it returns `null`: there is no programmatic path,
 * and the user must grant the default-handler role manually through system
 * settings (Settings → Apps → Default apps → SMS app). Note that because the
 * "not held" check lives inside the seam, the caller re-prompts on every launch
 * while the role stays unheld — re-prompting after a denial is accepted here:
 * TxxT is a sideloaded single-user app, not a store-distributed one.
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
     * Whether [RoleManager.ROLE_SMS] is held, or `null` when RoleManager is
     * not the authority (API <29). On current GrapheneOS the role is the
     * source of truth; `Telephony.Sms.getDefaultSmsPackage` still reads the
     * old `SMS_DEFAULT_APPLICATION` setting, which can be null while the
     * role is held — that is why the in-app banner lied.
     */
    private val roleHeld: (Context) -> Boolean? = { context ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            null
        }
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
    /**
     * The system intent that asks the user to grant the default-SMS-handler
     * role, or `null` when there is nothing to request (role already held,
     * role unavailable, or API <29 where no `RoleManager` exists — manual
     * grant through system settings). Defaults to the real `RoleManager`
     * request on API 29+.
     */
    private val roleRequestIntent: (Context) -> Intent? = { context ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                null
            }
        } else {
            // No RoleManager below API 29: the user grants the role manually.
            null
        }
    },
) {

    /**
     * Whether this app is currently the default SMS handler.
     *
     * RoleManager is authoritative on API 29+. The legacy default-SMS package
     * is only consulted when RoleManager is absent (API <29) or returned
     * nothing.
     */
    fun isHeld(context: Context): Boolean {
        roleHeld(context)?.let { return it }
        return defaultSmsPackage(context) == context.packageName
    }

    /**
     * Whether this app is currently the default SMS handler.
     *
     * Returns `true` when the role is held. When it is not, calls [onRevoked]
     * (so the app "warns loudly") and returns `false`.
     */
    fun warnIfRevoked(context: Context): Boolean {
        val held = isHeld(context)
        if (!held) onRevoked(context)
        return held
    }

    /**
     * The intent to start so the user can grant the default-SMS-handler role,
     * or `null` when there is nothing to request — the role is already held,
     * it is unavailable on this device, or the device runs API <29 where no
     * `RoleManager` exists (manual grant through system settings).
     *
     * Side-effect free: the platform dialog only appears when the caller
     * actually starts the returned intent. While the role stays unheld this
     * returns a fresh request on every call, so a caller that asks on every
     * launch re-prompts after a denial — accepted for a sideloaded
     * single-user app.
     */
    fun roleRequest(context: Context): Intent? = roleRequestIntent(context)
}