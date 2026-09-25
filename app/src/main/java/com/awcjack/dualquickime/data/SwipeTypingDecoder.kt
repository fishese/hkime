package com.awcjack.dualquickime.data

import kotlin.math.hypot

/** Geometry-only swipe decoder. No text leaves the device. */
internal object SwipeTypingDecoder {
    data class Point(val x: Float, val y: Float)
    data class Result(val code: String, val words: List<String>)

    fun decode(
        trace: List<Point>,
        keys: Map<Char, Point>,
        keyWidth: Float,
        words: Collection<String>
    ): Result? {
        if (trace.size < 2 || keys.isEmpty() || keyWidth <= 0f) return null
        val traversed = trace.mapNotNull { point ->
            keys.minByOrNull { (_, center) -> distance(point, center) }
                ?.takeIf { (_, center) -> distance(point, center) <= keyWidth * 0.9f }?.key
        }.fold(StringBuilder()) { code, letter ->
            if (code.lastOrNull() != letter) code.append(letter)
            code
        }.toString()
        if (traversed.length < 2) return null

        val sampledTrace = resample(trace, 24)
        val start = traversed.first()
        val end = traversed.last()
        val ranked = words.asSequence()
            .filter { word ->
                word.length in 2..24 && word.all { it in 'a'..'z' } &&
                    word.first() == start && word.last() == end
            }
            .mapNotNull { word ->
                val wordPath = word.mapNotNull(keys::get)
                if (wordPath.size != word.length) null else {
                    val sampledWord = resample(wordPath, 24)
                    val deviation = sampledTrace.indices.sumOf { index ->
                        val d = distance(sampledTrace[index], sampledWord[index]) / keyWidth
                        (d * d).toDouble()
                    } / sampledTrace.size
                    word to (deviation + 0.025 * kotlin.math.abs(word.length - traversed.length))
                }
            }
            .sortedWith(compareBy<Pair<String, Double>> { it.second }.thenBy { it.first.length })
            .take(6)
            .toList()
        // A weak shape match should remain a literal key sequence instead of
        // inventing an unrelated English word or masking a Chinese input code.
        val plausible = ranked.filter { it.second < 0.85 }.map { it.first }
        return Result(plausible.firstOrNull() ?: traversed, plausible)
    }

    private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

    private fun resample(path: List<Point>, count: Int): List<Point> {
        if (path.size == 1) return List(count) { path.first() }
        val cumulative = FloatArray(path.size)
        for (index in 1 until path.size) {
            cumulative[index] = cumulative[index - 1] + distance(path[index - 1], path[index])
        }
        val total = cumulative.last()
        if (total == 0f) return List(count) { path.first() }
        var segment = 1
        return List(count) { sample ->
            val position = total * sample / (count - 1)
            while (segment < cumulative.lastIndex && cumulative[segment] < position) segment++
            val length = cumulative[segment] - cumulative[segment - 1]
            val ratio = if (length == 0f) 0f else (position - cumulative[segment - 1]) / length
            Point(
                path[segment - 1].x + (path[segment].x - path[segment - 1].x) * ratio,
                path[segment - 1].y + (path[segment].y - path[segment - 1].y) * ratio
            )
        }
    }
}
