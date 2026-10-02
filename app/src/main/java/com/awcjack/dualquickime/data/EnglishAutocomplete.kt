package com.awcjack.dualquickime.data

import java.io.InputStream
import java.util.PriorityQueue

/** Offline English completions, independent of the conservative typo-correction lexicon. */
class EnglishAutocomplete private constructor(private val words: List<String>) {
    val size: Int get() = words.size
    internal fun allWords(): List<String> = words

    fun contains(word: String): Boolean = words.binarySearch(word.lowercase()) >= 0

    /** True when [prefix] begins at least [threshold] dictionary words (the list is sorted). */
    fun hasAtLeastCompletions(prefix: String, threshold: Int): Boolean {
        if (threshold <= 0) return true
        val lower = prefix.lowercase()
        val index = words.binarySearch(lower).let { if (it < 0) -it - 1 else it }
        val last = index + threshold - 1
        return last < words.size && words[last].startsWith(lower)
    }

    fun completions(typed: String): List<String> {
        if (typed.length !in 2..64 || typed.any { it !in 'a'..'z' && it !in 'A'..'Z' }) {
            return emptyList()
        }
        val prefix = typed.lowercase()
        val index = words.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        val ranking = compareBy<String> { COMMON_WORD_ORDER[it] ?: Int.MAX_VALUE }
            .thenBy { it.length }.thenBy { it }
        val matches = PriorityQueue(8, ranking.reversed())
        for (i in index until words.size) {
            val word = words[i]
            if (!word.startsWith(prefix)) break
            if (isRedundantSimplePlural(word, prefix)) continue
            if (matches.size < 8) matches.add(word)
            else if (ranking.compare(word, matches.peek()) < 0) { matches.poll(); matches.add(word) }
        }
        return matches.sortedWith(ranking)
            .map { word -> when {
                typed.all { it.isUpperCase() } -> word.uppercase()
                typed.first().isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                    word.replaceFirstChar { it.uppercase() }
                else -> word
            } }
    }

    private fun isRedundantSimplePlural(word: String, prefix: String): Boolean {
        if (word in NON_PLURAL_S_ES_FORMS) return false

        val singulars = ArrayList<String>(2)
        if (word.endsWith('s')) {
            val singular = word.dropLast(1)
            if (singular.length >= 2 && words.binarySearch(singular) >= 0) {
                singulars.add(singular)
            }
        }
        if (word.endsWith("es")) {
            val singular = word.dropLast(2)
            if ((singular.endsWith("s") || singular.endsWith("x") || singular.endsWith("z") ||
                    singular.endsWith("ch") || singular.endsWith("sh") || singular.endsWith("o")) &&
                words.binarySearch(singular) >= 0
            ) {
                singulars.add(singular)
            }
        }

        return singulars.any { singular ->
            prefix.length <= singular.length && singular.startsWith(prefix)
        }
    }

    companion object {
        val EMPTY = EnglishAutocomplete(emptyList())

        // These are ordinary words, not simple plurals (e.g. "does" is a verb form).
        private val NON_PLURAL_S_ES_FORMS = setOf("does", "goes", "news", "series", "species", "physics", "uses")

        // Common everyday and HK office words should appear before rare words
        // that happen to share the same prefix. The rest retain length order.
        private val COMMON_WORD_ORDER = listOf(
            "the", "and", "you", "that", "for", "with", "this", "are", "was", "have",
            "not", "but", "what", "when", "your", "there", "their", "they", "then", "one",
            "will", "would", "can", "all", "about", "like", "just", "know", "time", "people",
            "into", "make", "look", "use", "get", "good", "some", "could", "them", "see",
            "other", "than", "now", "also", "back", "after", "our", "work", "first", "well",
            "way", "even", "new", "want", "because", "any", "these", "give", "day", "most",
            "app", "apple", "apply", "application", "account", "address", "appointment", "booking",
            "business", "check", "company", "contact", "customer", "email", "invoice", "message",
            "order", "payment", "please", "product", "project", "report", "request", "service",
            "support", "thank", "tomorrow", "update", "availability", "forms", "inventory",
            "organise", "occurred", "user", "users", "vendor", "voicemail"
        ).withIndex().associate { (index, word) -> word to index }

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
