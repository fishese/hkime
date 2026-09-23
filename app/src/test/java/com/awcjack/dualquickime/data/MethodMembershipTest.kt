package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MethodMembershipTest {
    private val membership = MethodMembership(sequenceOf(
        "ng\tcantonese\t唔吳",
        "ng\tquick\t魚唔",
        "abc\tcangjie\t甲𠮷",
        "a\tcangjie\t日時間問",
        "buhu\tcangjie\t見",
        "aombc\tcangjie\t題",
        "aam\tcantonese\t啱",
        "fan\tcantonese\t返",
        "fong\tcantonese\t放",
        "gaam\tcantonese\t監",
    ))

    @Test fun keepsOriginalOrderWhenAllMethodsEnabled() {
        assertEquals(listOf("魚", "唔", "phrase", "吳"), membership.filter(
            "ng", listOf("魚", "唔", "phrase", "吳"), MethodMembership.Method.values().toSet()))
    }

    @Test fun overlappingCharacterSurvivesIfEitherMethodEnabled() {
        assertEquals(listOf("唔", "吳"), membership.filter(
            "ng", listOf("魚", "唔", "phrase", "吳"),
            setOf(MethodMembership.Method.CANTONESE)))
    }

    @Test fun exclusiveCodeRetainsUnclassifiedPhrases() {
        assertEquals(listOf("甲", "甲乙", "𠮷"), membership.filter(
            "abc", listOf("甲", "甲乙", "𠮷"),
            setOf(MethodMembership.Method.CANGJIE)))
    }

    @Test fun disabledMethodsDoNotLeakUnknownKeys() {
        assertEquals(emptyList<String>(), membership.filter(
            "unknown", listOf("字"), setOf(MethodMembership.Method.CANTONESE)))
    }

    @Test fun identifiesCangjiePhraseInitialsWithoutDirectCodeEntry() {
        assertEquals(listOf("日日見"), membership.filter("aab", listOf("日日見", "未分類"),
            setOf(MethodMembership.Method.CANGJIE)))
    }

    @Test fun identifiesFullAndQuickFormOfFinalCharacter() {
        assertEquals(listOf("時間問題"), membership.filter("aaaac", listOf("時間問題"),
            setOf(MethodMembership.Method.CANGJIE)))
        assertEquals(listOf("時間問題"), membership.filter("aaaaombc", listOf("時間問題"),
            setOf(MethodMembership.Method.CANGJIE)))
    }

    @Test fun identifiesCantoneseInitialsAndFinalSyllable() {
        val cantoneseOnly = setOf(MethodMembership.Method.CANTONESE)
        assertEquals(listOf("啱啱返"), membership.filter("aafan", listOf("啱啱返"), cantoneseOnly))
        assertEquals(listOf("啱啱放監"), membership.filter("aafg", listOf("啱啱放監"), cantoneseOnly))
    }
}
