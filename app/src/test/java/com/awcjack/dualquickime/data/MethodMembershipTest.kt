package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MethodMembershipTest {
    private val membership = MethodMembership(sequenceOf(
        "ng\tcantonese\t唔吳",
        "ng\tquick\t魚唔",
        "abc\tcangjie\t甲𠮷",
        "a\tcangjie\t日時間問",
        "aa\tcangjie\t昌",
        "a\tcantonese\t啊阿呀亞",
        "aa\tcantonese\t啊阿呀亞",
        "buhu\tcangjie\t見",
        "hbln\tcangjie\t制",
        "aombc\tcangjie\t題",
        "aam\tcantonese\t啱",
        "fan\tcantonese\t返",
        "fong\tcantonese\t放",
        "gaam\tcantonese\t監",
        "jai\tcantonese\t制",
        "ji\tcantonese\t支姿治",
        "jing\tcantonese\t整政",
        "juen\tcantonese\t專",
        "chai\tcantonese\t齊",
    ), sequenceOf(
        "lip\tcantonese\t𨋢",
        "jjyt\tcangjie\t𨋢",
        "jt\tquick\t𨋢",
        "lift\tenglish\t𨋢",
        "zzzz\tcantonese\t整整齊齊",
        "aac\tcangjie\t時間表",
        "aac\tquick\t時間表",
        "abv\tenglish\t納姆迪·阿齊基韋國際機場",
        "si\tcantonese\t豉",
        "mrmt\tcangjie\t豉",
        "mt\tquick\t豉",
        "soy\tenglish\t豉",
        "aby\tcangjie\t合肥市",
        "aby\tquick\t合肥市",
        "be\tcangjie\t是",
        "abc\tcantonese\t🔤",
        "abc\tcangjie\t🔤",
        "abc\tquick\t🔤",
        "abc\tenglish\t🔤",
    ))

    @Test fun keepsOriginalOrderWhenAllMethodsEnabled() {
        assertEquals(listOf("魚", "唔", "phrase", "吳"), membership.filter(
            "ng", listOf("魚", "唔", "phrase", "吳"), MethodMembership.Method.values().toSet()))
    }

    @Test fun overlappingCharacterSurvivesIfEitherMethodEnabled() {
        assertEquals(listOf("唔", "吳"), membership.filter(
            "ng", listOf("魚", "唔", "phrase", "吳"),
            setOf(MethodMembership.Method.CANTONESE), false))
    }

    @Test fun codeOnlyHintsLeaveUnmatchedPhrasesUncertain() {
        assertEquals(listOf("甲", "甲乙", "𠮷"), membership.filter(
            "abc", listOf("甲", "甲乙", "𠮷"),
            setOf(MethodMembership.Method.CANGJIE)))
        assertEquals(listOf("甲", "𠮷"), membership.filter(
            "abc", listOf("甲", "甲乙", "𠮷"),
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun disabledMethodsDoNotLeakUnknownKeys() {
        assertEquals(listOf("字"), membership.filter(
            "unknown", listOf("字"), setOf(MethodMembership.Method.CANTONESE)))
        assertEquals(emptyList<String>(), membership.filter(
            "unknown", listOf("字"), setOf(MethodMembership.Method.CANTONESE), false))
    }

    @Test fun identifiesCangjiePhraseInitialsWithoutDirectCodeEntry() {
        assertEquals(listOf("日日見"), membership.filter("aab", listOf("日日見", "未分類"),
            setOf(MethodMembership.Method.CANGJIE), false))
        assertEquals(listOf("日日見"), membership.filter("aab", listOf("日日見", "未分類"),
            setOf(MethodMembership.Method.QUICK), false))
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

    @Test fun latinLeadingPhraseKeepsItsTextAndUsesTheMatchingMethod() {
        val phrase = listOf("AA制")
        assertEquals(phrase, membership.filter("aaj", phrase,
            setOf(MethodMembership.Method.CANTONESE)))
        assertEquals(phrase, membership.filter("aajai", phrase,
            setOf(MethodMembership.Method.CANTONESE)))
        assertEquals(phrase, membership.filter("aah", phrase,
            setOf(MethodMembership.Method.CANGJIE)))
        assertEquals(phrase, membership.filter("aahbln", phrase,
            setOf(MethodMembership.Method.QUICK)))
        assertEquals(emptyList<String>(), membership.filter("aaj", phrase,
            setOf(MethodMembership.Method.CANGJIE), false))
        assertEquals(emptyList<String>(), membership.filter("aaj", listOf("aaj"),
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun aAndAaHaveTheFourRequestedCantoneseCandidates() {
        val choices = listOf("日", "啊", "阿", "呀", "亞", "昌")
        val cantonese = setOf(MethodMembership.Method.CANTONESE)
        assertEquals(listOf("啊", "阿", "呀", "亞"), membership.filter("a", choices, cantonese, false))
        assertEquals(listOf("啊", "阿", "呀", "亞"), membership.filter("aa", choices, cantonese, false))
        assertEquals(listOf("日"), membership.filter("a", choices,
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun zShorthandUsesCantoneseJReadingsAndKeepsManualException() {
        val cantonese = setOf(MethodMembership.Method.CANTONESE)
        val choices = listOf("支支整整", "專制政治", "整整齊齊", "姿姿整整")
        assertEquals(choices, membership.filter("zzzz", choices, cantonese))
        assertEquals(listOf("支支整整", "姿姿整整"),
            membership.filter("zzzzing", choices, cantonese, false))
        assertEquals(emptyList<String>(), membership.filter("zzzz", choices,
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun uncertainCanBeHiddenAndOverlappingMethodsNeverDuplicateCandidates() {
        val mixed = listOf("日日見", "日日見", "未分類")
        assertEquals(listOf("日日見", "未分類"), membership.filter("aab", mixed,
            setOf(MethodMembership.Method.CANGJIE, MethodMembership.Method.QUICK)))
        assertEquals(listOf("日日見"), membership.filter("aab", mixed,
            setOf(MethodMembership.Method.QUICK), false))
    }

    @Test fun reviewedMappingsObeyMethodSwitches() {
        assertEquals(listOf("時間表"), membership.filter("aac", listOf("時間表"),
            setOf(MethodMembership.Method.QUICK), false))
        val airport = listOf("納姆迪·阿齊基韋國際機場")
        assertEquals(airport, membership.filter("abv", airport,
            setOf(MethodMembership.Method.ENGLISH), false))
        assertEquals(emptyList<String>(), membership.filter("abv", airport,
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun englishInflectionsMissingFromBundledShardsUseEnglishOnly() {
        val english = setOf(MethodMembership.Method.ENGLISH)
        val additions = listOf(
            "apples" to "蘋果",
            "asked" to "問",
            "clipboard" to "剪貼簿",
            "painted" to "上色",
            "would" to "會",
        )
        val lines = additions.map { (code, candidate) -> "$code\tenglish\t$candidate" }
        val supplemented = MethodMembership(emptySequence(), lines.asSequence())
        for ((code, candidate) in additions) {
            assertEquals(listOf(candidate), supplemented.supplementalCandidates(code, english))
            assertEquals(emptyList<String>(), supplemented.supplementalCandidates(
                code, setOf(MethodMembership.Method.CANTONESE)))
        }
    }

    @Test fun suppliedCharacterAppearsEvenIfBundledShardLacksIt() {
        for ((code, method) in listOf(
            "si" to MethodMembership.Method.CANTONESE,
            "mrmt" to MethodMembership.Method.CANGJIE,
            "mt" to MethodMembership.Method.QUICK,
            "soy" to MethodMembership.Method.ENGLISH,
        )) {
            assertEquals(listOf("豉"), membership.supplementalCandidates(code, setOf(method)))
            assertEquals(emptyList<String>(), membership.supplementalCandidates(code, emptySet()))
        }
    }

    @Test fun liftCharacterUsesTheRequestedMethodsOnly() {
        for ((code, method) in listOf(
            "lip" to MethodMembership.Method.CANTONESE,
            "jjyt" to MethodMembership.Method.CANGJIE,
            "jt" to MethodMembership.Method.QUICK,
            "lift" to MethodMembership.Method.ENGLISH,
        )) {
            assertEquals(listOf("𨋢"), membership.supplementalCandidates(code, setOf(method)))
            assertEquals(emptyList<String>(), membership.supplementalCandidates(code, emptySet()))
        }
    }

    @Test fun reviewedMultiCharacterChangjieIsAlsoQuickButSingleCharacterIsNot() {
        assertEquals(listOf("合肥市"), membership.filter("aby", listOf("合肥市"),
            setOf(MethodMembership.Method.QUICK), false))
        assertEquals(emptyList<String>(), membership.filter("be", listOf("是"),
            setOf(MethodMembership.Method.QUICK), false))
        assertEquals(listOf("是"), membership.filter("be", listOf("是"),
            setOf(MethodMembership.Method.CANGJIE), false))
    }

    @Test fun reviewedAllSymbolAppearsForEveryEnabledMethod() {
        for (method in MethodMembership.Method.values()) {
            assertEquals(listOf("🔤"), membership.filter("abc", listOf("🔤"),
                setOf(method), false))
        }
    }
}
