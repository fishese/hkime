package com.awcjack.dualquickime.data

import android.content.ClipData

/** Automatic history never dereferences providers or retains source-marked secrets. */
internal object ClipboardCapture {
    fun text(clip: ClipData): String? {
        if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true || clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text ?: return null
        if (text.length !in ClipboardHistoryManager.MIN_TEXT_LENGTH..ClipboardHistoryManager.MAX_TEXT_LENGTH) return null
        return text.toString().takeUnless { it.isBlank() }
    }
}
