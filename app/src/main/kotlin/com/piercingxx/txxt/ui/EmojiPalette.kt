package com.piercingxx.txxt.ui

/**
 * Compose-bar glyph set drawn from the bundled JetBrains Mono Nerd Font.
 *
 * These are monochrome Nerd Font / dingbat codepoints, not colour emoji.
 * They render from `@font/font_body` with no system-emoji fallback. Private-use
 * icons will look right in TxxT; other phones may not have the same face.
 */
object EmojiPalette {

    /** Nerd Font smile used as the compose-bar affordance. */
    const val PICKER_GLYPH = "\uF118" // fa-smile_o

    val glyphs: List<String> = listOf(
        "\uF118", // fa-smile_o
        "\uEE49", // fa-face_grin
        "\uEE62", // fa-face_laugh
        "\uEE80", // fa-face_smile_beam
        "\uF119", // fa-frown_o
        "\uF11A", // fa-meh_o
        "\uEE1F", // fa-face_angry
        "\uEE7C", // fa-face_sad_tear
        "\uEE61", // fa-face_kiss_wink_heart
        "\uF004", // fa-heart
        "\uF08A", // fa-heart_o
        "\u2665", // ♥ (oct-heart)
        "\uF005", // fa-star
        "\uF06D", // fa-fire
        "\u26A1", // ⚡
        "\uF164", // fa-thumbs_up
        "\uF165", // fa-thumbs_down
        "\uF00C", // fa-check
        "\uF00D", // fa-xmark
        "\uF185", // fa-sun_o
        "\uF186", // fa-moon_o
        "\uF06E", // fa-eye
        "\uF21E", // fa-heartbeat
        "\uEEDB", // fa-hands_praying
        "\uEEFD", // fa-hand_fist
        "\uF25B", // fa-hand_peace_o
        "\uF140", // fa-bullseye
        "\u2713", // ✓
        "\u2717", // ✗
        "\u26A0", // ⚠
        "\uF069", // fa-asterisk
        "\uF11B", // fa-gamepad
    )
}
