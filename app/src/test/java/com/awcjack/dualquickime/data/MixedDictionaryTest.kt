package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MixedDictionaryTest {
    @Test
    fun decodesTraditionalSingleCharactersAndPhrases() {
        val encoded = "萬麥2盲啞\u0000𣿫\b万麦2盲哑"
        assertEquals(listOf("萬", "麥", "盲啞", "𣿫"), decodeMckCandidates(encoded, simplified = false))
    }

    @Test
    fun decodesSimplifiedSection() {
        val encoded = "萬麥2盲啞\u0000𣿫\b万麦2盲哑"
        assertEquals(listOf("万", "麦", "盲哑"), decodeMckCandidates(encoded, simplified = true))
    }

    @Test
    fun keepsEncodedEnglishCandidatesWhole() {
        assertEquals(listOf("竹", "http://", "https://"), decodeMckCandidates("竹7http://8https://", false))
    }
}
