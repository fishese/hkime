package com.awcjack.dualquickime.data

import java.io.InputStream

/** Small exact-match overlay for English words that are not translated by the bundled MCK dictionary. */
class EnglishTranslationOverlay private constructor(
    private val translations: Map<String, String>
) {
    fun lookup(typed: String): String? = translations[typed.lowercase()]

    companion object {
        val EMPTY = EnglishTranslationOverlay(emptyMap())

        fun parse(input: InputStream): EnglishTranslationOverlay {
            val translations = linkedMapOf<String, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().lowercase()
                        val value = parts[1].trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) translations[key] = value
                    }
                }
            }
            return EnglishTranslationOverlay(translations)
        }
    }
}
