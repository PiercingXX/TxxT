package com.piercingxx.txxt

import android.app.Application
import com.piercingxx.txxt.log.AppLog

/**
 * Process entry. Installs the on-phone logger and crash dump before any
 * activity, receiver, or service runs — inbound MMS is the reason this
 * cannot wait for MainActivity.
 */
class TxxtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.installCrashHandler()
        AppLog.i("app", "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
