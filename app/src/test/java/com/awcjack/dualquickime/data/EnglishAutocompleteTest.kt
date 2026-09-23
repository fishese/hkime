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
        for (word in listOf("apple", "paint", "birthday", "computer", "family", "school")) {
            assertTrue(word, dictionary.contains(word))
            assertTrue(word, word in dictionary.completions(word))
        }
        assertTrue("apple" in dictionary.completions("appl"))
        assertTrue("birthday" in dictionary.completions("birthd"))
    }

    @Test fun autocompleteIsBoundedAndDoesNotTreatChineseCodesAsEnglishWords() {
        val dictionary = bundled()
        assertTrue(dictionary.completions("ca").size <= 8)
        assertEquals(emptyList<String>(), dictionary.completions("c"))
        assertEquals(emptyList<String>(), dictionary.completions("我a"))
        assertFalse(dictionary.contains("notarealenglishwordzz"))
    }
}
