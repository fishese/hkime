package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeTypingDecoderTest {
    private val keys = mapOf(
        'c' to SwipeTypingDecoder.Point(0f, 0f),
        'a' to SwipeTypingDecoder.Point(1f, 0f),
        't' to SwipeTypingDecoder.Point(2f, 0f),
        'r' to SwipeTypingDecoder.Point(1f, 1f),
    )

    @Test fun ranksTheShapeBeforeOtherWords() {
        val trace = listOf(keys.getValue('c'), keys.getValue('a'), keys.getValue('t'))
        val result = SwipeTypingDecoder.decode(trace, keys, 1f, listOf("car", "cat"))
        assertEquals("cat", result?.code)
        assertEquals("cat", result?.words?.first())
    }

    @Test fun keepsAnUnknownPathAsAnInputCode() {
        val trace = listOf(keys.getValue('c'), keys.getValue('a'), keys.getValue('t'))
        assertEquals("cat", SwipeTypingDecoder.decode(trace, keys, 1f, emptyList())?.code)
    }

    @Test fun ignoresShortTouches() {
        assertNull(SwipeTypingDecoder.decode(listOf(keys.getValue('c')), keys, 1f, listOf("cat")))
    }
}
