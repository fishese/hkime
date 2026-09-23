package com.awcjack.dualquickime.data

/** Chooses punctuation width from the text immediately before the cursor. */
object ContextualPunctuation {
    data class Choice(val inserted: Char, val alternative: Char)

    private val halfToFull = mapOf(
        ',' to '，',
        '.' to '。',
        '!' to '！',
        '?' to '？',
        ':' to '：',
        ';' to '；'
    )
    private val fullToHalf = halfToFull.entries.associate { (half, full) -> full to half }

    fun choose(key: Char, beforeCursor: String, forceLiteral: Boolean = false): Choice? {
        val half = if (key in halfToFull) key else fullToHalf[key] ?: return null
        val full = halfToFull.getValue(half)
        val inserted = when {
            forceLiteral -> key
            followsLatin(beforeCursor) -> half
            else -> full
        }
        return Choice(inserted, if (inserted == half) full else half)
    }

    private fun followsLatin(beforeCursor: String): Boolean {
        val preceding = beforeCursor.lastOrNull { !it.isWhitespace() } ?: return false
        return Character.UnicodeScript.of(preceding.code) == Character.UnicodeScript.LATIN ||
            preceding in '0'..'9' || preceding in halfToFull
    }
}
