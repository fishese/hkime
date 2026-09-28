package com.awcjack.dualquickime.data

/**
 * Inserts one space after half-width punctuation when the next input starts a
 * word. Further punctuation stays attached, as in "???" and "?!".
 */
object PunctuationSpacing {
    private val triggers = setOf('.', ',', '!', '?', ';', ':', '…')
    private val closers = setOf(
        '"', '\'', '”', '’',
        ')', '）', ']', '］', '}', '｝',
        '」', '』',
    )

    fun needsSpaceBefore(beforeCursor: String, nextText: String): Boolean {
        if (!startsWithWord(nextText)) return false
        if (beforeCursor.isEmpty() || beforeCursor.last().isWhitespace()) return false
        var end = beforeCursor
        while (end.isNotEmpty() && end.last() in closers) end = end.dropLast(1)
        return end.isNotEmpty() && end.last() in triggers
    }

    private fun startsWithWord(text: String): Boolean {
        if (text.isEmpty()) return false
        val codePoint = text.codePointAt(0)
        if (Character.isDigit(codePoint)) return false
        if (Character.isLetter(codePoint)) return true
        val block = Character.UnicodeBlock.of(codePoint) ?: return false
        return block == Character.UnicodeBlock.EMOTICONS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS ||
            block == Character.UnicodeBlock.DINGBATS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
    }
}
