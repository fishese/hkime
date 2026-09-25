package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateSanitizerTest {
    @Test
    fun removesInvisibleAndDuplicateEntriesWithoutHidingVisibleText() {
        assertEquals(
            listOf("你", "a", "e\u0301", "👩\u200D💻"),
            sanitizeCandidates(listOf(
                "", "你", " ", "\u00A0", "\u200B", "\u0301", "\uFE0F",
                "\uDB40\uDD00", "a", "你", "e\u0301", "👩\u200D💻"
            ))
        )
    }

    @Test
    fun removesBlockedPhraseFromDirectAndAssociatedSuggestions() {
        assertEquals(
            listOf("其他"),
            sanitizeCandidates(listOf("愛國愛港", "爱国爱港", "其他"))
        )
        assertEquals(
            listOf("愛國"),
            sanitizeAssociatedPhraseCandidates("愛國", listOf("愛港", "愛國"))
        )
    }
}
