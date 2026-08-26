package com.piercingxx.txxt.ui

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies [PhotoStaging] — the answer to the photo picker's
 * one-shot, non-persistable URI grant — against a real temporary directory.
 *
 * [PhotoStaging] is pure over `java.io` for exactly this reason: the rules that
 * decide whether a picked photo is still readable at send time, whether a
 * restored path may be trusted, and whether an abandoned pick leaves a copy of
 * the operator's photo in cache forever are all testable on the JVM without a
 * device (docs/DESIGN.md §"Pure-Kotlin core").
 */
class PhotoStagingTest {

    private lateinit var cacheDir: File
    private lateinit var directory: File

    @Before
    fun setUp() {
        cacheDir = File.createTempFile("txxt-cache-", "").apply {
            delete()
            mkdirs()
        }
        directory = PhotoStaging.directory(cacheDir)
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ---- Staging ----

    @Test
    fun `staging writes the picked bytes into an app-private copy`() {
        // The whole point: after this returns, the photo is readable from a
        // file this app owns, so the picker's grant expiring cannot break the
        // send however long the operator spends composing.
        val payload = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00)
        val staged = PhotoStaging.stage(directory, payload, nowMillis = 1_000L)

        assertNotNull("staging must produce a file", staged)
        assertTrue(staged!!.isFile)
        assertArrayEquals(payload, staged.readBytes())
        assertEquals(directory.absolutePath, staged.parentFile?.absolutePath)
    }

    @Test
    fun `staging creates the staging directory on first use`() {
        assertFalse("precondition: nothing staged yet", directory.exists())
        assertNotNull(PhotoStaging.stage(directory, bytes(1, 2, 3), nowMillis = 1L))
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `two picks in the same millisecond do not overwrite each other`() {
        val first = PhotoStaging.stage(directory, bytes(1), nowMillis = 7L)!!
        val second = PhotoStaging.stage(directory, bytes(2), nowMillis = 7L)!!

        assertFalse("a collision must not reuse the name", first.name == second.name)
        assertArrayEquals(bytes(1), first.readBytes())
        assertArrayEquals(bytes(2), second.readBytes())
    }

    @Test
    fun `staging into an unusable directory reports failure instead of throwing`() {
        // A regular file where the directory should be: mkdirs cannot succeed.
        // The caller has to be able to tell the operator the photo was not
        // attached, so this must be a null, never an exception.
        val blocked = File(cacheDir, "blocked").apply { writeBytes(bytes(0)) }
        assertNull(PhotoStaging.stage(blocked, bytes(1, 2), nowMillis = 1L))
    }

    // ---- Containment (the restore path's guard) ----

    @Test
    fun `a staged copy is recognised as staged`() {
        val staged = PhotoStaging.stage(directory, bytes(9), nowMillis = 42L)!!
        assertTrue(PhotoStaging.isStagedIn(directory, staged))
    }

    @Test
    fun `a file outside the staging directory is never treated as staged`() {
        // This is the guard on the saved-instance-state restore: a path from a
        // stale or malformed bundle must not be able to point the send at an
        // arbitrary file on disk.
        directory.mkdirs()
        val outsider = File(cacheDir, PhotoStaging.name(1L, 0)).apply { writeBytes(bytes(1)) }
        assertFalse(PhotoStaging.isStagedIn(directory, outsider))
    }

    @Test
    fun `a foreign file inside the staging directory is not treated as staged`() {
        directory.mkdirs()
        val foreign = File(directory, "not-ours.jpg").apply { writeBytes(bytes(1)) }
        assertFalse(PhotoStaging.isStagedIn(directory, foreign))
    }

    @Test
    fun `a vanished staged copy is not treated as staged`() {
        // The honest end state after the OS reclaims the cache directory: the
        // attachment is gone, and the compose bar must be able to see that
        // rather than showing an indicator for something unreadable.
        val staged = PhotoStaging.stage(directory, bytes(1), nowMillis = 1L)!!
        assertTrue(staged.delete())
        assertFalse(PhotoStaging.isStagedIn(directory, staged))
    }

    // ---- Discard (remove without sending) ----

    @Test
    fun `discarding deletes the copy so a removed photo does not linger in cache`() {
        val staged = PhotoStaging.stage(directory, bytes(1, 2, 3), nowMillis = 1L)!!
        assertTrue(PhotoStaging.discard(staged))
        assertFalse(staged.exists())
    }

    @Test
    fun `discarding nothing is a no-op`() {
        assertFalse(PhotoStaging.discard(null))
    }

    // ---- Sweep (the process-death orphan collector) ----

    @Test
    fun `the sweep deletes copies orphaned by a process death`() {
        val orphan = PhotoStaging.stage(directory, bytes(1), nowMillis = 0L)!!
        orphan.setLastModified(0L)

        val deleted = PhotoStaging.sweep(directory, nowMillis = DAY * 2, ttlMillis = DAY)

        assertEquals(1, deleted)
        assertFalse("an abandoned pick must not keep a photo in cache forever", orphan.exists())
    }

    @Test
    fun `the sweep leaves a fresh copy alone`() {
        val fresh = PhotoStaging.stage(directory, bytes(1), nowMillis = DAY * 2)!!
        fresh.setLastModified(DAY * 2)

        assertEquals(0, PhotoStaging.sweep(directory, nowMillis = DAY * 2, ttlMillis = DAY))
        assertTrue(fresh.exists())
    }

    @Test
    fun `the sweep never deletes the currently attached copy, however old`() {
        // A long-lived compose session must not have its own attachment swept
        // out from under it.
        val attached = PhotoStaging.stage(directory, bytes(1), nowMillis = 0L)!!
        attached.setLastModified(0L)

        val deleted = PhotoStaging.sweep(
            directory = directory,
            nowMillis = DAY * 30,
            ttlMillis = DAY,
            keep = attached,
        )

        assertEquals(0, deleted)
        assertTrue(attached.exists())
    }

    @Test
    fun `the sweep touches nothing it did not write`() {
        directory.mkdirs()
        val foreign = File(directory, "someone-elses.dat").apply { writeBytes(bytes(1)) }
        foreign.setLastModified(0L)

        assertEquals(0, PhotoStaging.sweep(directory, nowMillis = DAY * 30, ttlMillis = DAY))
        assertTrue(foreign.exists())
    }

    @Test
    fun `sweeping a directory that does not exist is a no-op`() {
        assertEquals(0, PhotoStaging.sweep(directory, nowMillis = DAY, ttlMillis = DAY))
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
