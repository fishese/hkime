package com.awcjack.dualquickime.data

import org.junit.Assert.*
import org.junit.Test

class TypoCandidatePlacementTest {
    private fun english(text: String, cost: Int = 2, completion: Boolean = false, order: Int = 0) =
        TypoCandidate(text, text.lowercase(), MethodMembership.Method.ENGLISH, cost, completion, order)
    private fun chinese(text: String, code: String, cost: Int = 2,
            method: MethodMembership.Method = MethodMembership.Method.CANTONESE) =
        TypoCandidate(text, code, method, cost)

    @Test fun allThreeStahCorrectionsBeatPhraseShorthandAndCoexistWithChinese() {
        val fixes = listOf("stab", "stag", "stay").mapIndexed { i, word -> english(word, order = i) }
        val base = listOf("中文片語", "另一片語")
        for (chinese in listOf(emptyList(), listOf(chinese("甲", "abc")))) {
            val result = mergeTypoCandidates("stah", base, fixes + chinese)
            assertTrue(result.take(4).containsAll(listOf("stab", "stag", "stay")))
            assertTrue(result.indexOf("stay") < result.indexOf(base.first()))
            assertTrue(result.containsAll(base))
        }
    }

    @Test fun sanfKeepsBothLanguagesAndCodeDiversityWithoutLosingTheTail() {
        val fixes = listOf(english("sand"), english("sang", order = 1),
            chinese("生", "sang"), chinese("省", "sang"), chinese("甲", "samg"))
        val result = mergeTypoCandidates("sanf", listOf("片語"), fixes)
        assertTrue(result.take(4).containsAll(listOf("sand", "sang", "生", "甲")))
        assertTrue(result.indexOf("甲") < result.indexOf("省"))
        assertTrue(result.containsAll(listOf("省", "片語")))
    }

    @Test fun exactProvenanceCaseAndConfidenceSurviveLearningAndDeduplication() {
        val base = listOf("𨋢", "STAY", "片語", "completion")
        val fixes = listOf(english("stay"), english("lower", 2), english("weaker", 4),
            english("fuzzy", 2, true), chinese("𨋢", "abc"), chinese("甲乙", "abc"))
        val result = mergeTypoCandidates("stay", base, fixes) { it.reversed() }
        assertEquals(listOf("𨋢", "STAY"), result.take(2))
        assertFalse("stay" in result)
        assertTrue(result.indexOf("lower") < result.indexOf("weaker"))
        assertTrue(result.indexOf("weaker") < result.indexOf("fuzzy"))
        assertTrue(result.indexOf("fuzzy") < result.indexOf("甲乙"))
        assertEquals(base, mergeTypoCandidates("stay", base, emptyList()))
    }

    @Test fun weakerCangjieGroupsAreLimitedAfterLearningWhileStrongGroupsAreRetained() {
        val weak = (1..10).map { chinese(String(Character.toChars(0x4e00 + it)), "code$it", 3,
            MethodMembership.Method.CANGJIE) }
        val strong = (1..12).map { chinese(String(Character.toChars(0x5000 + it)), "strong$it") }
        val result = mergeTypoCandidates("typo", emptyList(), weak + strong) { listOf(weak.last().text) + it.filterNot { text -> text == weak.last().text } }
        assertEquals(8, result.count { it in weak.map { fix -> fix.text } })
        assertTrue(result.containsAll(strong.map { it.text }))
        assertTrue(weak.last().text in result)
    }

    @Test fun duplicateCharactersDoNotUsePromotionSlotsAndMethodsGetRepresentatives() {
        val fixes = listOf(chinese("我", "ngo"), chinese("餓", "ngo"),
            chinese("我", "abc", method = MethodMembership.Method.CANGJIE),
            chinese("查", "dam", method = MethodMembership.Method.CANGJIE),
            chinese("生", "sang"), chinese("省", "sang"))
        val result = mergeTypoCandidates("typo", emptyList(), fixes)
        assertEquals(5, result.size)
        assertTrue(result.take(3).containsAll(listOf("我", "查", "生")))
    }
}
