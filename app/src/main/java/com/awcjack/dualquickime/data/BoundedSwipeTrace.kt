package com.awcjack.dualquickime.data

/** Bounded move history; retain endpoints, representative timing and the sharpest turns. */
internal class BoundedSwipeTrace(initial: List<SwipeTypingDecoder.Point> = emptyList()) : AbstractList<SwipeTypingDecoder.Point>() {
    private val points = ArrayList<SwipeTypingDecoder.Point>(MAX_POINTS)
    override val size: Int get() = points.size
    override fun get(index: Int): SwipeTypingDecoder.Point = points[index]
    init { initial.forEach(::add) }
    fun add(point: SwipeTypingDecoder.Point) {
        if (points.size == MAX_POINTS) {
            val keep = mutableSetOf(0, points.lastIndex)
            // Uniform samples retain dwell times; turn samples retain shape.
            for (index in 1 until points.lastIndex step 4) keep.add(index)
            val turns = (1 until points.lastIndex).sortedByDescending { index ->
                val a = points[index - 1]; val b = points[index]; val c = points[index + 1]
                kotlin.math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
            }
            keep.addAll(turns.take(MAX_POINTS / 4))
            val retained = keep.sorted().map(points::get)
            points.clear(); points.addAll(retained)
        }
        points.add(point)
    }
    companion object { const val MAX_POINTS = 512 }
}
