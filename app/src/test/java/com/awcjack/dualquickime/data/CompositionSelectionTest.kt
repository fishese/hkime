package com.awcjack.dualquickime.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionSelectionTest {
    @Test fun caretAndSelectionInsideComposingTextRemainActive() {
        assertFalse(CompositionSelection.movedOutside(14, 14, 10, 14))
        assertFalse(CompositionSelection.movedOutside(12, 12, 10, 14))
        assertFalse(CompositionSelection.movedOutside(10, 14, 10, 14))
    }

    @Test fun movingOrSelectingOutsideFinishesComposition() {
        assertTrue(CompositionSelection.movedOutside(20, 20, 10, 14))
        assertTrue(CompositionSelection.movedOutside(2, 5, 10, 14))
        assertTrue(CompositionSelection.movedOutside(12, 20, 10, 14))
    }

    @Test fun invalidEditorRangesNeverTriggerCommit() {
        assertFalse(CompositionSelection.movedOutside(20, 20, -1, -1))
        assertFalse(CompositionSelection.movedOutside(20, 20, 10, 10))
        assertFalse(CompositionSelection.movedOutside(-1, -1, 10, 14))
    }
}
