package com.awcjack.dualquickime.data

/** Exact characters/full English words beat recovery; phrase shorthand does not. */
internal fun mergeTypoCandidates(
    typed: String,
    exact: List<String>,
    englishFixes: List<String>,
    chineseFixes: List<String>,
    rankInferred: (List<String>) -> List<String> = { it },
): List<String> {
    if (englishFixes.isEmpty() && chineseFixes.isEmpty()) return exact
    val (protected, remaining) = exact.partition {
        it.codePointCount(0, it.length) == 1 || it.equals(typed, ignoreCase = true)
    }
    val (characters, phrases) = chineseFixes.partition { it.codePointCount(0, it.length) == 1 }
    // Existing exact spellings retain their display case and strongest provenance.
    val exactWords = exact.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
    val inferred = rankInferred((englishFixes + characters).distinct().filterNot {
        it.lowercase(java.util.Locale.ROOT) in exactWords
    })
    return (protected + inferred + remaining + rankInferred(phrases)).distinct()
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
