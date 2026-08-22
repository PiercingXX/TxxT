package com.piercingxx.txxt

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.piercingxx.txxt.ui.ThreadActivity

/**
 * Launcher activity for TxxT.
 *
 * Declared in the manifest with the MAIN/LAUNCHER intent-filter
 * (`app/src/main/AndroidManifest.xml:36-44`). FLAG_SECURE is set in code
 * (docs/PRIVACY.md §3) so the launcher never appears in recents previews or
 * screenshots. With the conversation list (WS10) not yet landed, the launcher
 * opens the thread screen directly — the conversation list will pass the real
 * conversation id when it arrives.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        startActivity(
            Intent(this, ThreadActivity::class.java)
                .putExtra("extra_conversation_id", 1L)
        )
    }
}
