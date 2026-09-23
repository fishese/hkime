package com.awcjack.dualquickime.data

import java.io.InputStream

/** HK IME-owned context -> suffix suggestions. The committed context is never repeated. */
class CuratedAssociatedPhrases private constructor(private val entries: Map<String, List<String>>) {
    fun lookup(context: String): List<Pair<String, String>> = entries.entries
        .asSequence()
        .filter { context.endsWith(it.key) }
        .sortedByDescending { it.key.length }
        .flatMap { entry -> entry.value.asSequence().map { entry.key to it } }
        .toList()

    companion object {
        val EMPTY = CuratedAssociatedPhrases(emptyMap())

        fun parse(input: InputStream): CuratedAssociatedPhrases {
            val entries = linkedMapOf<String, MutableList<String>>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith('#')) return@forEach
                    val separator = line.indexOf('\t')
                    require(separator > 0 && separator < line.lastIndex) { "Invalid curated phrase row" }
                    val context = line.substring(0, separator)
                    val suffix = line.substring(separator + 1)
                    require(context.isNotBlank() && suffix.isNotEmpty()) { "Empty curated phrase field" }
                    entries.getOrPut(context) { mutableListOf() }.add(suffix)
                }
            }
            return CuratedAssociatedPhrases(entries)
        }
    }
}

/** Keeps the pre-existing MCK -> OpenVanilla fallback, then adds curated matches. */
object AssociatedPhraseSuggestions {
    fun merge(
        context: String,
        character: String,
        curated: CuratedAssociatedPhrases,
        mckLookup: (String) -> List<String>,
        openVanillaLookup: (String) -> List<String>
    ): List<String> {
        var existing = emptyList<String>()
        for (length in minOf(8, context.length) downTo 1) {
            existing = mckLookup(context.takeLast(length))
            if (existing.isNotEmpty()) break
        }
        if (existing.isEmpty()) existing = mckLookup(character)
        if (existing.isEmpty()) existing = openVanillaLookup(character)

        val curatedMatches = curated.lookup(context)
        val specific = curatedMatches.filter { it.first.length > 1 }.map { it.second }
        val broad = curatedMatches.filter { it.first.length == 1 }.map { it.second }
        return (specific + existing + broad).distinct()
    }
}
