package com.awcjack.dualquickime.data

import java.text.BreakIterator

/** Grapheme-aware caret movement for the space-bar drag gesture. */
internal object CursorMotion {
    fun stepsForDrag(dxPx: Float, stepPx: Float): Int =
        if (stepPx <= 0f) 0 else (dxPx / stepPx).toInt()

    fun offsetByGraphemes(text: String, index: Int, delta: Int): Int {
        val pos = index.coerceIn(0, text.length)
        if (delta == 0 || text.isEmpty()) return pos
        val breaks = BreakIterator.getCharacterInstance()
        breaks.setText(text)
        var cursor = pos
        var remaining = kotlin.math.abs(delta)
        if (delta < 0) {
            while (remaining > 0) {
                val previous = breaks.preceding(cursor)
                if (previous == BreakIterator.DONE) break
                cursor = previous
                remaining--
            }
        } else {
            while (remaining > 0) {
                val next = breaks.following(cursor)
                if (next == BreakIterator.DONE) break
                cursor = next
                remaining--
            }
        }
        return cursor
    }
}
