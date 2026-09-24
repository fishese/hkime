package com.awcjack.dualquickime.data

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
