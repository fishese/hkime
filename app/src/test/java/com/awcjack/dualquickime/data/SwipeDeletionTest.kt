package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeDeletionTest {
    @Test fun deletesTheLastSelectedPhraseAsAUnit() {
        assertEquals(4, SwipeDeletion.lengthBeforeCursor("你好香港", "你好香港"))
    }

    @Test fun deletesThePreviousEnglishWordAndTrailingSpace() {
        assertEquals(6, SwipeDeletion.lengthBeforeCursor("hello world ", ""))
    }

    @Test fun fallsBackToOneGraphemeForChineseText() {
        assertEquals(0, SwipeDeletion.lengthBeforeCursor("你好", ""))
    }
}
