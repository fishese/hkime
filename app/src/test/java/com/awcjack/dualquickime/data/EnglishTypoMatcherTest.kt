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
        assertTrue(matcher.suggestions("oftrm").size <= 4)
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
