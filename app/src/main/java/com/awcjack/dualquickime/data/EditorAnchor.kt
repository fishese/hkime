package com.awcjack.dualquickime.data

/** A suggestion or held space belongs to the caret and prefix that created it. */
internal data class EditorAnchor(val cursor: Int?, val prefix: String) {
    fun matches(currentCursor: Int?, selectedText: String?, textBeforeCursor: String?): Boolean =
        selectedText.isNullOrEmpty() &&
            (cursor == null || cursor == currentCursor) &&
            textBeforeCursor != null && textBeforeCursor.endsWith(prefix)
}
