package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorMotionTest {
    @Test fun dragStepsFollowFingerDirection() {
        assertEquals(0, CursorMotion.stepsForDrag(10f, 16f))
        assertEquals(2, CursorMotion.stepsForDrag(32f, 16f))
        assertEquals(-1, CursorMotion.stepsForDrag(-16f, 16f))
    }

    @Test fun dragStepsTrackHorizontalAndVerticalMovement() {
        assertEquals(
            CursorMotion.DragSteps(horizontal = 2, vertical = -1),
            CursorMotion.stepsForDrag(32f, -24f, 16f, 24f)
        )
    }

    @Test fun verticalDragUsesAHigherMovementThreshold() {
        assertEquals(
            CursorMotion.DragSteps(horizontal = 1, vertical = 0),
            CursorMotion.stepsForDrag(18f, 18f, 18f, 24f)
        )
        assertEquals(
            CursorMotion.DragSteps(horizontal = 1, vertical = 1),
            CursorMotion.stepsForDrag(18f, 24f, 18f, 24f)
        )
    }

    @Test fun verticalMovementStaysInsideMultilineInputBounds() {
        assertEquals(false, CursorMotion.canMoveVertically(-1, false, "text", "text"))
        assertEquals(false, CursorMotion.canMoveVertically(-1, true, "", "text"))
        assertEquals(false, CursorMotion.canMoveVertically(1, true, "text", ""))
        assertEquals(true, CursorMotion.canMoveVertically(-1, true, "text", "text"))
        assertEquals(true, CursorMotion.canMoveVertically(1, true, "text", "text"))
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
