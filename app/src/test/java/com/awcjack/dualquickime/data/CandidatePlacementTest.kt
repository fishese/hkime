package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePlacementTest {
    @Test fun liftIsPlacedBeforePhrasesForCangjieAndQuick() {
        val original = listOf("甲", "升降機", "電梯", "𨋢", "乙")
        val expected = listOf("甲", "𨋢", "升降機", "電梯", "乙")
        assertEquals(expected, promoteReviewedCharacter("jjyt", original))
        assertEquals(expected, promoteReviewedCharacter("jt", original))
    }

    @Test fun otherCodesAndAlreadyHighCharactersAreUnchanged() {
        val original = listOf("甲", "升降機", "𨋢")
        assertEquals(original, promoteReviewedCharacter("lip", original))
        assertEquals(listOf("𨋢", "升降機"),
            promoteReviewedCharacter("jjyt", listOf("𨋢", "升降機")))
    }
}
