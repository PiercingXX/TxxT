package com.piercingxx.txxt.core

/**
 * In-app copy shown before the operator leaves (or first grants) the
 * default-SMS role.
 *
 * The system role UI is out of our hands — we cannot intercept the
 * platform picker. These strings are the honest in-app warning: TxxT
 * stores history only in `txxt.db`, and uninstalling or abandoning the
 * app drops that archive unless it was exported first.
 */
object RoleSwitchCopy {

    const val ARCHIVE_DIES =
        "TxxT keeps your messages only on this phone, in this app. " +
            "Uninstalling TxxT deletes that archive. Export first if you still need it."

    const val FIRST_RUN =
        "TxxT needs to be the default SMS app to send and receive. " +
            ARCHIVE_DIES

    const val UNSET =
        "If you stop using TxxT as the default SMS app, new messages go " +
            "elsewhere. Uninstalling TxxT deletes the local archive. " +
            "Export first if you still need it."

    const val REVOKED =
        "TxxT is no longer the default SMS app — new messages will not " +
            "arrive here. The local archive stays until you uninstall; " +
            "export first if you still need it."
}
