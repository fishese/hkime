package com.awcjack.dualquickime.data

/**
 * Space finishes Latin letters and can show a provisional separator immediately.
 * The separator stays with Latin text, numbers, or opening Latin punctuation.
 * Further digits stay together. Selecting Chinese removes the separator.
 * An explicit later space, once nothing is left to commit, still inserts one.
 */
object LatinSpaceCommit {
    fun insertsSpace(
        ignoreCommitSpace: Boolean, committingLatin: Boolean, candidateInsertedSpace: Boolean = false
    ): Boolean {
        return !candidateInsertedSpace && !(ignoreCommitSpace && committingLatin)
    }

    /** Hold the commit space only when both the swallow option and the restore option are on. */
    fun deferCommitSpace(
        ignoreCommitSpace: Boolean,
        restoreBetweenLatin: Boolean,
        committedRawLatin: Boolean
    ): Boolean {
        return ignoreCommitSpace && restoreBetweenLatin && committedRawLatin
    }

    /** Put a held space back before Latin text or the first digit of a number. */
    fun restoreDeferredSpace(
        restoreBetweenLatin: Boolean,
        deferred: Boolean,
        nextCommitIsLatin: Boolean,
        nextCommitIsNumber: Boolean = false
    ): Boolean {
        return restoreBetweenLatin && deferred && (nextCommitIsLatin || nextCommitIsNumber)
    }

    /** English words, contractions, and unrecognized text left in Latin letters. */
    fun keptAsLatin(text: String): Boolean {
        if (text.isEmpty()) return false
        var hasLatinLetter = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (codePoint in CJK_UNIFIED || codePoint in CJK_EXTENSION_A || codePoint in CJK_COMPATIBILITY ||
                codePoint in CJK_EXTENSION_B) return false
            val character = codePoint.toChar()
            if (character in 'a'..'z' || character in 'A'..'Z') hasLatinLetter = true
            index += Character.charCount(codePoint)
        }
        return hasLatinLetter
    }

    /** Keep an explicit word separator before opening Latin punctuation. */
    fun isOpeningPunctuation(before: String, next: String): Boolean = when (next) {
        "(", "[", "{", "\u201c" -> true
        "\"" -> before.count { it == '"' } % 2 == 0
        else -> false // Apostrophes can be contractions or possessives.
    }

    private val CJK_UNIFIED = 0x4E00..0x9FFF
    private val CJK_EXTENSION_A = 0x3400..0x4DBF
    private val CJK_COMPATIBILITY = 0xF900..0xFAFF
    private val CJK_EXTENSION_B = 0x20000..0x2A6DF
}
