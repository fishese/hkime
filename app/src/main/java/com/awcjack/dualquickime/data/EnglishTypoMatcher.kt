package com.awcjack.dualquickime.data

import java.util.Locale

/** Bounded, offline weighted-edit trie. Two nearby slips are cheaper than two arbitrary edits. */
internal class EnglishTypoMatcher(words: Collection<String>, private val common: Set<String>) {
    companion object {
        const val MAX_WORD_LENGTH = 32
        const val MAX_TYPED_LENGTH = MAX_WORD_LENGTH + 1
        const val MAX_VISITED_NODES = 12_000
        const val MAX_COMPLETE_RESULTS = 8
        const val MAX_FUZZY_RESULTS = 4
    }

    private class Node {
        val children = sortedMapOf<Char, Node>()
        var word: String? = null
    }
    internal data class Match(val word: String, val cost: Int, val completion: Boolean)
    private val root = Node()
    private val vocabulary = (words + common).filter {
        it.length in 2..MAX_WORD_LENGTH && it.all { char -> char in 'a'..'z' }
    }.toSet()

    init {
        for (word in vocabulary) {
            var node = root
            for (letter in word) node = node.children.getOrPut(letter) { Node() }
            node.word = word
        }
    }

    fun suggestions(typed: String): List<String> = candidates(typed).map { it.text }

    internal fun candidates(typed: String): List<TypoCandidate> = matches(typed).mapIndexed { index, match ->
        val display = when {
            typed.all(Char::isUpperCase) -> match.word.uppercase(Locale.ROOT)
            typed.first().isUpperCase() -> match.word.replaceFirstChar { it.uppercaseChar() }
            else -> match.word
        }
        TypoCandidate(display, match.word, MethodMembership.Method.ENGLISH, match.cost,
            match.completion, index, match.word in common)
    }

    internal fun matches(typed: String): List<Match> {
        if (typed.length !in 3..MAX_TYPED_LENGTH || !EnglishSuggestions.isLatinWord(typed)) return emptyList()
        // Deliberately mixed case often means identifiers, not misspelled ordinary words.
        if (!(typed.all(Char::isLowerCase) || typed.all(Char::isUpperCase) ||
                (typed.first().isUpperCase() && typed.drop(1).all(Char::isLowerCase)))) return emptyList()
        val input = typed.lowercase(Locale.ROOT)
        if (input in vocabulary) return emptyList()
        val budget = if (input.length < 5) 2 else 4
        val results = mutableListOf<Match>()
        var visited = 0
        val initial = IntArray(input.length + 1) { it * 3 }

        fun visit(node: Node, letter: Char, depth: Int, previous: IntArray,
                  beforePrevious: IntArray?, previousLetter: Char?, prefixCost: Int, prefixDepth: Int) {
            // Deterministic caps keep worst-case searches from stalling the tap pipeline.
            if (++visited > MAX_VISITED_NODES || depth > minOf(MAX_WORD_LENGTH, input.length + 6)) return
            val row = IntArray(input.length + 1)
            row[0] = depth * 3
            for (i in 1..input.length) {
                val substitution = when {
                    input[i - 1] == letter -> 0
                    KeyProximity.adjacent(input[i - 1], letter) -> 2
                    else -> 3
                }
                row[i] = minOf(previous[i] + 3, row[i - 1] + 3, previous[i - 1] + substitution)
                if (beforePrevious != null && i > 1 && letter == input[i - 2] &&
                    previousLetter == input[i - 1]) {
                    row[i] = minOf(row[i], beforePrevious[i - 2] + 2)
                }
            }
            // Carry a ONE-edit prefix match into a short untyped suffix. Full corrections
            // retain their own cost; unfinished suffixes are not additional typing errors.
            var bestPrefixCost = prefixCost
            var bestPrefixDepth = prefixDepth
            val cost = row[input.length]
            if (input.length >= 4 && depth >= input.length - 1 && cost in 1..3 &&
                (cost < bestPrefixCost || (cost == bestPrefixCost && depth > bestPrefixDepth))) {
                bestPrefixCost = cost
                bestPrefixDepth = depth
            }
            node.word?.let { word ->
                if (cost in 1..budget && (input.length >= 4 || word in common)) {
                    results.add(Match(word, cost, false))
                } else if ((input.length >= 5 || word in common) && bestPrefixCost <= 3 &&
                    depth > bestPrefixDepth && depth - bestPrefixDepth <= 6 && !word.startsWith(input)) {
                    results.add(Match(word, bestPrefixCost, true))
                }
            }
            // A transposition can skip over an expensive intermediate row (teh -> the).
            if (row.minOrNull()!! > budget && previous.minOrNull()!! > budget && bestPrefixCost > 3) return
            for ((nextLetter, child) in node.children) {
                visit(child, nextLetter, depth + 1, row, previous, letter, bestPrefixCost, bestPrefixDepth)
            }
        }
        for ((letter, child) in root.children) {
            visit(child, letter, 1, initial, null, null, Int.MAX_VALUE, 0)
        }
        val ranked = results.sortedWith(compareBy<Match> { it.completion }
            .thenBy { it.cost }.thenBy { it.word !in common }
            .thenBy { it.word.length }.thenBy { it.word }).distinctBy { it.word }
        val completeLimit = if (input.length == 3) 2 else MAX_COMPLETE_RESULTS
        val fuzzyLimit = if (input.length == 4) 2 else MAX_FUZZY_RESULTS
        return ranked.filterNot { it.completion }.take(completeLimit) +
            ranked.filter { it.completion }.take(fuzzyLimit)
    }
}
