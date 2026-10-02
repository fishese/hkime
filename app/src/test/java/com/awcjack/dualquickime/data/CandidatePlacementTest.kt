package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePlacementTest {
    private fun mergeTypoCandidates(typed: String, exact: List<String>, english: List<String>,
            chinese: List<String>, rank: (List<String>) -> List<String> = { it }): List<String> =
        com.awcjack.dualquickime.data.mergeTypoCandidates(typed, exact,
            english.mapIndexed { i, text -> TypoCandidate(text, text.lowercase(),
                MethodMembership.Method.ENGLISH, 2, sourceOrder = i) } +
            chinese.mapIndexed { i, text -> TypoCandidate(text, "ngo",
                MethodMembership.Method.CANTONESE, 2, sourceOrder = i) }, rank)

    @Test fun exactCharactersBeatRecoveryButExactPhraseShorthandDoesNot() {
        assertEquals(listOf("甲", "我", "餓", "年貨", "我哋"), mergeTypoCandidates(
            "nfo", listOf("年貨", "甲"), emptyList(), listOf("我", "餓", "我哋")))
    }

    @Test fun learnedPhraseCountsCannotPushShorthandAboveRecoveredCharacters() {
        assertEquals(listOf("我", "年貨"), mergeTypoCandidates(
            "nfo", listOf("年貨"), emptyList(), listOf("我")) { it.reversed() })
    }

    @Test fun fullEnglishWordAndSupplementaryCharacterAreProtectedAndDeduplicated() {
        assertEquals(listOf("𨋢", "often", "年貨"), mergeTypoCandidates(
            "often", listOf("年貨", "𨋢", "often"), listOf("Often"), listOf("𨋢", "年貨")))
    }

    @Test fun englishCorrectionsBeatPhraseShortcutsAndPrefixCompletions() {
        assertEquals(listOf("often", "年貨", "offer"), mergeTypoCandidates(
            "oftrm", listOf("年貨", "offer"), listOf("often"), emptyList()))
    }

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
