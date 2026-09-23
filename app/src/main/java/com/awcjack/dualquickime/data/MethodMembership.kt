package com.awcjack.dualquickime.data

/** Method provenance hints for the merged MCK candidate list. */
class MethodMembership(lines: Sequence<String>) {
    enum class Method { CANTONESE, CANGJIE, QUICK }

    private val byCode: Map<String, Map<Method, Set<String>>> = buildMap {
        val mutable = mutableMapOf<String, MutableMap<Method, MutableSet<String>>>()
        for (line in lines) {
            if (line.startsWith('#')) continue
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) continue
            val method = when (parts[1]) {
                "cantonese" -> Method.CANTONESE
                "cangjie" -> Method.CANGJIE
                "quick" -> Method.QUICK
                else -> continue
            }
            val characters = mutable.getOrPut(parts[0]) { mutableMapOf() }
                .getOrPut(method) { mutableSetOf() }
            var index = 0
            while (index < parts[2].length) {
                val point = Character.codePointAt(parts[2], index)
                characters.add(String(Character.toChars(point)))
                index += Character.charCount(point)
            }
        }
        putAll(mutable)
    }

    /** Keep MCK order. Unclassified entries are retained only when no relevant
     * method is disabled, or when the code belongs exclusively to one enabled
     * method. This avoids assigning ambiguous MCK phrases to the wrong method. */
    fun filter(code: String, candidates: List<String>, enabled: Set<Method>): List<String> {
        if (enabled.containsAll(Method.values().toSet())) return candidates
        val methods = byCode[code.lowercase()] ?: return emptyList()
        return candidates.filter { candidate ->
            val identified = methods.filterValues { candidate in it }.keys
            when {
                identified.isNotEmpty() -> identified.any { it in enabled }
                methods.size == 1 -> methods.keys.first() in enabled
                else -> methods.keys.all { it in enabled }
            }
        }
    }
}
