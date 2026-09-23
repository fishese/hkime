package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedAssociatedPhrasesTest {
    private fun table(text: String) = CuratedAssociatedPhrases.parse(text.byteInputStream())

    private fun bundled() = File("src/main/assets/curated-associated-phrases.tsv")
        .inputStream().use(CuratedAssociatedPhrases::parse)

    @Test fun parsesUtf8TabSeparatedSuffixesIncludingLeadingSpaces() {
        val parsed = table("# comment\n影到我\t plz del\n回答我\t！\n")
        assertEquals(listOf("影到我" to " plz del"), parsed.lookup("剛剛影到我"))
        assertEquals(listOf("回答我" to "！"), parsed.lookup("回答我"))
    }

    @Test fun longestContextComesFirstWithoutDiscardingShorterMatches() {
        val parsed = table("來\t都來了\n本來\t應該\n")
        assertEquals(listOf("本來" to "應該", "來" to "都來了"), parsed.lookup("本來"))
    }

    @Test fun mergesSpecificCuratedWithMckWithoutLosingMckAndDeduplicates() {
        val parsed = table("影到我\t plz del\n我\t的\n影到我\t記得send返畀我\n")
        val merged = AssociatedPhraseSuggestions.merge("影到我", "我", parsed,
            { if (it == "我") listOf("的", "們", " plz del") else emptyList() }, { listOf("OV") })
        assertEquals(listOf(" plz del", "記得send返畀我", "的", "們"), merged)
    }

    @Test fun broadCuratedFollowsExistingAndOpenVanillaStillFallsBack() {
        val parsed = table("來\t都來了\n")
        assertEquals(listOf("了", "都來了"), AssociatedPhraseSuggestions.merge(
            "來", "來", parsed, { listOf("了") }, { listOf("OV") }))
        assertEquals(listOf("OV", "都來了"), AssociatedPhraseSuggestions.merge(
            "來", "來", parsed, { emptyList() }, { listOf("OV") }))
        assertEquals(listOf("OV"), AssociatedPhraseSuggestions.merge(
            "無", "無", parsed, { emptyList() }, { listOf("OV") }))
    }

    @Test fun bundledSuggestionsAppendOnlyTheUncommittedSuffix() {
        val curated = bundled()
        assertEquals("影到我 plz del", "影到我" + curated.lookup("影到我").first().second)
        assertEquals("我再 ven 一次", "我再" + curated.lookup("我再").first().second)
        assertEquals("回答我！", "回答我" + curated.lookup("回答我").first().second)
        assertEquals("露比醬～嗨！", "露比醬" + curated.lookup("露比醬").first().second)
        assertEquals("本來應該從從容容游刃有餘",
            "本來應該" + curated.lookup("本來應該").first().second)
    }

    @Test fun excludedContentIsAbsentFromBundledData() {
        val content = File("src/main/assets/curated-associated-phrases.tsv").readText()
        for (excluded in listOf("白卡", "白卡佬", "YBSG", "ㄅ級分", "SLS")) {
            assertFalse(excluded, content.contains(excluded))
        }
        assertTrue(bundled().lookup("高麗菜").size >= 2)
    }
}
