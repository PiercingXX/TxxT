package com.piercingxx.txxt.ui

import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.piercingxx.txxt.R

/**
 * Applies the bundled body face (JetBrains Mono Nerd Font) so [EmojiNerdFont]
 * glyphs paint as monochrome type instead of falling through to a colour-emoji
 * font.
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
