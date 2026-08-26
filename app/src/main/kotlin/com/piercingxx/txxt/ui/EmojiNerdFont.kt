package com.piercingxx.txxt.ui

/**
 * Display-only rewrite of colour emoji into Nerd Font / dingbat glyphs that
 * `@font/font_body` (JetBrains Mono Nerd Font) actually maps.
 *
 * SMS still carries real Unicode emoji — the keyboard inserts them, and we
 * send them unchanged. This mapping is paint only, so the AMOLED thread
 * never falls through to a colour-emoji face.
 */
object EmojiNerdFont {

    /** fa-smile-o — used when an emoji cluster has no specific glyph. */
    const val FALLBACK = "\uF118"

    data class GlyphSpan(val start: Int, val end: Int, val glyph: String)

    /**
     * Original-string ranges that should paint as a Nerd Font glyph.
     * Offsets are into [text] so an EditText can span them without rewriting
     * the underlying (sendable) characters.
     */
    fun glyphSpans(text: String): List<GlyphSpan> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<GlyphSpan>()
        var i = 0
        while (i < text.length) {
            if (!startsEmoji(text, i)) {
                i += Character.charCount(text.codePointAt(i))
                continue
            }
            val end = consumeEmojiCluster(text, i)
            val key = normalize(text.substring(i, end))
            out.add(GlyphSpan(i, end, GLYPHS[key] ?: FALLBACK))
            i = end
        }
        return out
    }

    fun display(text: String): String {
        val spans = glyphSpans(text)
        if (spans.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        for (span in spans) {
            if (i < span.start) out.append(text, i, span.start)
            out.append(span.glyph)
            i = span.end
        }
        if (i < text.length) out.append(text, i, text.length)
        return out.toString()
    }

    internal fun normalize(cluster: String): String {
        val out = StringBuilder(cluster.length)
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFE0F || cp == 0xFE0E) continue
            if (cp in 0x1F3FB..0x1F3FF) continue // Fitzpatrick skin tone
            if (cp == 0x200D) continue // ZWJ — lookup is on the remaining bases
            out.appendCodePoint(cp)
        }
        return out.toString()
    }

    private fun startsEmoji(text: String, index: Int): Boolean {
        val cp = text.codePointAt(index)
        return isEmojiBase(cp) || isRegionalIndicator(cp)
    }

    internal fun isEmojiBase(cp: Int): Boolean = when (cp) {
        in 0x1F300..0x1FAFF -> true
        in 0x1F1E6..0x1F1FF -> true
        in 0x2600..0x27BF -> true
        in 0x2300..0x23FF -> true
        in 0x2B00..0x2BFF -> true
        0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139, 0x2194, 0x2195,
        0x2196, 0x2197, 0x2198, 0x2199, 0x21A9, 0x21AA, 0x2764, 0x3030,
        0x303D, 0x3297, 0x3299,
        -> true
        else -> false
    }

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    internal fun consumeEmojiCluster(text: String, start: Int): Int {
        val n = text.length
        fun cpAt(i: Int) = if (i in 0 until n) text.codePointAt(i) else -1
        fun skip(i: Int, cp: Int) = i + Character.charCount(cp)

        var i = start
        val first = cpAt(i)
        if (isRegionalIndicator(first)) {
            i = skip(i, first)
            val second = cpAt(i)
            if (isRegionalIndicator(second)) i = skip(i, second)
            return i
        }

        fun eatModifiers(from: Int): Int {
            var j = from
            val skin = cpAt(j)
            if (skin in 0x1F3FB..0x1F3FF) j = skip(j, skin)
            val vs = cpAt(j)
            if (vs == 0xFE0F || vs == 0xFE0E) j = skip(j, vs)
            return j
        }

        if (!isEmojiBase(first)) return skip(start, first)
        i = eatModifiers(skip(i, first))
        while (cpAt(i) == 0x200D) {
            i = skip(i, 0x200D)
            val next = cpAt(i)
            if (!isEmojiBase(next) && !isRegionalIndicator(next)) break
            i = eatModifiers(skip(i, next))
        }
        return i
    }

    /**
     * Normalized cluster (no VS16, no skin, no ZWJ) → Nerd Font glyph.
     * Keys are the remaining colour-emoji bases concatenated.
     */
    private val GLYPHS: Map<String, String> = mapOf(
        // faces
        cp(0x1F60A) to "\uF118", // 😊 smile
        cp(0x1F642) to "\uF118", // 🙂
        cp(0x263A) to "\uF118", // ☺
        cp(0x1F600) to "\uEE49", // 😀 grin
        cp(0x1F601) to "\uEE49", // 😁
        cp(0x1F603) to "\uEE49", // 😃
        cp(0x1F604) to "\uEE62", // 😄 laugh
        cp(0x1F602) to "\uEE62", // 😂
        cp(0x1F923) to "\uEE62", // 🤣
        cp(0x1F605) to "\uEE62", // 😅
        cp(0x1F606) to "\uEE62", // 😆
        cp(0x1F609) to "\uF118", // 😉
        cp(0x1F60D) to "\uEE61", // 😍
        cp(0x1F970) to "\uEE61", // 🥰
        cp(0x1F618) to "\uEE61", // 😘
        cp(0x1F617) to "\uEE61", // 😗
        cp(0x1F619) to "\uEE61", // 😙
        cp(0x1F61A) to "\uEE61", // 😚
        cp(0x1F60E) to "\uF118", // 😎
        cp(0x1F62D) to "\uEE7C", // 😭
        cp(0x1F622) to "\uEE7C", // 😢
        cp(0x1F61E) to "\uF119", // 😞
        cp(0x1F614) to "\uF119", // 😔
        cp(0x1F61F) to "\uF119", // 😟
        cp(0x1F641) to "\uF119", // 🙁
        cp(0x2639) to "\uF119", // ☹
        cp(0x1F620) to "\uEE1F", // 😠
        cp(0x1F621) to "\uEE1F", // 😡
        cp(0x1F92C) to "\uEE1F", // 🤬
        cp(0x1F610) to "\uF11A", // 😐
        cp(0x1F611) to "\uF11A", // 😑
        cp(0x1F636) to "\uF11A", // 😶
        cp(0x1F914) to "\uF11A", // 🤔
        cp(0x1F644) to "\uF11A", // 🙄
        cp(0x1F62C) to "\uF11A", // 😬
        cp(0x1F61C) to "\uEE62", // 😜
        cp(0x1F61D) to "\uEE62", // 😝
        cp(0x1F92A) to "\uEE62", // 🤪
        cp(0x1F917) to "\uF118", // 🤗
        cp(0x1F44D) to "\uF164", // 👍
        cp(0x1F44E) to "\uF165", // 👎
        cp(0x1F44C) to "\uF00C", // 👌 → check
        cp(0x1F44F) to "\uF164", // 👏
        cp(0x1F64F) to "\uEEDB", // 🙏
        cp(0x270C) to "\uF25B", // ✌
        cp(0x1F91D) to "\uF164", // 🤝
        cp(0x270A) to "\uEEFD", // ✊
        cp(0x1F44A) to "\uEEFD", // 👊
        cp(0x1F4AA) to "\uEEFD", // 💪
        cp(0x2764) to "\uF004", // ❤
        cp(0x1F49B) to "\uF004",
        cp(0x1F49A) to "\uF004",
        cp(0x1F499) to "\uF004",
        cp(0x1F49C) to "\uF004",
        cp(0x1F495) to "\uF004", // 💕
        cp(0x1F496) to "\uF004",
        cp(0x1F497) to "\uF004",
        cp(0x1F493) to "\uF004",
        cp(0x1F49E) to "\uF004",
        cp(0x1F498) to "\uF004",
        cp(0x1F494) to "\uF08A", // 💔
        cp(0x2B50) to "\uF005", // ⭐
        cp(0x1F31F) to "\uF005", // 🌟
        cp(0x2728) to "\uF005", // ✨
        cp(0x1F525) to "\uF06D", // 🔥
        cp(0x26A1) to "\u26A1", // ⚡
        cp(0x2705) to "\uF00C", // ✅
        cp(0x2714) to "\uF00C",
        cp(0x2713) to "\u2713",
        cp(0x274C) to "\uF00D", // ❌
        cp(0x2716) to "\uF00D",
        cp(0x2717) to "\u2717",
        cp(0x2600) to "\uF185", // ☀
        cp(0x1F31E) to "\uF185", // 🌞
        cp(0x1F319) to "\uF186", // 🌙
        cp(0x1F31A) to "\uF186",
        cp(0x1F31B) to "\uF186",
        cp(0x1F31C) to "\uF186",
        cp(0x1F31D) to "\uF186",
        cp(0x1F440) to "\uF06E", // 👀
        cp(0x1F441) to "\uF06E", // 👁
        cp(0x1F4AF) to "\uF140", // 💯
        cp(0x1F389) to "\uF005", // 🎉
        cp(0x1F38A) to "\uF005", // 🎊
        cp(0x26A0) to "\u26A0", // ⚠
        cp(0x1F3AE) to "\uF11B", // 🎮
        cp(0x1F4F1) to "\uF10B", // 📱
        cp(0x1F4DE) to "\uF095", // 📞
        cp(0x1F4AC) to "\uF075", // 💬
        cp(0x1F4A1) to "\uF0EB", // 💡
        cp(0x1F4C1) to "\uF07B", // 📁
        cp(0x1F4C2) to "\uF07C",
        cp(0x1F4BE) to "\uF0C7", // 💾
        cp(0x1F512) to "\uF023", // 🔒
        cp(0x1F513) to "\uF09C", // 🔓
        cp(0x1F511) to "\uF084", // 🔑
        cp(0x2709) to "\uF0E0", // ✉
        cp(0x1F4E7) to "\uF0E0", // 📧
        cp(0x1F4F7) to "\uF030", // 📷
        cp(0x1F3B5) to "\uF001", // 🎵
        cp(0x1F3B6) to "\uF001",
        cp(0x1F697) to "\uF1B9", // 🚗
        cp(0x2708) to "\uF072", // ✈
        cp(0x1F680) to "\uF135", // 🚀
        cp(0x1F321) to "\uF2C8", // 🌡
        cp(0x2615) to "\uF0F4", // ☕
        cp(0x1F37A) to "\uF0FC", // 🍺
        cp(0x1F354) to "\uF0F5", // 🍔
        cp(0x1F36A) to "\uF563", // 🍪
        cp(0x1F431) to "\uF6BE", // 🐱
        cp(0x1F436) to "\uF6D3", // 🐶
        cp(0x1F4A9) to "\uF118", // 💩
        cp(0x1F47B) to "\uF0E7", // 👻 → bolt-ish; better than colour
        cp(0x1F480) to "\uF54C", // 💀
        cp(0x1F608) to "\uEE1F", // 😈
        cp(0x1F47D) to "\uF118", // 👽
        cp(0x1F4AA) to "\uEEFD",
        cp(0x1F91A) to "\uF0A4", // 🤚
        cp(0x1F590) to "\uF256", // 🖐
        cp(0x1F44B) to "\uF256", // 👋
        cp(0x1F64C) to "\uF164", // 🙌
        cp(0x1F91F) to "\uF118", // 🤟
        cp(0x1F918) to "\uF118", // 🤘
        cp(0x1F919) to "\uF0A4", // 🤙
        cp(0x261D) to "\uF0A6", // ☝
        cp(0x1F446) to "\uF0AA", // 👆
        cp(0x1F447) to "\uF0AB", // 👇
        cp(0x1F448) to "\uF0A8", // 👈
        cp(0x1F449) to "\uF0A9", // 👉
        cp(0x1F4AA) to "\uEEFD",
        cp(0x1F342) to "\uF06C", // 🍂 leaf-ish
        cp(0x1F333) to "\uF1BB", // 🌳
        cp(0x1F6D1) to "\uF05E", // 🛑
        cp(0x1F198) to "\uF12A", // 🆘
        cp(0x2757) to "\uF12A", // ❗
        cp(0x2753) to "\uF128", // ❓
        cp(0x1F4A4) to "\uF186", // 💤
        cp(0x1F4AB) to "\uF0E7", // 💫
        cp(0x1F4A5) to "\uF0E7", // 💥
        cp(0x1F4A6) to "\uF043", // 💦
        cp(0x1F4A7) to "\uF043", // 💧
        cp(0x2614) to "\uF043", // ☔
        cp(0x26C8) to "\uF0E7", // ⛈
        cp(0x1F327) to "\uF0E9", // 🌧
        cp(0x2601) to "\uF0C2", // ☁
        cp(0x26C5) to "\uF0C2",
        cp(0x2744) to "\uF2DC", // ❄
        cp(0x1F4CD) to "\uF041", // 📍
        cp(0x1F3E0) to "\uF015", // 🏠
        cp(0x1F6AA) to "\uF52B", // 🚪
        cp(0x231A) to "\uF017", // ⌚
        cp(0x23F0) to "\uF0F3", // ⏰
        cp(0x1F4F2) to "\uF10B", // 📲
        cp(0x1F50D) to "\uF002", // 🔍
        cp(0x1F4DD) to "\uF040", // 📝
        cp(0x1F4C4) to "\uF15B", // 📄
        cp(0x1F4B0) to "\uF0D6", // 💰
        cp(0x1F6D2) to "\uF07A", // 🛒
        cp(0x1F6AB) to "\uF05E", // 🚫
        cp(0x1F6B6) to "\uF554", // 🚶
        cp(0x1F3C3) to "\uF70C", // 🏃
        cp(0x1F3C6) to "\uF091", // 🏆
        cp(0x26BD) to "\uF1E3", // ⚽
        cp(0x1F3A7) to "\uF025", // 🎧
        cp(0x1F3A4) to "\uF130", // 🎤
        cp(0x1F4BB) to "\uF109", // 💻
        cp(0x2328) to "\uF11C", // ⌨
        cp(0x1F5A5) to "\uF108", // 🖥
        cp(0x1F4F0) to "\uF1EA", // 📰
        cp(0x1F517) to "\uF0C1", // 🔗
        cp(0x1F310) to "\uF0AC", // 🌐
        cp(0x1F30D) to "\uF0AC",
        cp(0x1F30E) to "\uF0AC",
        cp(0x1F30F) to "\uF0AC",
        cp(0x1F4A3) to "\uF1E2", // 💣
        cp(0x1F6A8) to "\uF0F3", // 🚨
        cp(0x1F4CC) to "\uF08D", // 📌
        cp(0x1F4CE) to "\uF0C6", // 📎 — nerd paperclip, not colour
        cp(0x270F) to "\uF040", // ✏
        cp(0x2712) to "\uF040",
        cp(0x1F58A) to "\uF304", // 🖊
        cp(0x1F4DA) to "\uF02D", // 📚
        cp(0x1F4D6) to "\uF02D",
        cp(0x1F48E) to "\uF3A5", // 💎
        cp(0x1F451) to "\uF521", // 👑
        cp(0x1F48D) to "\uF3A5", // 💍
        cp(0x1F381) to "\uF06B", // 🎁
        cp(0x1F382) to "\uF1FD", // 🎂
        cp(0x1F37B) to "\uF0FC", // 🍻
        cp(0x1F377) to "\uF000", // 🍷
        cp(0x1F36B) to "\uF564", // 🍫
        cp(0x1F34E) to "\uF5D1", // 🍎
        cp(0x1F33F) to "\uF06C", // 🌿
        cp(0x2618) to "\uF06C", // ☘
        cp(0x1F340) to "\uF06C", // 🍀
        cp(0x1F338) to "\uF4D8", // 🌸
        cp(0x1F339) to "\uF4D8", // 🌹
        cp(0x1F33A) to "\uF4D8",
        cp(0x1F490) to "\uF4D8", // 💐
        cp(0x1F30A) to "\uF5C4", // 🌊
        cp(0x1F315) to "\uF186", // 🌕
        cp(0x1F311) to "\uF186",
        cp(0x2B55) to "\uF111", // ⭕
        cp(0x1F534) to "\uF111", // 🔴
        cp(0x1F7E2) to "\uF111", // 🟢
        cp(0x1F535) to "\uF111",
        cp(0x26AA) to "\uF111",
        cp(0x26AB) to "\uF111",
        cp(0x1F7E0) to "\uF111",
        cp(0x1F7E1) to "\uF111",
        cp(0x1F7E3) to "\uF111",
        cp(0x1F7E4) to "\uF111",
        cp(0x1F53A) to "\uF0D8", // 🔺
        cp(0x25B6) to "\uF04B", // ▶
        cp(0x23F8) to "\uF04C", // ⏸
        cp(0x23F9) to "\uF04D", // ⏹
        cp(0x1F500) to "\uF074", // 🔀
        cp(0x1F501) to "\uF01E", // 🔁
        cp(0x1F502) to "\uF01E",
        cp(0x23E9) to "\uF051", // ⏩
        cp(0x23EA) to "\uF048", // ⏪
        cp(0x1F3B2) to "\uF522", // 🎲
        cp(0x2660) to "\uF219", // ♠
        cp(0x2665) to "\u2665", // ♥
        cp(0x2666) to "\uF219",
        cp(0x2663) to "\uF219",
        cp(0x1F0CF) to "\uF1BB", // 🃏
        cp(0x1F004) to "\uF1BB",
        cp(0x1F3B4) to "\uF1BB",
        cp(0x1F19A) to "\uF11B",
        cp(0x1F4CA) to "\uF080", // 📊
        cp(0x1F4C8) to "\uF201", // 📈
        cp(0x1F4C9) to "\uF201",
        cp(0x1F5D1) to "\uF1F8", // 🗑
        cp(0x1F6AE) to "\uF1F8",
        cp(0x267B) to "\uF1B8", // ♻
        cp(0x1F504) to "\uF021", // 🔄
        cp(0x1F198) to "\uF12A",
        cp(0x2122) to "\uF25A",
        cp(0x00A9) to "\uF1F9",
        cp(0x00AE) to "\uF25D",
        // keycaps like 1️⃣ — digit + combining enclosing keycap
        "1\u20E3" to "1",
        "2\u20E3" to "2",
        "3\u20E3" to "3",
        "4\u20E3" to "4",
        "5\u20E3" to "5",
        "6\u20E3" to "6",
        "7\u20E3" to "7",
        "8\u20E3" to "8",
        "9\u20E3" to "9",
        "0\u20E3" to "0",
        "#\u20E3" to "#",
        "*\u20E3" to "*",
    )

    private fun cp(code: Int): String = String(Character.toChars(code))
}
