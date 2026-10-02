package com.awcjack.dualquickime.data

import kotlin.math.pow

/** Local continuation counts with a 30-day half-life. All lengths count Unicode characters. */
class LearnedPhraseModel(entries: List<Entry> = emptyList()) {
    data class Entry(val prefix: String, val next: String, val score: Double, val updated: Long)
    private val data = linkedMapOf<Pair<String, String>, Entry>()

    init {
        entries.filter { isChinese(it.prefix) && characters(it.prefix).size <= MAX_CONTEXT &&
            isChinese(it.next) && characters(it.next).size == 1 &&
            it.score.isFinite() && it.score > 0 && it.updated >= 0
        }.take(MAX_ENTRIES).forEach { data[it.prefix to it.next] = it }
    }

    fun recordAppend(context: String, selected: String, now: Long) {
        if (!isChinese(selected) || now < 0) return
        var preceding = if (isChinese(context)) characters(context).takeLast(MAX_CONTEXT) else emptyList()
        for (next in characters(selected)) {
            for (length in 1..preceding.size) {
                val prefix = preceding.takeLast(length).joinToString("")
                val key = prefix to next
                val previous = data[key]
                data[key] = Entry(prefix, next,
                    (previous?.let { weight(it, now) } ?: 0.0) + 1.0,
                    maxOf(now, previous?.updated ?: now))
            }
            preceding = (preceding + next).takeLast(MAX_CONTEXT)
        }
        prune(now)
    }

    fun suggestions(context: String, now: Long, limit: Int = 16): List<String> {
        if (!isChinese(context) || limit <= 0) return emptyList()
        val result = linkedSetOf<String>()
        val chars = characters(context).takeLast(MAX_CONTEXT)
        // Specific context beats a popular continuation of just the final character.
        for (length in chars.size downTo 1) {
            val prefix = chars.takeLast(length).joinToString("")
            data.values.filter { it.prefix == prefix && weight(it, now) >= MIN_SCORE }
                .sortedWith(ranking(now)).forEach { result.add(it.next) }
        }
        return result.take(limit)
    }

    fun entries(): List<Entry> = data.values.toList()

    private fun prune(now: Long) {
        data.entries.removeAll { weight(it.value, now) < MIN_SCORE }
        data.values.groupBy { it.prefix }.values.forEach { entries ->
            entries.sortedWith(ranking(now)).drop(MAX_PER_PREFIX).forEach { data.remove(it.prefix to it.next) }
        }
        data.values.sortedWith(ranking(now)).drop(MAX_ENTRIES).forEach { data.remove(it.prefix to it.next) }
    }

    private fun ranking(now: Long) = compareByDescending<Entry> { weight(it, now) }
        .thenByDescending { it.updated }.thenBy { it.next }.thenBy { it.prefix }

    private fun weight(entry: Entry, now: Long): Double =
        entry.score * 0.5.pow((now.toDouble() - entry.updated).coerceAtLeast(0.0) / HALF_LIFE_MS)

    companion object {
        const val MAX_CONTEXT = 8
        const val MAX_PER_PREFIX = 16
        const val MAX_ENTRIES = 2000
        const val HALF_LIFE_MS = 30.0 * 24 * 60 * 60 * 1000
        private const val MIN_SCORE = 0.1

        fun characters(text: String): List<String> = text.codePoints().toArray()
            .map { String(Character.toChars(it)) }

        fun isChinese(text: String): Boolean = text.isNotEmpty() && text.codePoints().allMatch {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        }

        fun tail(text: String): String = characters(text).takeLast(MAX_CONTEXT).joinToString("")
    }
}
