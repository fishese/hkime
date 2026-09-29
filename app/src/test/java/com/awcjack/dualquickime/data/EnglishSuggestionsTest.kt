package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishSuggestionsTest {
    @Test fun correctsOneClearEditWithoutChangingCase() {
        assertEquals("candidate", EnglishSuggestions.correction("canddiate"))
        assertEquals("Candidate", EnglishSuggestions.correction("Canddiate"))
    }

    @Test fun correctsCommonSpellingTargetsFromTheBrainstorm() {
        assertEquals("accommodation", EnglishSuggestions.correction("accomodation"))
        assertEquals("necessary", EnglishSuggestions.correction("neccessary"))
        assertEquals("Definitely", EnglishSuggestions.correction("Definately"))
    }

    @Test fun keepsTestingAsAWordAndCorrectsAMissingLetter() {
        assertNull(EnglishSuggestions.correction("testing"))
        assertEquals("testing", EnglishSuggestions.correction("testng"))
        assertTrue("testing" in EnglishSuggestions.completions("testi"))
    }

    @Test fun leavesValidOrUncertainWordsAlone() {
        assertNull(EnglishSuggestions.correction("candidate"))
        assertNull(EnglishSuggestions.correction("ng"))
        assertNull(EnglishSuggestions.correction("qxzrrr"))
    }

    @Test fun neighbourKeyTyposSuggestAdjacentAndTransposedWords() {
        val dictionary = setOf("plain")
        val isWord = { word: String -> word in dictionary }
        assertEquals(listOf("the"), EnglishSuggestions.neighbourKeyCorrections("teh", isWord))
        assertEquals(listOf("the"), EnglishSuggestions.neighbourKeyCorrections("rhe", isWord))
        assertEquals(listOf("THE"), EnglishSuggestions.neighbourKeyCorrections("RHE", isWord))
        assertEquals(listOf("plain"), EnglishSuggestions.neighbourKeyCorrections("plajn", isWord))
        assertEquals(listOf("plain"), EnglishSuggestions.neighbourKeyCorrections("olaun", isWord))
    }

    @Test fun neighbourKeyTyposLeaveRealWordsAndPrefixesAlone() {
        assertEquals(emptyList<String>(), EnglishSuggestions.neighbourKeyCorrections("the", { false }))
        assertEquals(emptyList<String>(), EnglishSuggestions.neighbourKeyCorrections("out", { false }))
        assertEquals(
            emptyList<String>(),
            EnglishSuggestions.neighbourKeyCorrections("rhe", { false }, { true })
        )
        assertEquals(emptyList<String>(), EnglishSuggestions.neighbourKeyCorrections("tEh", { false }))
    }

    @Test fun suggestsCommonApostropheContractionsWithoutTypingApostrophe() {
        assertEquals("don't", EnglishSuggestions.contractions("dont").first())
        assertTrue("don't" in EnglishSuggestions.contractions("don"))
        assertEquals("hadn't", EnglishSuggestions.contractions("hadn").first())
        assertEquals("Don't", EnglishSuggestions.contractions("Dont").first())
        assertEquals("DON'T", EnglishSuggestions.contractions("DONT").first())
    }

    @Test fun unicodeKeywordsAreExactEnglishOnly() {
        assertEquals(listOf("★", "☆"), UnicodeWordSuggestions.lookupEnglish("star").take(2))
        assertEquals(emptyList<String>(), UnicodeWordSuggestions.lookupEnglish("sing"))
        assertTrue(listOf("[", "]", "(", ")", "<", ">").all {
            it in UnicodeWordSuggestions.lookupEnglish("bracket")
        })
    }

    @Test fun englishCompletionsAreSmallAndPreserveSimpleCase() {
        assertTrue("candidate" in EnglishSuggestions.completions("cand"))
        assertTrue("Candidate" in EnglishSuggestions.completions("Cand"))
        assertTrue("CAN" in EnglishSuggestions.completions("CA"))
        assertTrue(EnglishSuggestions.completions("ca").size <= 8)
        assertEquals(emptyList<String>(), EnglishSuggestions.completions("c"))
        assertEquals(emptyList<String>(), EnglishSuggestions.completions("我a"))
    }

    @Test fun englishWordsCanBeLearnedWithoutPromotingThemInitially() {
        val base = listOf("甲", "乙", "candidate")
        assertEquals(base, rankCandidates(base, emptyMap()))
        assertEquals("candidate", rankCandidates(base, mapOf("candidate" to 2)).first())
        assertTrue(EnglishSuggestions.isLatinWord("Candidate"))
        assertFalse(EnglishSuggestions.isLatinWord("http://"))
    }

    @Test fun animalsAndCardinalArrowsHaveUnicodeChoices() {
        assertTrue("🐈" in UnicodeWordSuggestions.lookupEnglish("cat"))
        assertTrue("🐕" in UnicodeWordSuggestions.lookupEnglish("dog"))
        assertTrue("🐟" in UnicodeWordSuggestions.lookupEnglish("fish"))
        assertEquals(listOf("↑", "↓", "←", "→", "↔"), UnicodeWordSuggestions.lookupEnglish("arrow").take(5))
    }

    @Test fun symbolAlternativesKeepOpeningAndClosingBracketsSeparate() {
        assertEquals(true, "「" in SymbolAlternatives.forKey('['))
        assertEquals(true, "」" in SymbolAlternatives.forKey(']'))
        assertEquals(false, "」" in SymbolAlternatives.forKey('['))
        assertEquals(true, "<" in SymbolAlternatives.forKey('['))
        assertEquals(true, "＜" in SymbolAlternatives.forKey('['))
        assertEquals(true, ">" in SymbolAlternatives.forKey(']'))
        assertEquals(true, "＞" in SymbolAlternatives.forKey(']'))
        assertEquals(false, ">" in SymbolAlternatives.forKey('['))
        assertEquals(false, "<" in SymbolAlternatives.forKey(']'))
        assertEquals(true, "<" in UnicodeWordSuggestions.lookupEnglish("bracket"))
        assertEquals(true, "＞" in UnicodeWordSuggestions.lookupEnglish("bracket"))
        assertEquals(listOf("‘", "’", "單引號", "撇號"), SymbolAlternatives.forKey('\'').take(4))
    }

    @Test fun bracketAlternativesRankSimilarShapesFirst() {
        assertEquals(listOf("＜", "〈", "《", "«"), SymbolAlternatives.forKey('<').take(4))
        assertEquals(listOf("＞", "〉", "》", "»"), SymbolAlternatives.forKey('>').take(4))
        assertEquals(listOf("（", "{", "["), SymbolAlternatives.forKey('(').take(3))
        assertEquals(listOf("）", "}", "]"), SymbolAlternatives.forKey(')').take(3))
        assertEquals(listOf("【", "{", "("), SymbolAlternatives.forKey('[').take(3))
        assertEquals(listOf("】", "}", ")"), SymbolAlternatives.forKey(']').take(3))
        assertEquals(listOf("『", "“", "‘", "【"), SymbolAlternatives.forKey('「').take(4))
        assertEquals(listOf("』", "”", "’", "】"), SymbolAlternatives.forKey('」').take(4))
        assertFalse(">" in SymbolAlternatives.forKey('<'))
        assertFalse("<" in SymbolAlternatives.forKey('>'))
        assertEquals(SymbolAlternatives.forKey('「').distinct(), SymbolAlternatives.forKey('「'))
    }
}
