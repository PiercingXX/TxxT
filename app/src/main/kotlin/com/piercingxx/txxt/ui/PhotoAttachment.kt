package com.piercingxx.txxt.ui

import java.util.Locale

/**
 * One dispatch step a composed send expands into.
 *
 * A composed message is not always one transmission. The send pipeline's MMS
 * entry point ([com.piercingxx.txxt.service.SendPipeline.sendMms]) carries a
 * single scrubbed media body and **no text part** — so a photo composed with a
 * caption cannot travel as one PDU through the pipeline this app has. Rather
 * than silently dropping the caption (the one outcome that would lie to the
 * operator about what was sent), [PhotoAttachment.plan] expands that case into
 * two ordered steps and the thread shows both rows.
 */
enum class SendStep {
    /** The composed text, dispatched over SMS exactly as a text-only send is. */
    SMS_TEXT,

    /** The attached photo, dispatched over MMS through the scrubbing pipeline. */
    MMS_PHOTO,
}

/**
 * The compose bar's photo-attachment seam (pure).
 *
 * Pure Kotlin with zero `android.*` imports so the two decisions that actually
 * carry behaviour — *what a composed send expands into* and *what the
 * attachment indicator says* — are JVM-testable without a device
 * (docs/DESIGN.md §"Pure-Kotlin core", the established `DictationInsert` /
 * `ThreadMessagePresenter` pattern). `ThreadActivity` owns only the framework
 * hops around this seam: the photo picker, the staged copy, and the two
 * pipeline calls.
 *
 * **The glyphs.** Both constants are codepoints the bundled JetBrains Mono
 * (`@font/font_body`) actually maps in its `cmap` — checked against the font
 * binary, not assumed, exactly as the send button's `➜` (U+279C) was. `⊕`
 * (U+2295 CIRCLED PLUS) and `✕` (U+2715 MULTIPLICATION X) are both present, so
 * they render as monochrome type from our own face and can never fall through
 * to a colour-emoji font. A paperclip (U+1F4CE) is NOT in that cmap — it would
 * arrive as a colour emoji from the system fallback and break the compose
 * bar's idiom, so it is deliberately not used.
 */
object PhotoAttachment {

    /**
     * The attach affordance's glyph: `⊕` (U+2295). Borderless bright-white
     * type in the compose row, the same treatment the send `➜` gets — no
     * filled pill, no vector icon (docs/DESIGN.md — the accent lives in the
     * type, not in chrome).
     */
    const val ATTACH_GLYPH = "⊕"

    /**
     * The remove affordance's glyph: `✕` (U+2715), shown on the attachment
     * indicator line. Removing an attachment must never require sending it,
     * so this sits beside the indicator and clears the staged photo outright.
     */
    const val REMOVE_GLYPH = "✕"

    /** Revealed photo-only marker (same string as [com.piercingxx.txxt.core.MmsRetrievedContent.PHOTO_PLACEHOLDER]). */
    const val PHOTO_ROW_PLACEHOLDER = "[photo]"

    /**
     * Expands a composed message into the ordered dispatch steps it takes.
     *
     * The rules, and why each one exists:
     *  - **nothing composed** (blank body, no photo) → no steps. The send
     *    affordance is inert rather than sending an empty SMS.
     *  - **text only** → [SendStep.SMS_TEXT]. This is the pre-existing path,
     *    unchanged: a text-only send still goes through `SendPipeline.sendSms`
     *    exactly as it did before attachments existed.
     *  - **photo only** (blank body) → [SendStep.MMS_PHOTO]. Sending a photo
     *    with an empty caption is a first-class case; it must not require the
     *    operator to type something first.
     *  - **photo with a caption** → [SendStep.SMS_TEXT] then
     *    [SendStep.MMS_PHOTO]. The caption goes first so it arrives before the
     *    (slower) MMS where the carrier allows, and — decisively — so the
     *    caption is *sent* rather than discarded: the pipeline's MMS entry
     *    point carries media only.
     *
     * [body] is expected already trimmed by the caller; a blank body is
     * treated as no text either way.
     */
    fun plan(body: String, hasPhoto: Boolean): List<SendStep> = when {
        !hasPhoto && body.isBlank() -> listOf()
        !hasPhoto -> listOf(SendStep.SMS_TEXT)
        body.isBlank() -> listOf(SendStep.MMS_PHOTO)
        else -> listOf(SendStep.SMS_TEXT, SendStep.MMS_PHOTO)
    }

    /**
     * The one-line attachment indicator shown above the compose row.
     *
     * Text-first by design: this app has no bubbles and no cards
     * (docs/PRIVACY.md §2), so an attached photo announces itself as a line of
     * type, not as a thumbnail tile. The line names the thing (`photo`), the
     * file the operator picked when the picker gave a display name, and the
     * size — enough to confirm *which* photo is about to leave the device
     * without rendering the image itself.
     *
     * The separator is `·` (U+00B7), also in the bundled face's cmap. A blank
     * or absent [displayName] simply drops that segment rather than printing an
     * empty one.
     */
    fun indicator(displayName: String?, byteCount: Long): String {
        val name = displayName?.trim().orEmpty()
        val size = formatSize(byteCount)
        return if (name.isEmpty()) "photo · $size" else "photo · $name · $size"
    }

    /**
     * Formats a byte count for the indicator line.
     *
     * Binary units (the file-manager convention the operator sees elsewhere on
     * the device), [Locale.ROOT] so the decimal separator cannot drift with the
     * device locale and make the line untestable. A negative count — which can
     * only mean a caller bug — reads as `0 B` rather than printing a negative
     * size.
     */
    fun formatSize(byteCount: Long): String = when {
        byteCount <= 0L -> "0 B"
        byteCount < KIBI -> "$byteCount B"
        byteCount < MEBI -> "${(byteCount + KIBI / 2) / KIBI} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", byteCount.toDouble() / MEBI)
    }

    private const val KIBI = 1024L
    private const val MEBI = 1024L * 1024L
}
