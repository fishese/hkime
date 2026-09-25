package com.awcjack.dualquickime.data

/** Method provenance hints for the merged MCK candidate list. */
class MethodMembership(lines: Sequence<String>, phraseOverrides: Sequence<String> = emptySequence()) {
    enum class Method { CANTONESE, CANGJIE, QUICK, ENGLISH }

    private val cangjieCodesByCharacter = mutableMapOf<String, MutableSet<String>>()
    private val cantoneseCodesByCharacter = mutableMapOf<String, MutableSet<String>>()
    private val phraseMethods = mutableMapOf<Pair<String, String>, MutableSet<Method>>()
    private val overrideCandidatesByCode = linkedMapOf<String, LinkedHashSet<String>>()
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
                val character = String(Character.toChars(point))
                characters.add(character)
                if (method == Method.CANGJIE) {
                    cangjieCodesByCharacter.getOrPut(character) { mutableSetOf() }.add(parts[0])
                } else if (method == Method.CANTONESE) {
                    cantoneseCodesByCharacter.getOrPut(character) { mutableSetOf() }.add(parts[0])
                }
                index += Character.charCount(point)
            }
        }
        putAll(mutable)
    }

    init {
        for (line in phraseOverrides) {
            if (line.startsWith('#')) continue
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) continue
            val method = when (parts[1]) {
                "cantonese" -> Method.CANTONESE
                "cangjie" -> Method.CANGJIE
                "quick" -> Method.QUICK
                "english" -> Method.ENGLISH
                else -> continue
            }
            val code = parts[0].lowercase()
            phraseMethods.getOrPut(code to parts[2]) { mutableSetOf() }.add(method)
            overrideCandidatesByCode.getOrPut(code) { linkedSetOf() }.add(parts[2])
        }
    }

    /** Only enabled, known input codes participate in swipe decoding. */
    fun swipeCodes(enabled: Set<Method>): Set<String> = buildSet {
        for ((code, methods) in byCode) {
            if (code.length in 2..12 && code.all { it in 'a'..'z' } &&
                methods.keys.any { it in enabled }) add(code)
        }
        for ((key, methods) in phraseMethods) {
            val code = key.first
            if (code.length in 2..12 && code.all { it in 'a'..'z' } &&
                methods.any { it in enabled }) add(code)
        }
    }

    /** Add reviewed/user-supplied entries missing from a bundled dictionary shard. */
    fun supplementalCandidates(code: String, enabled: Set<Method>): List<String> =
        overrideCandidatesByCode[code.lowercase()].orEmpty().filter { candidate ->
            phraseMethods[code.lowercase() to candidate].orEmpty().any { it in enabled }
        }

    /** Keep MCK order and retain any unassigned candidates by default. */
    fun filter(code: String, candidates: List<String>, enabled: Set<Method>,
               includeUncertain: Boolean = true): List<String> {
        if (enabled.containsAll(Method.values().toSet()) && includeUncertain) return candidates.distinct()
        val methods = byCode[code.lowercase()].orEmpty()
        return candidates.filter { candidate ->
            val identified = methods.filterValues { candidate in it }.keys.toMutableSet()
            identified.addAll(phraseMethods[code.lowercase() to candidate].orEmpty())
            // Multi-character Cangjie shorthand uses the same first-key logic
            // in Quick/Simple Input, so either method should expose it.
            if (matchesPhraseCode(code, candidate, cangjieCodesByCharacter, true)) {
                identified.add(Method.CANGJIE)
                identified.add(Method.QUICK)
            }
            if (matchesPhraseCode(code, candidate, cantoneseCodesByCharacter, false, true))
                identified.add(Method.CANTONESE)
            when {
                identified.isNotEmpty() -> identified.any { it in enabled }
                else -> includeUncertain
            }
        }.distinct()
    }

    private fun matchesPhraseCode(
        rawCode: String,
        candidate: String,
        codesByCharacter: Map<String, Set<String>>,
        quickFinalCode: Boolean,
        cantoneseZAlias: Boolean = false
    ): Boolean {
        val code = rawCode.lowercase()
        val characters = mutableListOf<String>()
        var index = 0
        while (index < candidate.length) {
            val point = Character.codePointAt(candidate, index)
            characters.add(String(Character.toChars(point)))
            index += Character.charCount(point)
        }
        if (characters.size < 2 || code.length < characters.size ||
            characters.all(::isAsciiLatin)) return false
        for (position in 0 until characters.lastIndex) {
            val codes = characterCodes(characters[position], codesByCharacter) ?: return false
            if (codes.none { codePartMatches(code[position].toString(), it.take(1), cantoneseZAlias) }) return false
        }
        val suffix = code.substring(characters.lastIndex)
        return characterCodes(characters.last(), codesByCharacter)?.any {
            codePartMatches(suffix, it, cantoneseZAlias) ||
                codePartMatches(suffix, it.take(1), cantoneseZAlias) ||
                (quickFinalCode && suffix == "${it.first()}${it.last()}")
        } == true
    }

    private fun codePartMatches(typed: String, reference: String, cantoneseZAlias: Boolean): Boolean =
        typed == reference || (cantoneseZAlias && typed.startsWith('z') &&
            reference.startsWith('j') && typed.drop(1) == reference.drop(1))

    private fun isAsciiLatin(character: String): Boolean =
        character.length == 1 && (character[0] in 'A'..'Z' || character[0] in 'a'..'z')

    private fun characterCodes(character: String, codesByCharacter: Map<String, Set<String>>): Set<String>? =
        if (isAsciiLatin(character)) {
            setOf(character.lowercase())
        } else {
            codesByCharacter[character]
        }
}
