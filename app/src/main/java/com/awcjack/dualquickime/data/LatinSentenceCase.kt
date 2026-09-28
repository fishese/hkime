package com.awcjack.dualquickime.data

/**
 * Optional Latin sentence case. Only the first letter of a blank line, or the
 * letter after a full stop and a space, is capitalized. Proper nouns are ignored.
 */
object LatinSentenceCase {
    fun contextCapital(textBeforeCursor: String, composingLength: Int): Boolean {
        if (composingLength > 0) return false
        val line = textBeforeCursor.substringAfterLast('\n').substringAfterLast('\r')
        if (line.isBlank()) return true
        val end = line.trimEnd()
        if (end.length == line.length || end.isEmpty()) return false
        val mark = end.last()
        return mark == '.' || mark == '。'
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
