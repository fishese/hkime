package com.awcjack.dualquickime.data

import java.util.Locale

private fun candidateKey(text: String): String =
    if (EnglishSuggestions.isLatinWord(text)) text.lowercase(Locale.ROOT) else text

/** Exact characters/full English words beat recovery; phrase shorthand does not. */
internal fun mergeTypoCandidates(
    typed: String,
    exact: List<String>,
    recovered: List<TypoCandidate>,
    rankInferred: (List<String>) -> List<String> = { it },
): List<String> {
    if (recovered.isEmpty()) return exact
    val (protected, remaining) = exact.partition {
        it.codePointCount(0, it.length) == 1 || it.equals(typed, ignoreCase = true)
    }
    val exactKeys = exact.map(::candidateKey).toSet()
    val initial = recovered.sortedWith(compareBy<TypoCandidate> { it.completion }.thenBy { it.cost }
        .thenBy { !it.commonWord }.thenBy { if (it.isEnglish) it.sourceOrder else 0 }
        .thenBy { it.method.ordinal }.thenBy { it.correctedCode }
        .thenBy { it.sourceOrder }).filterNot { candidateKey(it.text) in exactKeys }
        .distinctBy { candidateKey(it.text) }
    val ranks = rankInferred(initial.map { it.text }).withIndex()
        .associate { candidateKey(it.value) to it.index }
    // Learning breaks ties inside confidence bands, never across exact/cost/completion boundaries.
    val ranked = initial.sortedWith(compareBy<TypoCandidate> { it.completion }.thenBy { it.cost }
        .thenBy { ranks[candidateKey(it.text)] ?: Int.MAX_VALUE })
    val weakGroups = ranked.filter { !it.isEnglish && it.cost >= 3 }
        .groupBy { it.method }.values.flatMap { methodCandidates ->
            methodCandidates.map { it.method to it.correctedCode }.distinct().take(8)
        }.toSet()
    val retained = ranked.filter { it.isEnglish || it.cost < 3 ||
        (it.method to it.correctedCode) in weakGroups }

    fun chineseQueue(items: List<TypoCandidate>): List<TypoCandidate> {
        val groupsByMethod = items.groupBy { it.method }.toSortedMap(compareBy { it.ordinal })
            .values.map { method -> method.groupBy { it.correctedCode }.values.toList() }
        val groups = mutableListOf<List<TypoCandidate>>()
        for (index in 0 until (groupsByMethod.maxOfOrNull { it.size } ?: 0)) {
            for (method in groupsByMethod) method.getOrNull(index)?.let { groups += it }
        }
        return buildList {
            for (depth in 0 until (groups.maxOfOrNull { it.size } ?: 0)) {
                for (group in groups) group.getOrNull(depth)?.let { add(it) }
            }
        }
    }
    fun interleave(english: List<TypoCandidate>, chinese: List<TypoCandidate>): List<TypoCandidate> = buildList {
        for (index in 0 until maxOf(english.size, chinese.size)) {
            english.getOrNull(index)?.let { add(it) }
            chinese.getOrNull(index)?.let { add(it) }
        }
    }

    val leading = mutableListOf<TypoCandidate>()
    val tail = mutableListOf<TypoCandidate>()
    for ((_, band) in retained.filter { !it.completion && (it.isEnglish || it.isCharacter) }
            .groupBy { it.cost }.toSortedMap()) {
        val english = band.filter { it.isEnglish }
        val chinese = chineseQueue(band.filter { it.isCharacter })
        val slots = 4 - leading.size
        val englishCount = when {
            slots == 0 -> 0
            chinese.isEmpty() -> minOf(slots, english.size)
            english.isEmpty() -> 0
            slots == 1 -> if (band.first().isEnglish) 1 else 0
            else -> minOf(english.size, slots - 1, 3)
        }
        val selectedChinese = chinese.take((slots - englishCount).coerceAtLeast(0))
        val selectedEnglish = english.take(englishCount +
            minOf((slots - englishCount - selectedChinese.size).coerceAtLeast(0), english.size - englishCount))
        val selected = interleave(selectedEnglish, selectedChinese)
        leading += selected
        val selectedKeys = selected.map { candidateKey(it.text) }.toSet()
        tail += interleave(english, chinese).filterNot { candidateKey(it.text) in selectedKeys }
    }
    val fuzzy = retained.filter { it.completion }.map { it.text }
    val phrases = retained.filter { !it.isEnglish && !it.isCharacter }.map { it.text }
    return (protected + leading.map { it.text } + remaining + tail.map { it.text } + fuzzy + phrases)
        .distinctBy(::candidateKey)
}

/** Put a reviewed single character before phrase candidates without disturbing other ranking. */
internal fun promoteReviewedCharacter(code: String, candidates: List<String>): List<String> {
    if (code != "jjyt" && code != "jt") return candidates
    val character = "𨋢"
    val current = candidates.indexOf(character)
    if (current < 0) return candidates
    val firstPhrase = candidates.indexOfFirst { it.codePointCount(0, it.length) > 1 }
    if (firstPhrase < 0 || current < firstPhrase) return candidates
    return candidates.toMutableList().apply {
        removeAt(current)
        add(firstPhrase, character)
    }
}
