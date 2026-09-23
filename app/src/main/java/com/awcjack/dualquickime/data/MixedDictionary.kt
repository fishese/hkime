package com.awcjack.dualquickime.data

import android.content.res.AssetManager
import java.io.ObjectInputStream
import java.util.LinkedHashMap
import java.util.zip.ZipInputStream

/**
 * Reader for the Apache-2.0 Mixed Chinese Keyboard Plus v2.1 dictionary format.
 *
 * A lookup key can be an HKG Cantonese spelling, Cangjie/Quick code, or an
 * English word. The source dictionary intentionally merges those namespaces,
 * which is what allows seamless Chinese/English input without a mode switch.
 */
class MixedDictionary(
    private val assets: AssetManager,
    private val script: Script = Script.TRADITIONAL
) {
    enum class Script { TRADITIONAL, SIMPLIFIED }

    private val shardCache = object : LinkedHashMap<String, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHED_SHARDS
    }

    @Synchronized
    fun lookup(rawCode: String): List<String> {
        val code = rawCode.lowercase()
        if (code.isEmpty() || code.any { it !in 'a'..'z' }) return emptyList()

        val shardName = shardName(code)
        val shard = shardCache[shardName] ?: loadShard(shardName)?.also {
            shardCache[shardName] = it
        } ?: return emptyList()

        val dictionaryKey = code.drop(1)
        val value = findValue(shard, dictionaryKey) ?: return emptyList()
        return decodeMckCandidates(value, script == Script.SIMPLIFIED)
    }

    private fun shardName(code: String): String {
        val first = code[0]
        val highSecondChar = code.length > 1 && code[1] > 'k'
        return "mix_map_ext_${first}${if (highSecondChar) "1" else ""}.cs2"
    }

    private fun loadShard(name: String): String? = runCatching {
        assets.open("mck/$name").use { input ->
            ZipInputStream(input).use { zip ->
                requireNotNull(zip.nextEntry) { "Missing zip entry in $name" }
                ObjectInputStream(zip).use { objects -> objects.readObject() as String }
            }
        }
    }.getOrNull()

    private fun findValue(shard: String, key: String): String? {
        val needle = "$key\t"
        var lineStart = if (shard.startsWith(needle)) 0 else shard.indexOf("\n$needle")
        if (lineStart < 0) return null
        if (lineStart > 0) lineStart++
        val valueStart = lineStart + needle.length
        val lineEnd = shard.indexOf('\n', valueStart).let { if (it < 0) shard.length else it }
        return shard.substring(valueStart, lineEnd)
    }

    private companion object {
        const val MAX_CACHED_SHARDS = 8
    }
}

/** Decode one dictionary value without Android dependencies, so it is directly testable. */
internal fun decodeMckCandidates(value: String, simplified: Boolean): List<String> {
    val backspace = value.indexOf('\b')
    val section = if (simplified) {
        if (backspace < 0) "" else value.substring(backspace + 1)
    } else {
        value.substring(0, if (backspace < 0) value.length else backspace)
    }

    val candidates = LinkedHashSet<String>()
    var index = 0
    while (index < section.length) {
        val char = section[index]
        when {
            char == '\u0000' || char == '\u000c' || char == '\r' || char == '•' -> index++
            char in '1'..'9' -> {
                var digitsEnd = index + 1
                if (digitsEnd < section.length && section[digitsEnd].isDigit()) digitsEnd++
                val candidateLength = section.substring(index, digitsEnd).toIntOrNull() ?: 0
                val candidateEnd = (digitsEnd + candidateLength).coerceAtMost(section.length)
                if (candidateEnd > digitsEnd) candidates += section.substring(digitsEnd, candidateEnd)
                index = candidateEnd.coerceAtLeast(index + 1)
            }
            else -> {
                val codePoint = Character.codePointAt(section, index)
                candidates += String(Character.toChars(codePoint))
                index += Character.charCount(codePoint)
            }
        }
    }
    return candidates.toList()
}
