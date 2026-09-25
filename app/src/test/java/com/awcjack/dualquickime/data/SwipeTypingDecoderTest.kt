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

    private val qwerty = buildMap {
        "qwertyuiop".forEachIndexed { x, key -> put(key, SwipeTypingDecoder.Point(x.toFloat(), 0f)) }
        "asdfghjkl".forEachIndexed { x, key -> put(key, SwipeTypingDecoder.Point(x + 0.5f, 1f)) }
        "zxcvbnm".forEachIndexed { x, key -> put(key, SwipeTypingDecoder.Point(x + 1.5f, 2f)) }
    }

    private fun glide(code: String, stepMs: Long, pauseMs: Long = 0): List<SwipeTypingDecoder.Point> {
        val points = mutableListOf<SwipeTypingDecoder.Point>()
        var time = 0L
        for ((index, letter) in code.withIndex()) {
            val center = qwerty.getValue(letter)
            if (index > 0) {
                val previous = qwerty.getValue(code[index - 1])
                for (step in 1..12) {
                    time += stepMs
                    val fraction = step / 12f
                    points.add(SwipeTypingDecoder.Point(
                        previous.x + (center.x - previous.x) * fraction,
                        previous.y + (center.y - previous.y) * fraction, time))
                }
            } else points.add(center.copy(timeMs = time))
            if (pauseMs > 0) {
                time += pauseMs
                points.add(center.copy(timeMs = time))
            }
        }
        return points
    }

    @Test fun cantoneseCodeWinsOverKeysCrossedDuringSlowGlide() {
        val result = SwipeTypingDecoder.decode(glide("nei", 25, 95), qwerty, 1f,
            listOf("net", "new", "nib"), listOf("nei", "ng", "ngoi"))
        assertEquals("nei", result?.code)
        assertEquals("nei", result?.codes?.first())
    }

    @Test fun englishWordWinsOverKeysCrossedDuringSlowGlide() {
        val result = SwipeTypingDecoder.decode(glide("happy", 25, 95), qwerty, 1f,
            listOf("happy", "happen", "harpy"), listOf("hap", "hapy"))
        assertEquals("happy", result?.code)
        assertEquals("happy", result?.words?.first())
    }

    @Test fun unknownGlideDoesNotTypeEveryCrossedKey() {
        val result = SwipeTypingDecoder.decode(glide("nei", 25), qwerty, 1f,
            emptyList(), emptyList())
        assertEquals("nei", result?.code)
    }
}
