package com.awcjack.dualquickime.data

import java.io.InputStream

/** Method-specific English -> Traditional Chinese candidates. */
class EnglishDictionary private constructor(
    private val entries: Map<String, List<String>>
) {
    fun lookup(word: String): List<String> = entries[word.lowercase()].orEmpty()

    val size: Int get() = entries.size

    companion object {
        val EMPTY = EnglishDictionary(emptyMap())

        fun parse(input: InputStream): EnglishDictionary {
            val entries = linkedMapOf<String, MutableList<String>>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val separator = line.indexOf('\t')
                    require(separator > 0 && separator < line.lastIndex) { "Invalid English dictionary row" }
                    val word = line.substring(0, separator).trim().lowercase()
                    val candidate = line.substring(separator + 1).trim()
                    require(word.all { it in 'a'..'z' } && candidate.isNotEmpty()) {
                        "Invalid English dictionary entry"
                    }
                    entries.getOrPut(word) { mutableListOf() }.add(candidate)
                }
            }
            return EnglishDictionary(entries.mapValues { (_, candidates) -> candidates.distinct() })
        }
    }
}
