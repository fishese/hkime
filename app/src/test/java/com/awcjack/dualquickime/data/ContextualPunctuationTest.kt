package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextualPunctuationTest {
    @Test fun latinTextDefaultsToHalfWidth() {
        assertEquals(ContextualPunctuation.Choice(',', '，'),
            ContextualPunctuation.choose('，', "hello"))
        assertEquals(ContextualPunctuation.Choice('.', '。'),
            ContextualPunctuation.choose('。', "café "))
        assertEquals(ContextualPunctuation.Choice('?', '？'),
            ContextualPunctuation.choose('？', "abc!"))
        assertEquals(ContextualPunctuation.Choice(':', '：'),
            ContextualPunctuation.choose('：', "123"))
    }

    @Test fun chineseTextDefaultsToFullWidth() {
        assertEquals(ContextualPunctuation.Choice('，', ','),
            ContextualPunctuation.choose(',', "你好"))
        assertEquals(ContextualPunctuation.Choice('。', '.'),
            ContextualPunctuation.choose('.', "你好 "))
        assertEquals(ContextualPunctuation.Choice('！', '!'),
            ContextualPunctuation.choose('!', "你好。"))
    }

    @Test fun longPressCanForceTheOtherWidth() {
        assertEquals(ContextualPunctuation.Choice('，', ','),
            ContextualPunctuation.choose('，', "hello", forceLiteral = true))
        assertEquals(ContextualPunctuation.Choice('.', '。'),
            ContextualPunctuation.choose('.', "你好", forceLiteral = true))
    }

    @Test fun nonPairedSymbolsAreUnchanged() {
        assertNull(ContextualPunctuation.choose('@', "hello"))
    }
}
