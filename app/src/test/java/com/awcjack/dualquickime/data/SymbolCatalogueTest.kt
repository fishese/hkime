package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolCatalogueTest {
    @Test fun usefulVariantsAndNamesLeadTheList() {
        assertEquals(listOf("＊", "星號", "asterisk", "star", "※", "★", "☆"),
            SymbolCatalogue.candidatesForSymbol("*").take(7))
        assertEquals(listOf("１", "一", "one", "壹", "①"),
            SymbolCatalogue.candidatesForSymbol("1").take(5))
    }

    @Test fun catalogueIsStableAndDeduplicated() {
        for (key in listOf("★", "[", "→", "￥", "，")) {
            val values = SymbolCatalogue.candidatesForSymbol(key)
            assertEquals(values, values.distinct())
            assertFalse(key in values)
        }
    }

    @Test fun everyVisibleSymbolPageKeyIsCovered() {
        val rows = listOf(
            "1234567890", "@#$&-+*/()", "<>×÷'!?",
            "~`|•√π§、“”", "£¢€¥^°=\\:;", "％‘’™℅[]",
            "「」，。：；", "！？—–_‖", "¦※·…±∞",
            "$¥€£¢₩₹฿₱₽", "%‰°℃℉≈≠", "≤≥∑∏†‡\"'©®",
            "←→↑↓↔↕⇐⇒⇑⇓", "▲▼◀▶◆◇□■△∆", "♠♣♥♦★☆♪"
        )
        for (key in rows.flatMap { it.toList() }.distinct()) {
            assertTrue("Missing symbol: $key", SymbolCatalogue.contains(key.toString()))
            assertTrue(SymbolCatalogue.candidatesForSymbol(key.toString()).isNotEmpty())
        }
    }

    @Test fun contextualPunctuationHasOppositeWidthFirst() {
        for (before in listOf("hello", "你好")) {
            val choice = ContextualPunctuation.choose(',', before)!!
            val candidates = (listOf(choice.alternative.toString()) +
                SymbolCatalogue.candidatesForSymbol(choice.inserted.toString())).distinct()
            assertEquals(choice.alternative.toString(), candidates.first())
            assertEquals("逗號", candidates[1])
        }
    }

    @Test fun pendingReplacementRequiresTheWholeInsertedTextAtCaret() {
        val pending = PendingSymbol("𨋢", listOf("升降機", "lift"))
        assertEquals(2, pending.insertedText.length) // one supplementary Unicode scalar
        assertTrue(pending.canReplace("lift", null, "𨋢"))
        assertFalse(pending.canReplace("lift", null, "?"))
        assertFalse(pending.canReplace("lift", "selected", "𨋢"))
        assertFalse(pending.canReplace("other", null, "𨋢"))
        assertTrue(PendingSymbol("👩‍💻", listOf("coder")).canReplace("coder", null, "👩‍💻"))
    }

    @Test fun exactEnglishKeywordsAreAppendedAfterOrdinaryWords() {
        assertTrue(SymbolCatalogue.lookupEnglish("star").take(2) == listOf("★", "☆"))
        assertTrue(SymbolCatalogue.lookupEnglish("sing").isEmpty())
        val ordinary = listOf("星", "star")
        assertEquals(ordinary, (ordinary + UnicodeWordSuggestions.lookupEnglish("star")).take(2))
    }
}
