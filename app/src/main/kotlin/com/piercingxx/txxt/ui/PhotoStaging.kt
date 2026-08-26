package com.piercingxx.txxt.ui

import java.io.File
import java.io.IOException

/**
 * The staged-photo store: app-private copies of picked photos, and the answer
 * to the picker's one-shot URI grant.
 *
 * **The problem this solves.** The Android photo picker
 * (`ActivityResultContracts.PickVisualMedia`) hands back a `content://` URI
 * carrying a *temporary* read grant. That grant is not persistable —
 * `takePersistableUriPermission` throws `SecurityException` for a photo-picker
 * URI, because the picker deliberately grants access to exactly one item for
 * exactly this task and nothing more (which is precisely why it needs no
 * `READ_MEDIA_IMAGES` permission at all). The grant does not survive process
 * death, and TxxT's send is not synchronous with the pick: the operator picks,
 * then types, then taps send, and the send itself runs on a coroutine. Holding
 * the picker's URI and hoping it is still readable at send time is exactly the
 * kind of silent failure this codebase refuses.
 *
 * **The decision.** The bytes are copied into app-private cache storage *at
 * pick time*, while the one-shot grant is provably live, and everything
 * downstream — the indicator line, the send, a restore after a configuration
 * change — refers to that copy by a `file://` URI the app owns outright. The
 * picker's URI is never retained. `SendPipeline.sendMms` still does the
 * scrubbing: the staged copy is the *original* bytes, and the scrubbed bytes
 * only ever exist in the pipeline's own temp file, so the metadata-scrub
 * guarantee is untouched by this staging step.
 *
 * **What a dead process costs.** The staged file survives process death; the
 * in-memory pointer to it does not. `ThreadActivity` writes the staged path
 * into its saved instance state, so a configuration change or a
 * saved-state restore re-attaches the same photo. A cold start with no saved
 * state, or a staged file the OS reclaimed from the cache directory, means the
 * attachment is simply gone — and the compose bar shows no indicator, because
 * claiming an attachment the app can no longer read would be the dishonest
 * outcome. [sweep] then removes whatever such a death orphaned, so an
 * abandoned pick cannot accumulate copies of the operator's photos in cache
 * forever.
 *
 * Pure over `java.io` — zero `android.*` imports — so the naming, collision,
 * containment and expiry rules are JVM-testable against a real temp directory
 * without a device.
 */
object PhotoStaging {

    /** Name of the staging subdirectory inside the app's cache directory. */
    const val DIRECTORY_NAME = "attachments"

    /** How long an orphaned staged copy is kept before [sweep] deletes it. */
    const val DEFAULT_TTL_MILLIS = 24L * 60L * 60L * 1000L

    private const val PREFIX = "staged-"
    private const val SUFFIX = ".bin"

    /**
     * How many same-millisecond name collisions are tolerated before staging
     * gives up. Two picks cannot realistically land in one millisecond; the
     * bound exists so a directory the app cannot delete from can never spin
     * this into an unbounded loop.
     */
    private const val MAX_COLLISION_RETRIES = 64

    /** The staging directory inside [cacheDir]. Not created until [stage] needs it. */
    fun directory(cacheDir: File): File = File(cacheDir, DIRECTORY_NAME)

    /**
     * The name a staged copy takes: prefix, the pick's wall-clock millisecond,
     * a collision sequence, and a neutral extension.
     *
     * The extension is deliberately `.bin`, not the source photo's: nothing
     * downstream dispatches on it (`MetadataScrubber` sniffs the bytes'
     * container, and the pipeline writes its own `.scrubbed` temp file), and a
     * name carried over from the picked file would put the operator's own
     * filename on disk for no gain.
     */
    fun name(nowMillis: Long, sequence: Int): String = "$PREFIX$nowMillis-$sequence$SUFFIX"

    /**
     * Whether [file] is a staged copy this app wrote into [directory].
     *
     * Both halves matter. The name check keeps [sweep] from deleting anything
     * that is not ours. The containment check is what a restore path needs: a
     * path arriving from saved instance state is only ever used after it proves
     * to be a real file *inside* the staging directory, so a malformed or stale
     * bundle can never point the send at an arbitrary file on disk.
     */
    fun isStagedIn(directory: File, file: File): Boolean =
        file.isFile &&
            file.name.startsWith(PREFIX) &&
            file.name.endsWith(SUFFIX) &&
            file.parentFile?.absolutePath == directory.absolutePath

    /**
     * Writes [bytes] into [directory] as a staged copy and returns the file.
     *
     * Returns `null` — never throws — when the directory cannot be created,
     * every candidate name is taken, or the write fails; the caller surfaces
     * that to the operator instead of pretending a photo was attached.
     */
    fun stage(directory: File, bytes: ByteArray, nowMillis: Long): File? = try {
        if (!directory.isDirectory && !directory.mkdirs()) {
            null
        } else {
            var sequence = 0
            var candidate = File(directory, name(nowMillis, sequence))
            while (candidate.exists() && sequence < MAX_COLLISION_RETRIES) {
                sequence += 1
                candidate = File(directory, name(nowMillis, sequence))
            }
            if (candidate.exists()) {
                null
            } else {
                candidate.writeBytes(bytes)
                candidate
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /**
     * Deletes a staged copy. A `null` file (nothing attached) is a no-op, and
     * a delete that fails is reported rather than thrown — the caller's job is
     * to drop the reference either way, not to crash the compose bar over a
     * cache file.
     */
    fun discard(file: File?): Boolean = try {
        file != null && file.delete()
    } catch (_: SecurityException) {
        false
    }

    /**
     * Deletes staged copies older than [ttlMillis], returning how many went.
     *
     * This is the orphan collector for the process-death case: a pick whose
     * activity never came back leaves a copy of the operator's photo in cache
     * with no reference to it. [keep] is the currently-attached copy, which is
     * never deleted regardless of its age — a long-lived compose session must
     * not have its own attachment swept out from under it. Only files [isStagedIn]
     * recognises are candidates, so nothing else in the cache directory is
     * touched.
     */
    fun sweep(
        directory: File,
        nowMillis: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        keep: File? = null,
    ): Int {
        val files = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return 0
        var deleted = 0
        files.forEach { file ->
            if (!isStagedIn(directory, file)) return@forEach
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            if (nowMillis - file.lastModified() < ttlMillis) return@forEach
            if (discard(file)) deleted += 1
        }
        return deleted
    }
}
