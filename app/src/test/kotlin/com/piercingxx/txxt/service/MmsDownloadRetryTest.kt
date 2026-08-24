package com.piercingxx.txxt.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the T4 MMS download retry (MmsDownloadRetry.kt).
 *
 * MMS auto-download is off (`docs/PRIVACY.md:151-153`); an attachment is fetched
 * only on explicit tap, and a tap-initiated download can fail. `MmsDownloadRetry`
 * retries a failed download with an exponential backoff schedule and — when every
 * attempt is exhausted — leaves the message in [MmsDownloadState.FAILED] so the
 * thread UI shows the failure instead of silently giving up. The component is
 * pure over its seams (`performDownload` / `wait` / `onStateChange`), so the
 * retry/backoff/failed-state logic is driven directly; the `MmsReceiver` wire-in
 * is locked by a source-reading assertion (the established `RebootReconcileTest`
 * pattern).
 */
class MmsDownloadRetryTest {

    private fun retry(
        maxAttempts: Int = 5,
        baseDelayMillis: Long = 1_000L,
        maxDelayMillis: Long = 60_000L,
        onStateChange: (Long, MmsDownloadState) -> Unit = { _, _ -> },
        performDownload: suspend (Long) -> Boolean,
    ) = MmsDownloadRetry(
        maxAttempts = maxAttempts,
        baseDelayMillis = baseDelayMillis,
        maxDelayMillis = maxDelayMillis,
        wait = { _ -> }, // skip real waiting in tests
        onStateChange = onStateChange,
        performDownload = performDownload,
    )

    // ---- Backoff schedule ----

    @Test
    fun `backoff doubles each retry and caps at the maximum`() {
        val r = retry(performDownload = { true })

        assertEquals(1_000L, r.retryDelayMillis(1))
        assertEquals(2_000L, r.retryDelayMillis(2))
        assertEquals(4_000L, r.retryDelayMillis(3))
        assertEquals(8_000L, r.retryDelayMillis(4))
        // Capped: 16_000, 32_000, then 64_000 -> 60_000.
        assertEquals(60_000L, r.retryDelayMillis(10))
    }

    @Test
    fun `backoff respects a custom base and cap`() {
        val r = retry(baseDelayMillis = 100L, maxDelayMillis = 400L, performDownload = { true })

        assertEquals(100L, r.retryDelayMillis(1))
        assertEquals(200L, r.retryDelayMillis(2))
        assertEquals(400L, r.retryDelayMillis(3))
        assertEquals(400L, r.retryDelayMillis(4))
    }

    // ---- Success path ----

    @Test
    fun `a successful download reaches DOWNLOADED on the first attempt`() {
        var calls = 0
        val r = retry(performDownload = { calls++; true })

        val state = runBlocking { r.download(7L) }

        assertEquals(MmsDownloadState.DOWNLOADED, state)
        assertEquals(1, calls)
    }

    @Test
    fun `a download that succeeds on a later attempt reports DOWNLOADED`() {
        var calls = 0
        val r = retry(performDownload = { calls++; calls >= 3 })

        val state = runBlocking { r.download(7L) }

        assertEquals(MmsDownloadState.DOWNLOADED, state)
        assertEquals(3, calls)
    }

    // ---- Failure path and the failed state ----

    @Test
    fun `a download that always fails reaches FAILED after maxAttempts`() {
        var calls = 0
        val r = retry(maxAttempts = 4, performDownload = { calls++; false })

        val state = runBlocking { r.download(7L) }

        assertEquals(MmsDownloadState.FAILED, state)
        assertEquals(4, calls)
    }

    @Test
    fun `a failed download stays FAILED on a later call`() {
        var calls = 0
        val r = retry(maxAttempts = 2, performDownload = { calls++; false })

        val first = runBlocking { r.download(7L) }
        val callsAfterFirst = calls
        val second = runBlocking { r.download(7L) }

        assertEquals(MmsDownloadState.FAILED, first)
        assertEquals(MmsDownloadState.FAILED, second)
        // The terminal FAILED state is respected: the second call attempts nothing.
        assertEquals(callsAfterFirst, calls)
    }

    @Test
    fun `the failed state is surfaced through onStateChange`() {
        val transitions = mutableListOf<MmsDownloadState>()
        val r = retry(
            maxAttempts = 2,
            onStateChange = { _, s -> transitions.add(s) },
            performDownload = { false },
        )

        runBlocking { r.download(7L) }

        // DOWNLOADING then FAILED — the UI sees the failure, never a silent drop.
        assertEquals(
            listOf(MmsDownloadState.DOWNLOADING, MmsDownloadState.FAILED),
            transitions,
        )
    }

    @Test
    fun `state defaults to NOT_ATTEMPTED before any download`() {
        val r = retry(performDownload = { true })

        assertEquals(MmsDownloadState.NOT_ATTEMPTED, r.state(7L))
    }

    // ---- Concurrency: a double tap must never double-download (M9) ----

    @Test
    fun `a concurrent second tap does not start a second download`() = runBlocking {
        // The first tap parks inside performDownload while holding the state
        // machine; the second tap arrives mid-flight. Without the download
        // lock the two callers interleave and calls reaches 2 — with it, the
        // second tap is a no-op.
        val releaseFirstTap = CompletableDeferred<Unit>()
        var calls = 0
        val r = retry(maxAttempts = 5, performDownload = {
            calls++
            releaseFirstTap.await()
            true
        })

        val firstTap = async(start = CoroutineStart.UNDISPATCHED) { r.download(7L) }
        // The first tap is now parked in performDownload with DOWNLOADING set.
        val secondTap = async { r.download(7L) }

        releaseFirstTap.complete(Unit)

        assertEquals(MmsDownloadState.DOWNLOADED, firstTap.await())
        assertEquals(MmsDownloadState.DOWNLOADED, secondTap.await())
        assertEquals("a concurrent double-tap must perform exactly one download", 1, calls)
    }

    // ---- Wire-in: the running receiver reaches MmsDownloadRetry ----

    @Test
    fun `MmsReceiver source constructs and reaches the MmsDownloadRetry tracker`() {
        val source = sourceText("service/MmsReceiver.kt")
        assertTrue(
            "MmsReceiver must construct an MmsDownloadRetry",
            source.contains("MmsDownloadRetry("),
        )
        assertTrue(
            "MmsReceiver must reach the retry tracker for a stored MMS message",
            source.contains("downloadRetry."),
        )
    }

    @Test
    fun `the declared receiver name resolves to a class`() {
        Class.forName("com.piercingxx.txxt.service.MmsReceiver")
    }

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private fun sourceText(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()
}