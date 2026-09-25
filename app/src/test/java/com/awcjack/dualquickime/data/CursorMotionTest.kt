package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorMotionTest {
    @Test fun dragStepsFollowFingerDirection() {
        assertEquals(0, CursorMotion.stepsForDrag(10f, 16f))
        assertEquals(2, CursorMotion.stepsForDrag(32f, 16f))
        assertEquals(-1, CursorMotion.stepsForDrag(-16f, 16f))
    }

    @Test fun movesByGraphemesIncludingEmoji() {
        val text = "a你👍b"
        val emoji = text.indexOf("👍")
        val letterB = text.indexOf('b')
        assertEquals(letterB, CursorMotion.offsetByGraphemes(text, text.length, -1))
        assertEquals(emoji, CursorMotion.offsetByGraphemes(text, text.length, -2))
        assertEquals(text.length, CursorMotion.offsetByGraphemes(text, emoji, 2))
        assertEquals(0, CursorMotion.offsetByGraphemes(text, 1, -5))
    }
}
