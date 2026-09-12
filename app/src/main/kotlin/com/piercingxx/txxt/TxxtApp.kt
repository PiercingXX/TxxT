package com.piercingxx.txxt

import android.app.Application
import com.piercingxx.txxt.log.AppLog
import com.piercingxx.txxt.service.TelephonyInboxImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process entry. Installs the on-phone logger and crash dump before any
 * activity, receiver, or service runs — inbound MMS is the reason this
 * cannot wait for MainActivity.
 */
class TxxtApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.installCrashHandler()
        AppLog.i("app", "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appScope.launch {
            AppLog.i("mms", "inbox import scheduled")
            try {
                val n = TelephonyInboxImport.importPending(this@TxxtApp)
                AppLog.i("mms", "inbox import done n=$n")
            } catch (t: Throwable) {
                AppLog.w("mms", "inbox import failed", t)
            }
        }
    }
}
