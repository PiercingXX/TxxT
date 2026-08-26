package com.piercingxx.txxt.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText

/**
 * Sentence-start capitals in the compose field, including the first character.
 *
 * `textShortMessage` (TYPE_TEXT_VARIATION_SHORT_MESSAGE) makes Gboard and
 * other IMEs ignore TYPE_TEXT_FLAG_CAP_SENTENCES, so the first letter of a
 * draft stayed lowercase. This watcher is the IME-independent half: any
 * letter that starts the field or follows `.` `!` `?` is uppercased in
 * place, without rewriting the rest of the draft (emoji spans stay put).
 */
object SentenceCapitalizer {

    fun inputType(): Int =
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT

    fun bind(editText: EditText) {
        editText.inputType = inputType()
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

    fun capitalize(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var pending = true
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            when {
                Character.isWhitespace(cp) -> out.appendCodePoint(cp)
                pending && Character.isLetter(cp) -> {
                    out.appendCodePoint(Character.toUpperCase(cp))
                    pending = false
                }
                else -> {
                    out.appendCodePoint(cp)
                    if (isSentenceEnd(cp)) pending = true
                }
            }
            i += len
        }
        return out.toString()
    }

    fun apply(editable: Editable?) {
        if (editable == null || editable.isEmpty()) return
        var pending = true
        var i = 0
        while (i < editable.length) {
            val cp = Character.codePointAt(editable, i)
            val len = Character.charCount(cp)
            when {
                Character.isWhitespace(cp) -> i += len
                pending && Character.isLetter(cp) -> {
                    val upper = Character.toUpperCase(cp)
                    if (upper != cp && Character.charCount(upper) == len) {
                        editable.replace(i, i + len, String(Character.toChars(upper)))
                    }
                    pending = false
                    i += Character.charCount(Character.codePointAt(editable, i))
                }
                else -> {
                    if (isSentenceEnd(cp)) pending = true
                    i += len
                }
            }
        }
    }

    private fun isSentenceEnd(cp: Int): Boolean =
        cp == '.'.code || cp == '!'.code || cp == '?'.code
}
