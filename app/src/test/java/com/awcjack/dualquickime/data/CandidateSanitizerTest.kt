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
}
