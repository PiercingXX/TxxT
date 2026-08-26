package com.piercingxx.txxt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour-verifies the pure photo-attachment seam ([PhotoAttachment]): the
 * send plan a composed message expands into, and the text of the one-line
 * attachment indicator.
 *
 * This is the routing rule the compose bar depends on, held here rather than in
 * `ThreadActivity` precisely so it is JVM-testable without a device (the
 * established `DictationInsert` / `ThreadMessagePresenter` pattern). The glyph
 * constants are locked against the bundled font's cmap by
 * [ComposeBarGlyphTest]; what is under test here is behaviour.
 */
class PhotoAttachmentTest {

    // ---- Send plan ----

    @Test
    fun `nothing composed dispatches nothing`() {
        assertEquals(emptyList<SendStep>(), PhotoAttachment.plan("", hasPhoto = false))
        assertEquals(emptyList<SendStep>(), PhotoAttachment.plan("   ", hasPhoto = false))
    }

    @Test
    fun `text only still goes through the SMS path`() {
        // The pre-existing behaviour must be untouched by attachments existing:
        // a text-only send is one SMS step and nothing else.
        assertEquals(listOf(SendStep.SMS_TEXT), PhotoAttachment.plan("hello", hasPhoto = false))
    }

    @Test
    fun `a photo with an empty body sends as MMS alone`() {
        // Sending a photo with no caption is a first-class case: it must not
        // require typing something first, and it must not emit an empty SMS.
        assertEquals(listOf(SendStep.MMS_PHOTO), PhotoAttachment.plan("", hasPhoto = true))
        assertEquals(listOf(SendStep.MMS_PHOTO), PhotoAttachment.plan("  ", hasPhoto = true))
    }

    @Test
    fun `a photo with a caption sends the caption too, never drops it`() {
        // SendPipeline.sendMms carries media and no text part, so a caption
        // composed alongside a photo has to travel as its own SMS. The one
        // outcome that would be a lie about what was sent — dropping it — is
        // what this test exists to prevent.
        val plan = PhotoAttachment.plan("look at this", hasPhoto = true)
        assertEquals(listOf(SendStep.SMS_TEXT, SendStep.MMS_PHOTO), plan)
        assertTrue("the caption must be dispatched, not discarded", SendStep.SMS_TEXT in plan)
    }

    @Test
    fun `the caption is dispatched before the photo`() {
        val plan = PhotoAttachment.plan("caption", hasPhoto = true)
        assertTrue(plan.indexOf(SendStep.SMS_TEXT) < plan.indexOf(SendStep.MMS_PHOTO))
    }

    // ---- Indicator line ----

    @Test
    fun `the indicator names the photo, the file and the size`() {
        assertEquals(
            "photo · IMG_0421.jpg · 2.0 MB",
            PhotoAttachment.indicator("IMG_0421.jpg", 2L * 1024 * 1024),
        )
    }

    @Test
    fun `a missing display name drops that segment instead of printing an empty one`() {
        assertEquals("photo · 512 B", PhotoAttachment.indicator(null, 512))
        assertEquals("photo · 512 B", PhotoAttachment.indicator("   ", 512))
    }

    @Test
    fun `the size reads in the units a file manager uses`() {
        assertEquals("0 B", PhotoAttachment.formatSize(0))
        assertEquals("0 B", PhotoAttachment.formatSize(-1))
        assertEquals("1023 B", PhotoAttachment.formatSize(1023))
        assertEquals("1 KB", PhotoAttachment.formatSize(1024))
        assertEquals("100 KB", PhotoAttachment.formatSize(102_400))
        assertEquals("1.0 MB", PhotoAttachment.formatSize(1024L * 1024))
        assertEquals("3.5 MB", PhotoAttachment.formatSize((3.5 * 1024 * 1024).toLong()))
    }
}

/**
 * Locks the compose bar's glyphs against the **bundled font's actual cmap**.
 *
 * The rule this repo already follows for the send `➜` (U+279C): a glyph in the
 * compose bar is only allowed if `@font/font_body` — JetBrains Mono, shipped in
 * `res/font/` — really maps it, so it renders as monochrome type from our own
 * face and can never fall through to a system colour-emoji font. Assuming
 * coverage is how a paperclip 📎 (U+1F4CE, NOT in this cmap) ends up as a
 * coloured tile in the middle of a text-first bar.
 *
 * The test parses the TTF's `cmap` table directly rather than trusting a
 * rendering: no device, no font library, just the bytes that ship in the APK.
 */
class ComposeBarGlyphTest {

    /** Gradle unit tests run with the module directory (app/) as the working directory. */
    private fun fontFile(name: String): File =
        sequenceOf(
            File("src/main/res/font/$name"),
            File("app/src/main/res/font/$name"),
        ).first { it.exists() }

    private val coverage: Set<Int> by lazy {
        TrueTypeCmap.codepoints(fontFile("jetbrains_mono_regular.ttf").readBytes())
    }

    private fun assertMapped(label: String, glyph: String) {
        val codepoint = glyph.codePointAt(0)
        assertTrue(
            "$label ${"U+%04X".format(codepoint)} must be mapped by the bundled JetBrains Mono",
            codepoint in coverage,
        )
    }

    @Test
    fun `the bundled face maps the send glyph`() {
        // The precedent: ➜ was chosen exactly this way.
        assertMapped("the send glyph", "➜")
    }

    @Test
    fun `the bundled face maps the attach glyph`() {
        assertMapped("the attach glyph", PhotoAttachment.ATTACH_GLYPH)
    }

    @Test
    fun `the bundled face maps the remove glyph`() {
        assertMapped("the remove glyph", PhotoAttachment.REMOVE_GLYPH)
    }

    @Test
    fun `the bundled face maps every character of the indicator line`() {
        // Including the · separator — an indicator line is only text-first if
        // every codepoint in it comes from our own face.
        PhotoAttachment.indicator("IMG_0421.jpg", 2048).codePoints().forEach { codepoint ->
            assertTrue(
                "indicator codepoint ${"U+%04X".format(codepoint)} must be mapped",
                codepoint in coverage,
            )
        }
    }

    @Test
    fun `a paperclip emoji is NOT mapped, which is why it is not the attach glyph`() {
        // The negative control. If this ever starts failing the font changed,
        // and the reasoning behind the glyph choice needs revisiting — but for
        // the face that ships today, 📎 would arrive as a colour emoji.
        assertTrue(
            "U+1F4CE must be absent from the bundled face (the reason ⊕ was chosen)",
            0x1F4CE !in coverage,
        )
    }
}

/**
 * Minimal TrueType `cmap` reader: enough to answer "does this face map this
 * codepoint?" from the raw font bytes.
 *
 * Test-only, and deliberately so — nothing at runtime needs it. Supports the
 * two subtable formats a modern face ships (format 4 BMP segment mapping and
 * format 12 full-range groups) and prefers format 12 when both are present.
 */
private object TrueTypeCmap {

    fun codepoints(font: ByteArray): Set<Int> {
        val tableCount = u16(font, 4)
        var cmapOffset = -1
        for (index in 0 until tableCount) {
            val record = 12 + 16 * index
            val tag = String(font, record, 4, Charsets.ISO_8859_1)
            if (tag == "cmap") cmapOffset = u32(font, record + 8)
        }
        require(cmapOffset >= 0) { "font has no cmap table" }

        val subtableCount = u16(font, cmapOffset + 2)
        var chosen = -1
        var chosenFormat = -1
        for (index in 0 until subtableCount) {
            val record = cmapOffset + 4 + 8 * index
            val subtable = cmapOffset + u32(font, record + 4)
            when (u16(font, subtable)) {
                12 -> {
                    chosen = subtable
                    chosenFormat = 12
                }
                4 -> if (chosenFormat != 12) {
                    chosen = subtable
                    chosenFormat = 4
                }
            }
        }
        require(chosen >= 0) { "font has no format 4 or 12 cmap subtable" }

        return if (chosenFormat == 12) format12(font, chosen) else format4(font, chosen)
    }

    private fun format4(font: ByteArray, subtable: Int): Set<Int> {
        val segCountX2 = u16(font, subtable + 6)
        val segCount = segCountX2 / 2
        val endBase = subtable + 14
        val startBase = endBase + segCountX2 + 2
        val deltaBase = startBase + segCountX2
        val rangeBase = deltaBase + segCountX2
        val mapped = HashSet<Int>()
        for (segment in 0 until segCount) {
            val end = u16(font, endBase + 2 * segment)
            val start = u16(font, startBase + 2 * segment)
            if (start > end) continue
            val delta = u16(font, deltaBase + 2 * segment)
            val rangeOffset = u16(font, rangeBase + 2 * segment)
            for (code in start..end) {
                if (code == 0xFFFF) continue
                val glyph = if (rangeOffset == 0) {
                    (code + delta) and 0xFFFF
                } else {
                    val at = rangeBase + 2 * segment + rangeOffset + 2 * (code - start)
                    if (at + 1 >= font.size) continue
                    val raw = u16(font, at)
                    if (raw == 0) 0 else (raw + delta) and 0xFFFF
                }
                if (glyph != 0) mapped.add(code)
            }
        }
        return mapped
    }

    private fun format12(font: ByteArray, subtable: Int): Set<Int> {
        val groups = u32(font, subtable + 12)
        val mapped = HashSet<Int>()
        for (group in 0 until groups) {
            val at = subtable + 16 + 12 * group
            val start = u32(font, at)
            val end = u32(font, at + 4)
            for (code in start..end) mapped.add(code)
        }
        return mapped
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun u32(bytes: ByteArray, at: Int): Int =
        (u16(bytes, at) shl 16) or u16(bytes, at + 2)
}
