package com.awcjack.dualquickime.data

/**
 * Inserts one space after half-width punctuation when the next input starts a
 * word or emoji. Further punctuation stays attached, as in "???" and "?!".
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
        return shouldOfferSpaceAfter(beforeCursor)
    }

    /** Whether a provisional space should be shown immediately after this text. */
    fun shouldOfferSpaceAfter(textBeforeCursor: String): Boolean {
        if (textBeforeCursor.isEmpty() || textBeforeCursor.last().isWhitespace()) return false
        var end = textBeforeCursor
        while (end.isNotEmpty() && end.last() in closers) end = end.dropLast(1)
        return end.isNotEmpty() && end.last() in triggers
    }

    private fun startsWithWord(text: String): Boolean {
        if (text.isEmpty()) return false
        val codePoint = text.codePointAt(0)
        if (Character.isDigit(codePoint)) return false
        if (Character.isLetter(codePoint)) return true
        // These ranges avoid UnicodeBlock fields unavailable on older Android versions.
        return codePoint in 0x2600..0x26FF || // Miscellaneous Symbols.
            codePoint in 0x2700..0x27BF || // Dingbats.
            codePoint in 0x1F1E6..0x1F1FF || // Regional indicators used in flags.
            codePoint in 0x1F300..0x1F5FF || // Miscellaneous Symbols and Pictographs.
            codePoint in 0x1F600..0x1F64F || // Emoticons.
            codePoint in 0x1F680..0x1F6FF || // Transport and Map Symbols.
            codePoint in 0x1F900..0x1F9FF // Supplemental Symbols and Pictographs.
    }
}
