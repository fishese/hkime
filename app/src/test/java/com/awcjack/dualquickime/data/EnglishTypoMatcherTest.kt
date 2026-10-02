package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EnglishTypoMatcherTest {
    private fun bundled(): EnglishTypoMatcher {
        val words = File("src/main/assets/english-autocomplete.txt")
            .inputStream().use(EnglishAutocomplete::parse).allWords()
        return EnglishTypoMatcher(words, EnglishSuggestions.typoWords())
    }

    @Test fun shortDictionaryTyposRetainAllReportedAlternatives() {
        val matcher = bundled()
        assertTrue("sang" in matcher.suggestions("sanf"))
        assertTrue(matcher.suggestions("stah").containsAll(listOf("stab", "stag", "stay")))
    }

    @Test fun longDictionaryTyposRemainAvailable() {
        val matcher = bundled()
        for ((typed, target) in listOf("compiter" to "computer", "keyboarf" to "keyboard",
                "infornation" to "information", "accommofation" to "accommodation",
                "counterrevolutionart" to "counterrevolutionary",
                "polytetrafluoroethyleme" to "polytetrafluoroethylene",
                "dichlorodiphenyltrichloroethanr" to "dichlorodiphenyltrichloroethane")) {
            assertTrue(typed, target in matcher.suggestions(typed))
        }
    }

    @Test fun uncommonFourLetterCorrectionsDoNotBroadenErrorBudgetsOrFuzzyPrefixes() {
        val matcher = EnglishTypoMatcher(listOf("lark", "larkin"), emptySet())
        assertEquals(listOf("lark"), matcher.suggestions("larm"))
        assertEquals(listOf("lark"), matcher.suggestions("lakr"))
        assertTrue(matcher.suggestions("larq").isEmpty())
        assertTrue(matcher.suggestions("oarm").isEmpty())
        assertTrue(EnglishTypoMatcher(listOf("larkin"), emptySet()).suggestions("larm").isEmpty())
        assertTrue(EnglishTypoMatcher(listOf("the"), emptySet()).suggestions("teh").isEmpty())
    }

    @Test fun supportedLengthBoundariesAllowOneExtraKeyButKeepBoundedEdits() {
        val word = "a".repeat(32)
        val matcher = EnglishTypoMatcher(listOf(word), emptySet())
        for (position in listOf(0, 16, 31)) {
            val typo = word.replaceRange(position, position + 1, "s")
            assertEquals(listOf(word), matcher.suggestions(typo))
        }
        assertEquals(listOf(word), matcher.suggestions(word + "a"))
        assertTrue(matcher.suggestions(word + "aa").isEmpty())
        assertTrue(matcher.suggestions("p" + word.drop(2) + "q").isEmpty())
    }

    @Test fun completeAndFuzzyResultsHaveIndependentAllowancesAndPreserveCase() {
        val direct = KeyProximity.codeVariants("compit", false).keys.sorted().take(12)
        val matcher = EnglishTypoMatcher(direct + listOf("computer", "computers", "computing",
            "computerize", "computations"), emptySet())
        val results = matcher.matches("compit")
        assertEquals(8, results.count { !it.completion })
        assertEquals(4, results.count { it.completion })
        assertTrue(results.take(8).none { it.completion })
        assertTrue("STAY" in bundled().suggestions("STAH"))
        assertTrue("Stay" in bundled().suggestions("Stah"))
        assertTrue(bundled().suggestions("sTaH").isEmpty())
        for (word in listOf("stay", "sang", "computer", "information")) {
            assertTrue(word, bundled().suggestions(word).isEmpty())
        }
    }

    @Test fun userExamplesWorkAgainstActualBundledVocabulary() {
        val matcher = bundled()
        assertTrue("oftrm", "often" in matcher.suggestions("oftrm"))
        assertTrue("sohnds", "sounds" in matcher.suggestions("sohnds"))
        assertTrue("SOUNDS" in matcher.suggestions("SOHNDS"))
        assertTrue("Sounds" in matcher.suggestions("Sohnds"))
    }

    @Test fun retainsExistingAdjacentAndTranspositionExamples() {
        val matcher = EnglishTypoMatcher(listOf("plain"), EnglishSuggestions.typoWords())
        assertTrue("the" in matcher.suggestions("teh"))
        assertTrue("the" in matcher.suggestions("rhe"))
        assertTrue("plain" in matcher.suggestions("plajn"))
        assertTrue("plain" in matcher.suggestions("olaun"))
    }

    @Test fun supportsMissingExtraAndRepeatedLettersOutsideSmallCommonSet() {
        val matcher = EnglishTypoMatcher(listOf("volcano"), emptySet())
        for (typo in listOf("volcno", "volccano", "volcanpo")) {
            assertEquals(typo, listOf("volcano"), matcher.suggestions(typo))
        }
    }

    @Test fun unfinishedTyposHaveBoundedFuzzyCompletions() {
        val matcher = EnglishTypoMatcher(listOf("computer", "computers", "computing"), emptySet())
        assertTrue("computer" in matcher.suggestions("compit"))
        assertTrue("computers" in matcher.suggestions("compit"))
        assertTrue(matcher.matches("compit").all { it.completion })
    }

    @Test fun twoDistantEditsAreRejectedButTwoNearbySlipsAreAllowed() {
        val matcher = EnglishTypoMatcher(listOf("often"), emptySet())
        assertEquals(listOf("often"), matcher.suggestions("oftrm"))
        assertEquals(emptyList<String>(), matcher.suggestions("ofpqn"))
    }

    @Test fun validWordsIdentifiersAndTinyInputsAreNotCorrected() {
        val matcher = bundled()
        for (text in listOf("out", "our", "often", "sounds", "tEh", "ng", "我a", "https://")) {
            assertEquals(text, emptyList<String>(), matcher.suggestions(text))
        }
        assertTrue(matcher.suggestions("oftrm").size <= 12)
    }

    @Test fun ambiguousMisspellingsReturnAlternativesRatherThanNothing() {
        val matcher = EnglishTypoMatcher(listOf("volcano", "volcans"), emptySet())
        assertEquals(setOf("volcano", "volcans"), matcher.suggestions("volcanp").toSet())
    }

    @Test fun resultsAreDeterministicAndDoNotRequireTheFirstKeyToBeCorrect() {
        val matcher = EnglishTypoMatcher(listOf("computer"), emptySet())
        assertEquals(listOf("computer"), matcher.suggestions("vomputer"))
        assertEquals(matcher.suggestions("vomputer"), matcher.suggestions("vomputer"))
    }
}
