package com.awcjack.dualquickime.data

import kotlin.math.abs
import kotlin.math.hypot

/** Geometry-only swipe decoder. No text leaves the device. */
internal object SwipeTypingDecoder {
    data class Point(val x: Float, val y: Float, val timeMs: Long = 0L)
    data class Result(val code: String, val words: List<String>, val codes: List<String> = emptyList())

    fun decode(
        trace: List<Point>,
        keys: Map<Char, Point>,
        keyWidth: Float,
        words: Collection<String>,
        codes: Collection<String> = emptyList()
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
        val first = trace.first()
        val last = trace.last()
        val anchors = dwellAnchors(trace, keys, keyWidth)
        val ranked = (codes.asSequence().map { it to false } + words.asSequence().map { it to true })
            .filter { (word, _) ->
                word.length in 2..24 && word.all { it in 'a'..'z' } &&
                    keys[word.first()]?.let { distance(first, it) <= keyWidth * 1.25f } == true &&
                    keys[word.last()]?.let { distance(last, it) <= keyWidth * 1.25f } == true
            }
            .distinct()
            .mapNotNull { (word, english) ->
                val wordPath = word.mapNotNull(keys::get)
                if (wordPath.size != word.length) null else {
                    val sampledWord = resample(wordPath, 24)
                    val deviation = sampledTrace.indices.sumOf { index ->
                        val d = distance(sampledTrace[index], sampledWord[index]) / keyWidth
                        (d * d).toDouble()
                    } / sampledTrace.size
                    val endpoints = (distance(first, wordPath.first()) + distance(last, wordPath.last())) /
                        keyWidth * 0.3
                    val missingAnchors = anchors.count { it !in word } * 0.5
                    val geometry = deviation + endpoints + missingAnchors +
                        0.018 * abs(word.length - anchors.size.coerceAtLeast(2)) -
                        if (english) 0.05 else 0.0
                    // A repeated letter adds no movement. Count it only when the
                    // finger never paused on that key, so "mo" beats "moo" while
                    // a real pause on "happy" still counts.
                    val dwells = anchors.toSet()
                    val unpausedRepeats = word.zipWithNext().count { (a, b) -> a == b && b !in dwells }
                    Scored(word, english, geometry, geometry + unpausedRepeats * 0.12)
                }
            }
            .sortedWith(compareBy<Scored> { it.rank }.thenBy { it.word.length })
            .take(20)
            .toList()
        // Transit keys are not typed keys. On a weak match, retain only turns in
        // the stroke rather than dumping every key crossed into the editor.
        val fallback = simplify(trace, keyWidth * 0.35f).mapNotNull { point ->
            keys.minByOrNull { (_, center) -> distance(point, center) }?.key
        }.fold(StringBuilder()) { code, letter ->
            if (code.lastOrNull() != letter) code.append(letter)
            code
        }.toString().let { if (traversed.length <= 3) traversed else it.ifEmpty { traversed } }
        val bestGeometry = ranked.minOfOrNull { it.geometry } ?: Double.POSITIVE_INFINITY
        val plausible = ranked.filter { it.geometry < 1.0 && it.geometry <= bestGeometry + 0.28 }
            .sortedWith(compareBy<Scored> { it.rank }.thenBy { it.word.length })
        val best = plausible.firstOrNull()?.word ?: fallback
        return Result(best,
            plausible.filter { it.english }.map { it.word }.distinct().take(6),
            plausible.filterNot { it.english }.map { it.word }.distinct().take(8))
    }

    private data class Scored(val word: String, val english: Boolean, val geometry: Double, val rank: Double)

    private fun dwellAnchors(trace: List<Point>, keys: Map<Char, Point>, width: Float): List<Char> {
        val anchors = mutableListOf<Char>()
        var index = 0
        while (index < trace.size) {
            val key = keys.minByOrNull { (_, center) -> distance(trace[index], center) }
            if (key == null || distance(trace[index], key.value) > width * 0.45f) {
                index++
                continue
            }
            var end = index + 1
            // Remaining near a key while the finger is moving slowly is not a
            // deliberate pause. Require little actual movement as well as time.
            while (end < trace.size && distance(trace[end], trace[index]) <= width * 0.18f) end++
            if (trace[end - 1].timeMs - trace[index].timeMs >= 90 && anchors.lastOrNull() != key.key)
                anchors.add(key.key)
            index = end
        }
        return anchors
    }

    private fun simplify(points: List<Point>, tolerance: Float): List<Point> {
        if (points.size <= 2) return points
        val start = points.first()
        val end = points.last()
        val length = distance(start, end)
        var furthest = 0f
        var at = 0
        for (index in 1 until points.lastIndex) {
            val p = points[index]
            val deviation = if (length == 0f) distance(start, p) else
                abs((end.x - start.x) * (start.y - p.y) -
                    (start.x - p.x) * (end.y - start.y)) / length
            if (deviation > furthest) { furthest = deviation; at = index }
        }
        if (furthest <= tolerance) return listOf(start, end)
        return simplify(points.subList(0, at + 1), tolerance).dropLast(1) +
            simplify(points.subList(at, points.size), tolerance)
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
