package com.piercingxx.txxt.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.widget.EditText
import kotlin.math.roundToInt

/**
 * Paints a Nerd Font glyph over a colour-emoji cluster in an [EditText]
 * without rewriting the underlying characters, so send still uses real
 * Unicode. ReplacementSpan is the non-janky path: the IME, cursor, and
 * selection keep emoji offsets; only draw() changes.
 */
class EmojiNerdSpan(private val glyph: String) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        fm?.let { paint.getFontMetricsInt(it) }
        return paint.measureText(glyph).roundToInt().coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        canvas.drawText(glyph, x, y.toFloat(), paint)
    }
}

object EmojiNerdCompose {

    fun bind(editText: EditText) {
        apply(editText.text)
        editText.addTextChangedListener(object : TextWatcher {
            private var applying = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (applying) return
                applying = true
                try {
                    apply(s)
                } finally {
                    applying = false
                }
            }
        })
    }

    fun apply(editable: Editable?) {
        if (editable == null) return
        editable.getSpans(0, editable.length, EmojiNerdSpan::class.java)
            .forEach { editable.removeSpan(it) }
        for (span in EmojiNerdFont.glyphSpans(editable.toString())) {
            editable.setSpan(
                EmojiNerdSpan(span.glyph),
                span.start,
                span.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}
