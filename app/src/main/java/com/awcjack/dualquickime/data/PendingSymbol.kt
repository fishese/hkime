package com.awcjack.dualquickime.data

/** A committed symbol may be replaced only while it remains directly before the caret. */
data class PendingSymbol(val insertedText: String, val alternatives: List<String>) {
    fun canReplace(candidate: String, selectedText: String?, beforeCursor: String?): Boolean =
        candidate in alternatives && selectedText.isNullOrEmpty() && beforeCursor == insertedText
}
