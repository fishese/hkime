package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnglishSuggestionsTest {
    @Test fun correctsOneClearEditWithoutChangingCase() {
        assertEquals("candidate", EnglishSuggestions.correction("canddiate"))
        assertEquals("Candidate", EnglishSuggestions.correction("Canddiate"))
    }

    @Test fun leavesValidOrUncertainWordsAlone() {
        assertNull(EnglishSuggestions.correction("candidate"))
        assertNull(EnglishSuggestions.correction("ng"))
        assertNull(EnglishSuggestions.correction("qxzrrr"))
    }

    @Test fun unicodeKeywordsAreExactEnglishOnly() {
        assertEquals(listOf("★", "☆"), UnicodeWordSuggestions.lookupEnglish("star"))
        assertEquals(emptyList<String>(), UnicodeWordSuggestions.lookupEnglish("sing"))
        assertEquals(listOf("[", "]", "(", ")", "{", "}", "«", "»", "「", "」", "『", "』", "【", "】"),
            UnicodeWordSuggestions.lookupEnglish("bracket"))
    }

    @Test fun symbolAlternativesKeepOpeningAndClosingBracketsSeparate() {
        assertEquals(true, "「" in SymbolAlternatives.forKey('['))
        assertEquals(true, "」" in SymbolAlternatives.forKey(']'))
        assertEquals(false, "」" in SymbolAlternatives.forKey('['))
        assertEquals(listOf("‘", "’", "′"), SymbolAlternatives.forKey('\''))
    }
}
