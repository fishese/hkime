package com.awcjack.dualquickime.data

import java.io.InputStream

/** Offline English completions, independent of the conservative typo-correction lexicon. */
class EnglishAutocomplete private constructor(private val words: List<String>) {
    val size: Int get() = words.size
    internal fun allWords(): List<String> = words

    fun contains(word: String): Boolean = words.binarySearch(word.lowercase()) >= 0

    fun completions(typed: String): List<String> {
        if (typed.length !in 2..64 || typed.any { it !in 'a'..'z' && it !in 'A'..'Z' }) {
            return emptyList()
        }
        val prefix = typed.lowercase()
        val index = words.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        val matches = ArrayList<String>()
        for (i in index until words.size) {
            val word = words[i]
            if (!word.startsWith(prefix)) break
            matches.add(word)
        }
        return matches.sortedWith(compareBy<String> { it.length }.thenBy { it })
            .take(8)
            .map { word -> when {
                typed.all { it.isUpperCase() } -> word.uppercase()
                typed.first().isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                    word.replaceFirstChar { it.uppercase() }
                else -> word
            } }
    }

    companion object {
        val EMPTY = EnglishAutocomplete(emptyList())

        fun parse(input: InputStream): EnglishAutocomplete {
            val words = input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(String::trim)
                    .filter { word -> word.length >= 2 && word.all { it in 'a'..'z' } }
                    .toSortedSet()
                    .toList()
            }
            return EnglishAutocomplete(words)
        }
    }
}
