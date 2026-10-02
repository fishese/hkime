package com.awcjack.dualquickime.data

/** Evidence is retained until ranking; text remains a normal view candidate. */
internal data class TypoCandidate(
    val text: String,
    val correctedCode: String,
    val method: MethodMembership.Method,
    val cost: Int,
    val completion: Boolean = false,
    val sourceOrder: Int = 0,
    val commonWord: Boolean = false,
) {
    val isEnglish: Boolean get() = method == MethodMembership.Method.ENGLISH
    val isCharacter: Boolean get() = !isEnglish && text.codePointCount(0, text.length) == 1
}

internal fun MethodMembership.Recovery.typedCandidates(): List<TypoCandidate> =
    candidates.mapIndexed { index, text -> TypoCandidate(text, code, method, cost, sourceOrder = index) }
