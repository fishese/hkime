package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishAutocompleteTest {
    private fun bundled() = File("src/main/assets/english-autocomplete.txt")
        .inputStream().use(EnglishAutocomplete::parse)

    @Test fun parsesSortedDeduplicatedWordsAndPreservesSimpleCase() {
        val dictionary = EnglishAutocomplete.parse("paint\napple\npaint\nBirthday\nbirthday\n".byteInputStream())
        assertEquals(3, dictionary.size)
        assertEquals(listOf("Paint"), dictionary.completions("Pain"))
        assertEquals(listOf("BIRTHDAY"), dictionary.completions("BIRTH"))
    }

    @Test fun bundledEnglishCoverageIncludesEveryRequestedExample() {
        val dictionary = bundled()
        assertTrue(dictionary.size >= 17_000)
        for (word in listOf(
            "app", "apple", "autocorrect", "booking", "forms", "inventory", "organise",
            "occurred", "pairs", "supporting", "users", "voicemail",
            "paint", "birthday", "computer", "family", "school", "testing"
        )) {
            assertTrue(word, dictionary.contains(word))
            assertTrue(word, word in dictionary.completions(word))
        }
        assertTrue("apple" in dictionary.completions("appl"))
        assertTrue("birthday" in dictionary.completions("birthd"))
        assertFalse(dictionary.contains("birdofparadise"))
        assertFalse("birdofparadise" in dictionary.completions("bird"))
    }

    @Test fun commonWordsRankAheadOfRareWordsWithTheSamePrefix() {
        val dictionary = EnglishAutocomplete.parse(
            "app\napparatus\napple\napplication\napply\n".byteInputStream()
        )
        assertEquals(listOf("app", "apple", "apply", "application", "apparatus"),
            dictionary.completions("app"))
    }

    @Test fun simplePluralCompletionsAreHiddenUntilThePluralIsFullyTyped() {
        val dictionary = EnglishAutocomplete.parse(
            "apple\napples\nbird\nbirds\nbox\nboxes\ndo\ndoes\ngo\ngoes\nnew\nnews\n".byteInputStream()
        )

        assertTrue("apple" in dictionary.completions("app"))
        assertFalse("apples" in dictionary.completions("app"))
        assertTrue("bird" in dictionary.completions("bi"))
        assertFalse("birds" in dictionary.completions("bi"))
        assertFalse("boxes" in dictionary.completions("box"))
        assertEquals(listOf("apples"), dictionary.completions("apples"))
        assertEquals(listOf("birds"), dictionary.completions("birds"))
        assertTrue("does" in dictionary.completions("do"))
        assertTrue("goes" in dictionary.completions("go"))
        assertTrue("news" in dictionary.completions("new"))
    }

    @Test fun prefixThresholdUsesTheSortedDictionary() {
        val dictionary = EnglishAutocomplete.parse(
            "the\ntheir\nthem\nthen\nthere\nthose\n".byteInputStream()
        )
        assertTrue(dictionary.hasAtLeastCompletions("th", 6))
        assertFalse(dictionary.hasAtLeastCompletions("th", 7))
        assertTrue(dictionary.hasAtLeastCompletions("the", 5))
        assertFalse(dictionary.hasAtLeastCompletions("the", 6))
    }

    @Test fun autocompleteIsBoundedAndDoesNotTreatChineseCodesAsEnglishWords() {
        val dictionary = bundled()
        assertTrue(dictionary.completions("ca").size <= 8)
        assertEquals(emptyList<String>(), dictionary.completions("c"))
        assertEquals(emptyList<String>(), dictionary.completions("我a"))
        assertFalse(dictionary.contains("notarealenglishwordzz"))
    }
}
