package com.piercingxx.txxt.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import com.piercingxx.txxt.data.InboundStore
import com.piercingxx.txxt.data.TxxTDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The SENDTO compose entry point (default-SMS-handler role requirement).
 *
 * Other apps hand off a compose request — `ACTION_SENDTO` with an `sms:` /
 * `smsto:` / `mms:` / `mmsto:` data URI carrying the recipient — and this
 * screen's job is to land the user on the right conversation thread: resolve
 * the recipient from the URI, find or create the conversation through
 * [InboundStore] (the same store inbound delivery uses), and hand off to
 * [ThreadActivity]. There is nothing to show when the URI carries no usable
 * recipient, so that case finishes immediately.
 *
 * FLAG_SECURE is set in code (docs/PRIVACY.md §3), exactly like
 * [ThreadActivity] and [SettingsActivity], so a compose hand-off never leaks
 * into recents previews or screenshots.
 *
 * The two decisions behind the hand-off are injectable seams (the established
 * `NotificationService` / `SmsReceiver` pattern) so the behaviour stays
 * drivable without Robolectric (not in the offline cache):
 *  - [resolveRecipient] — URI to recipient; defaults to the pure
 *    [recipientFrom];
 *  - [findConversationId] — recipient to conversation id; `null` (the
 *    default) means "use the real [InboundStore]" resolved lazily inside
 *    [onCreate]'s coroutine over the Room database.
 */
class ComposeActivity(
    /**
     * Resolves the recipient address out of the SENDTO data URI. Defaults to
     * the pure [recipientFrom]; injectable so the resolution rule can be
     * driven directly.
     */
    private val resolveRecipient: (Uri) -> String? = { uri -> recipientFrom(uri) },
    /**
     * Finds (or creates) the conversation id for a resolved recipient.
     * Defaults to `null`, meaning the real [InboundStore] lookup runs lazily
     * inside [onCreate]'s coroutine (see [findConversation]); injectable so a
     * test can supply the id without touching Room.
     */
    private val findConversationId: (suspend (String) -> Long?)? = null,
) : Activity() {

    /**
     * The Room database backing the real conversation lookup. Built lazily —
     * the first touch happens inside [onCreate]'s coroutine, never during
     * construction (ThreadActivity precedent).
     */
    private val database: TxxTDatabase by lazy { TxxTDatabase.build(this) }

    /**
     * The activity-scoped coroutine scope (ThreadActivity precedent): a
     * [SupervisorJob] so one failed child cannot cancel the others and
     * [Dispatchers.Main.immediate] so UI work lands on the main thread. The
     * exception handler is the crash backstop — this activity is the most
     * attacker-influenced surface in the app (any app or web link can hand it
     * a URI), so a DB failure in the lookup must finish quietly, never crash
     * the process. The scope is cancelled in [onDestroy] so the lookup can
     * never outlive — and leak — the activity.
     */
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE in code (docs/PRIVACY.md §3): no recents preview, no screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        val recipient = intent?.data?.let(resolveRecipient)
        if (recipient == null) {
            // Nothing to open: a SENDTO with no usable recipient has no thread.
            finish()
            return
        }

        scope.launch {
            val conversationId = findConversation(recipient)
            if (conversationId != null) {
                startActivity(ThreadActivity.launchIntent(this@ComposeActivity, conversationId))
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the lookup feeding a dead activity (ThreadActivity precedent).
        scope.cancel()
    }

    /**
     * The real conversation lookup behind a `null` [findConversationId] seam:
     * [InboundStore.findOrCreateConversation] over the lazily built database's
     * conversation DAO — the same store inbound delivery persists through.
     */
    private suspend fun findConversation(address: String): Long? =
        findConversationId?.invoke(address)
            ?: InboundStore.findOrCreateConversation(database.conversationDao(), address)

    companion object {

        /**
         * Extracts the recipient address from a SENDTO data URI, purely over
         * its decoded scheme-specific part (`sms:+15551234567`,
         * `smsto:%2B15551234567`, `mms:…`, `mmsto:…` — the scheme itself never
         * changes the rule). Everything from the first `?` onward is query
         * (e.g. `sms:5551234?body=hi` from browsers and share sheets) and is
         * stripped before the number is used — AOSP messaging does the same.
         * Whitespace around the number is trimmed; a null URI or a blank
         * payload yields `null`, i.e. nothing to open. Pure — JVM-testable.
         */
        fun recipientFrom(uri: Uri?): String? =
            uri?.schemeSpecificPart
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        /** Builds the [ThreadActivity] launch intent for the resolved conversation. */
        fun launchIntentFor(context: Context, conversationId: Long): Intent =
            ThreadActivity.launchIntent(context, conversationId)
    }
}
