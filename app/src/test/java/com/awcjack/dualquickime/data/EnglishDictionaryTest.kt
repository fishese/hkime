package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishDictionaryTest {
    @Test fun parsesMethodSpecificTranslationsCaseInsensitively() {
        val dictionary = EnglishDictionary.parse(
            "# comment\nsuggest\t建議\napples\t蘋果\n".byteInputStream()
        )
        assertEquals(listOf("建議"), dictionary.lookup("Suggest"))
        assertEquals(listOf("蘋果"), dictionary.lookup("apples"))
        assertEquals(emptyList<String>(), dictionary.lookup("missing"))
    }

    @Test fun bundledDictionaryCoversCommonEnglishSuggestions() {
        val dictionary = File("src/main/assets/english-dictionary.tsv")
            .inputStream().use(EnglishDictionary::parse)
        assertTrue(dictionary.size >= 15_000)
        assertEquals(listOf("建議"), dictionary.lookup("suggest"))
        assertEquals(listOf("蘋果"), dictionary.lookup("apple"))
        assertEquals(listOf("香蕉"), dictionary.lookup("banana"))
        assertEquals(listOf("學校"), dictionary.lookup("school"))
    }
}
