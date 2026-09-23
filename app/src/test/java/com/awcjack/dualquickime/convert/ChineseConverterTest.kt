package com.awcjack.dualquickime.convert

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseConverterTest {
    @Test
    fun convertsBothDirectionsAndPreservesLatinText() {
        assertEquals("繁体中文 ABC", ChineseConverter.toSimplified("繁體中文 ABC"))
        assertEquals("簡體中文 ABC", ChineseConverter.toTraditional("简体中文 ABC"))
    }

    @Test
    fun autoDetectsTheDirection() {
        assertEquals("繁体", ChineseConverter.convertAuto("繁體"))
        assertEquals("簡體", ChineseConverter.convertAuto("简体"))
    }
}
