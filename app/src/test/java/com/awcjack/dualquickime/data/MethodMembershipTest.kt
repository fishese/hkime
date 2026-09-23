package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MethodMembershipTest {
    private val membership = MethodMembership(sequenceOf(
        "ng\tcantonese\t唔吳",
        "ng\tquick\t魚唔",
        "abc\tcangjie\t甲𠮷",
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
}
