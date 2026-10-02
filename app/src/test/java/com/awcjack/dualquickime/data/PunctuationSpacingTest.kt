package com.awcjack.dualquickime.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationSpacingTest {
    @Test fun spacesBeforeAWordAfterHalfWidthPunctuation() {
        assertTrue(PunctuationSpacing.needsSpaceBefore("hello.", "Therefore"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("hello?", "你"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("wait!", "😀"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("hello,", "world"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("note:", "this"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("done…", "next"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("hello.\"", "Next"))
    }

    @Test fun keepsPunctuationTogetherAndSkipsChineseMarks() {
        assertFalse(PunctuationSpacing.needsSpaceBefore("hello.", "?"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("???", "?"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("hello?", "!"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("hello.", "14"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("well-", "known"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("hello. ", "Therefore"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("你好。", "你"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("你好，", "hello"))
        assertFalse(PunctuationSpacing.needsSpaceBefore("", "hello"))
    }

    @Test fun latinQuotesAndBracketsSeparateWordsWithoutBreakingContractions() {
        assertTrue(PunctuationSpacing.needsSpaceBefore("say \"hello\"", "world"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("say (hello)", "world"))
        assertTrue(PunctuationSpacing.needsSpaceBefore("hello.", "\""))
        assertFalse(PunctuationSpacing.shouldOfferSpaceAfter("say \""))
        assertFalse(PunctuationSpacing.shouldOfferSpaceAfter("don't"))
        assertFalse(PunctuationSpacing.shouldOfferSpaceAfter("\"你好\""))
    }

    @Test fun spacesBeforeEmojiFromEachSupportedUnicodeRange() {
        listOf("☀", "✈", "🇭🇰", "🗺", "😀", "🚀", "🦄").forEach { emoji ->
            assertTrue(emoji, PunctuationSpacing.needsSpaceBefore("Done.", emoji))
        }
    }
}
