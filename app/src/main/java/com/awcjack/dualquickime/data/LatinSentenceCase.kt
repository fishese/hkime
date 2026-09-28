package com.awcjack.dualquickime.data

/**
 * Optional Latin sentence case. The first letter of a blank line, or the
 * letter after sentence-ending punctuation, is capitalized. The space before
 * that word may already be typed, or it is inserted with the word.
 * Proper nouns are ignored.
 */
object LatinSentenceCase {
    private val sentenceEnders = setOf(
        '.', '。', '．', '｡',
        '!', '！', '‼',
        '?', '？', '⁇', '⁈', '⁉', '‽',
        '…',
    )
    private val closingMarks = setOf(
        '"', '\'', '”', '’', '»',
        ')', '）', ']', '］', '}', '｝',
        '」', '』', '〉', '》', '】', '〕',
    )

    fun contextCapital(textBeforeCursor: String, composingLength: Int): Boolean {
        if (composingLength > 0) return false
        val line = textBeforeCursor.substringAfterLast('\n').substringAfterLast('\r')
        if (line.isBlank()) return true
        var end = line.trimEnd()
        while (end.isNotEmpty() && end.last() in closingMarks) {
            end = end.dropLast(1)
        }
        return end.isNotEmpty() && end.last() in sentenceEnders
    }

    fun typedIsUpper(
        capsLock: Boolean,
        manualShift: Boolean,
        sentenceCase: Boolean,
        contextCapital: Boolean,
    ): Boolean {
        if (capsLock) return true
        if (!sentenceCase) return manualShift
        return manualShift xor contextCapital
    }

    fun keyShowsUpper(
        capsLock: Boolean,
        manualShift: Boolean,
        sentenceCase: Boolean,
        contextCapital: Boolean,
    ): Boolean {
        if (!sentenceCase) return true
        return typedIsUpper(capsLock, manualShift, sentenceCase = true, contextCapital)
    }

    fun letter(char: Char, upper: Boolean): Char =
        if (upper) char.uppercaseChar() else char.lowercaseChar()
}
