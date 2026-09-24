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
            "←→↑↓↔↕⇐⇒⇑⇓", "▲▼◀▶◆◇Ω■△∆", "♠♣♥♦★╬♪"
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

    @Test fun visibleNamesStayShortWithoutLosingBasicTerms() {
        val star = SymbolCatalogue.candidatesForSymbol("★")
        assertTrue("星" in star)
        assertTrue("star" in star)
        assertFalse("星星" in star)
        assertFalse("實心星" in star)
        assertFalse("black star" in star)

        val left = SymbolCatalogue.candidatesForSymbol("←")
        assertTrue(listOf("左", "左箭咀", "left", "left arrow").all { it in left })
        assertFalse("向左" in left)

        val square = SymbolCatalogue.candidatesForSymbol("■")
        assertTrue("正方型" in square)
        assertTrue("square" in square)
        assertFalse("實心方形" in square)
    }

    @Test fun punctuationAndMathKeysOfferTheRequestedVariants() {
        assertEquals(listOf("❗", "❢", "❣", "！", "‼"),
            SymbolCatalogue.candidatesForSymbol("!").take(5))
        assertTrue(listOf("‡", "✙", "✚", "✛", "✜", "✞", "✟", "✠", "✢", "✣", "✥", "➕", "﹢", "＋")
            .all { it in SymbolCatalogue.candidatesForSymbol("+") })
        assertTrue(listOf("¼", "½", "¾", "⅓", "⅔", "／")
            .all { it in SymbolCatalogue.candidatesForSymbol("/") })
    }

    @Test fun combinedStarAndSquareKeysKeepTheirOutlineCandidates() {
        assertEquals("☆", SymbolCatalogue.candidatesForSymbol("★").first())
        assertEquals("□", SymbolCatalogue.candidatesForSymbol("■").first())
    }

    @Test fun greekAndBoxDrawingKeysExposeCommonCharacters() {
        val greek = SymbolCatalogue.candidatesForSymbol("Ω")
        assertTrue(listOf("α", "β", "π", "ω", "Δ", "Σ", "Ψ").all { it in greek })
        val box = SymbolCatalogue.candidatesForSymbol("╬")
        assertTrue(listOf("─", "│", "┌", "┼", "═", "║", "█", "░", "▒", "▓")
            .all { it in box })
    }

    @Test fun romanNumeralsOnlyAppearForExistingUnicodeNumberForms() {
        assertTrue("Ⅰ" in SymbolCatalogue.candidatesForSymbol("1"))
        assertEquals(listOf("Ⅻ", "ⅻ"), SymbolCatalogue.candidatesForSymbol("12"))
        assertTrue(SymbolCatalogue.candidatesForSymbol("13").isEmpty())
        assertEquals(listOf("Ⅼ", "ⅼ"), SymbolCatalogue.candidatesForSymbol("50"))
        assertEquals(listOf("Ⅰ", "ⅰ"), SymbolCatalogue.lookupEnglish("i"))
        assertEquals(listOf("Ⅱ", "ⅱ"), SymbolCatalogue.lookupEnglish("ii"))
        assertEquals(listOf("Ⅷ", "ⅷ"), SymbolCatalogue.lookupEnglish("viii"))
        assertTrue(SymbolCatalogue.lookupEnglish("xiii").isEmpty())
    }
}
