package com.piercingxx.txxt.service

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * URI grants for the telephony process that reads/writes TxxT's MMS
 * FileProvider files.
 *
 * `SmsManager.downloadMultimediaMessage` / `sendMultimediaMessage` run in
 * [MMS_SERVICE_ACTION], typically `com.android.mms.service` — not
 * `com.android.phone`. A grant that names only phone/mms/telephony never
 * reaches the writer, the dest file stays empty, and the tap path sits on
 * "Downloading…" until the waiter times out.
 */
object MmsUriGrants {

    const val MMS_SERVICE_ACTION = "android.service.mms.MmsService"

    internal val KNOWN_PACKAGES = listOf(
        "com.android.phone",
        "com.android.mms",
        "com.android.mms.service",
        "com.android.telephony",
    )

    fun grantWrite(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        packages(context).forEach { pkg ->
            try {
                context.grantUriPermission(pkg, uri, flags)
            } catch (_: Exception) {
                // Package may be absent on this device.
            }
        }
    }

    internal fun packages(context: Context): Set<String> {
        val found = LinkedHashSet(KNOWN_PACKAGES)
        try {
            @Suppress("DEPRECATION")
            context.packageManager
                .queryIntentServices(Intent(MMS_SERVICE_ACTION), 0)
                .forEach { resolve ->
                    val pkg = resolve.serviceInfo?.packageName
                    if (!pkg.isNullOrBlank()) found += pkg
                }
        } catch (_: Exception) {
            // PackageManager may be stubbed; the known list still applies.
        }
        KNOWN_PACKAGES.forEach { seed ->
            try {
                val uid = context.packageManager.getPackageUid(seed, 0)
                context.packageManager.getPackagesForUid(uid)?.forEach { found += it }
            } catch (_: Exception) {
            }
        }
        return found
    }
}
