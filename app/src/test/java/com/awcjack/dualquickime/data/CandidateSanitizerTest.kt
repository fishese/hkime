package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateSanitizerTest {
    @Test
    fun removesEmptyWhitespaceAndDuplicateEntries() {
        assertEquals(
            listOf("你", "a"),
            sanitizeCandidates(listOf("", "你", " ", "\u200B", "a", "你"))
        )
    }
}
