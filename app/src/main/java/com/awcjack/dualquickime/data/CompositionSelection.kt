package com.awcjack.dualquickime.data

/** A valid composing range makes IME-generated caret updates distinguishable from a move away. */
object CompositionSelection {
    fun movedOutside(newStart: Int, newEnd: Int, composingStart: Int, composingEnd: Int): Boolean {
        if (composingStart < 0 || composingEnd <= composingStart ||
            newStart < 0 || newEnd < newStart) return false
        return newStart < composingStart || newEnd > composingEnd
    }
}
