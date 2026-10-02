package com.awcjack.dualquickime.data

/** Shared tap-key geometry fallback. No touch coordinates or typing data are stored. */
internal object KeyProximity {
    private val neighbours = mapOf(
        'a' to "qsw", 'b' to "hjnv", 'c' to "fgvx", 'd' to "efrsxz", 'e' to "drsw",
        'f' to "cdgrtx", 'g' to "cfhtvy", 'h' to "bgjuvy", 'i' to "jkou", 'j' to "bhiknu",
        'k' to "ijlmno", 'l' to "kmop", 'm' to "kln", 'n' to "bjkm", 'o' to "iklp",
        'p' to "lo", 'q' to "aw", 'r' to "deft", 's' to "adewz", 't' to "fgry",
        'u' to "hijy", 'v' to "bcgh", 'w' to "aeqs", 'x' to "cdfz", 'y' to "ghtu", 'z' to "dsx",
    )

    fun adjacent(a: Char, b: Char): Boolean = b in neighbours[a].orEmpty()

    /** One edit only: substitutions/transpositions cost 2, missing/extra keys cost 3. */
    fun codeVariants(input: String, allowLengthEdits: Boolean): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        fun add(code: String, cost: Int) {
            if (code != input) result[code] = minOf(result[code] ?: Int.MAX_VALUE, cost)
        }
        for (i in input.indices) {
            for (letter in neighbours[input[i]].orEmpty()) {
                add(input.substring(0, i) + letter + input.substring(i + 1), 2)
            }
            if (i + 1 < input.length && input[i] != input[i + 1]) {
                add(input.substring(0, i) + input[i + 1] + input[i] + input.substring(i + 2), 2)
            }
            if (allowLengthEdits) add(input.removeRange(i, i + 1), 3)
        }
        if (allowLengthEdits) {
            for (i in 0..input.length) for (letter in 'a'..'z') {
                add(input.substring(0, i) + letter + input.substring(i), 3)
            }
        }
        return result
    }
}
