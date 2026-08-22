package com.piercingxx.txxt.service

import kotlinx.coroutines.delay

/**
 * Lifecycle of an MMS attachment download as tracked by [MmsDownloadRetry].
 *
 * The state the running thread UI can surface to the user:
 *  - [NOT_ATTEMPTED] — the attachment has not been fetched yet (MMS auto-download
 *    is off; remote content is fetched only on explicit tap, `docs/PRIVACY.md:151-153`).
 *  - [DOWNLOADING] — a fetch is in progress.
 *  - [DOWNLOADED] — the attachment has been fetched successfully.
 *  - [FAILED] — every retry attempt was exhausted; the download stays failed and
 *    is shown as such instead of silently retrying forever.
 */
enum class MmsDownloadState {
    NOT_ATTEMPTED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

/**
 * Retries a failed MMS download with exponential backoff and surfaces the failed
 * state (T4).
 *
 * MMS auto-download is off (`docs/PRIVACY.md:151-153`); an attachment is fetched
 * only on explicit tap. A tap-initiated download can fail (carrier MMS gateway
 * down, no data, transient network error), so this component retries it with an
 * exponential backoff schedule instead of dropping it, and — when every attempt
 * is exhausted — leaves it in [MmsDownloadState.FAILED] so the thread UI shows
 * the failure rather than silently giving up.
 *
 * The component is pure over its seams — [performDownload], [now], [wait] and
 * [onStateChange] — so the retry/backoff/failed-state logic is JVM-testable
 * without a device. The running call sites ([MmsReceiver]) supply the real
 * download function and surface [onStateChange] to the UI.
 */
class MmsDownloadRetry(
    /** Total attempts before the download is marked [MmsDownloadState.FAILED]. */
    private val maxAttempts: Int = 5,
    /** Delay before the first retry, in milliseconds. */
    private val baseDelayMillis: Long = 1_000L,
    /** Upper bound on the per-attempt backoff delay, in milliseconds. */
    private val maxDelayMillis: Long = 60_000L,
    /** Clock source; injectable so a test can pin the retry schedule. */
    private val now: () -> Long = System::currentTimeMillis,
    /** Pause between retry attempts; injectable so a test can skip real waiting. */
    private val wait: suspend (Long) -> Unit = { delay(it) },
    /** Fired on every state transition so the running UI can show the state. */
    private val onStateChange: (Long, MmsDownloadState) -> Unit = { _, _ -> },
    /** Performs one download attempt for [Long] (the message id); true on success. */
    private val performDownload: suspend (Long) -> Boolean,
) {

    private val states = mutableMapOf<Long, MmsDownloadState>()
    private val attempts = mutableMapOf<Long, Int>()

    /**
     * The current download state for [messageId], defaulting to [MmsDownloadState.NOT_ATTEMPTED].
     * The thread UI reads this to show the failed state.
     */
    fun state(messageId: Long): MmsDownloadState =
        states[messageId] ?: MmsDownloadState.NOT_ATTEMPTED

    /**
     * The exponential backoff delay, in milliseconds, before the attempt numbered
     * [attempt] (1-based). `baseDelay * 2^(attempt-1)`, capped at [maxDelayMillis].
     */
    fun retryDelayMillis(attempt: Int): Long {
        require(attempt >= 1) { "attempt must be >= 1, was $attempt" }
        var delayMillis = baseDelayMillis
        repeat(attempt - 1) { delayMillis = (delayMillis * 2).coerceAtMost(maxDelayMillis) }
        return delayMillis.coerceAtMost(maxDelayMillis)
    }

    /**
     * Attempts to download the MMS for [messageId], retrying on failure with
     * exponential backoff, and returns the terminal state.
     *
     * Terminal states are respected: once [MmsDownloadState.DOWNLOADED] or
     * [MmsDownloadState.FAILED], a subsequent call is a no-op returning the same
     * state (a failed download stays visible as failed until the user retries
     * explicitly). On success the state is [MmsDownloadState.DOWNLOADED]; when
     * [maxAttempts] consecutive attempts fail, the state is [MmsDownloadState.FAILED].
     */
    suspend fun download(messageId: Long): MmsDownloadState {
        val current = state(messageId)
        if (current == MmsDownloadState.DOWNLOADED || current == MmsDownloadState.FAILED) {
            return current
        }
        setState(messageId, MmsDownloadState.DOWNLOADING)

        var attempt = attempts[messageId] ?: 0
        while (attempt < maxAttempts) {
            attempt += 1
            attempts[messageId] = attempt
            if (performDownload(messageId)) {
                setState(messageId, MmsDownloadState.DOWNLOADED)
                return MmsDownloadState.DOWNLOADED
            }
            if (attempt < maxAttempts) {
                wait(retryDelayMillis(attempt))
            }
        }
        setState(messageId, MmsDownloadState.FAILED)
        return MmsDownloadState.FAILED
    }

    private fun setState(messageId: Long, state: MmsDownloadState) {
        states[messageId] = state
        onStateChange(messageId, state)
    }
}