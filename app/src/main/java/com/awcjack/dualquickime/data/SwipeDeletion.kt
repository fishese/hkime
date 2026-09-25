package com.awcjack.dualquickime.data

/** Returns UTF-16 units to remove, or zero for the caller's grapheme fallback. */
internal object SwipeDeletion {
    fun lengthBeforeCursor(before: String, lastSelected: String): Int {
        if (lastSelected.isNotEmpty() && before.endsWith(lastSelected)) return lastSelected.length
        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && isLatinWordChar(before[start - 1])) start--
        return when {
            start < end -> before.length - start
            end < before.length -> before.length - end
            else -> 0
        }
    }

    private fun isLatinWordChar(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '\''
}
