package com.piercingxx.txxt.ui

import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.piercingxx.txxt.R

/**
 * Applies the bundled body face (JetBrains Mono Nerd Font) so Nerd Font
 * glyphs in [EmojiPalette] render as monochrome type. No colour-emoji
 * fallback — that would light the AMOLED with candy glyphs the picker
 * does not insert.
 */
object EmojiTypeface {

    fun apply(view: TextView) {
        try {
            val face = ResourcesCompat.getFont(view.context, R.font.font_body) ?: return
            view.typeface = face
        } catch (_: Exception) {
            // JVM unit tests have no font resources; leave the view's typeface.
        }
    }
}
