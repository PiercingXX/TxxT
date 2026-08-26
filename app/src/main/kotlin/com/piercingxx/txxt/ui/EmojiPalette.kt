package com.piercingxx.txxt.ui

/**
 * Compose-bar emoji set. These are standard Unicode emoji so they survive
 * SMS/MMS to other phones. Private-use Nerd Font codepoints do not encode
 * through the SMS alphabet and arrive as boxes or nothing.
 */
object EmojiPalette {

    /** Colour-emoji smile used as the compose-bar affordance. */
    const val PICKER_GLYPH = "😊"

    val glyphs: List<String> = listOf(
        "😊", "😂", "😍", "😘", "😎", "😢", "😡", "😭",
        "🤔", "❤️", "💕", "⭐", "🔥", "⚡", "👍", "👎",
        "✅", "❌", "☀️", "🌙", "👀", "💯", "🎉", "🙏",
        "✌️", "💪", "😅", "🤣", "🥰", "😉", "⚠️", "🎮",
    )
}
