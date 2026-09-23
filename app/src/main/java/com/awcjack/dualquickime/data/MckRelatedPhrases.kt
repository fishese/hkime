package com.awcjack.dualquickime.data

import android.content.res.AssetManager
import java.io.ObjectInputStream
import java.util.LinkedHashMap
import java.util.zip.ZipInputStream

/** Related-word suffixes from the Apache-2.0 MCK Plus v2.1 phrase shards. */
class MckRelatedPhrases(private val assets: AssetManager) {
    private val boundaries = intArrayOf(21413, 23460, 25238, 26999, 29281, 31757, 33769, 36076, 38287)
    private val cache = object : LinkedHashMap<Int, String>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > 3
    }

    @Synchronized
    fun lookup(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val shardNumber = boundaries.indexOfFirst { prefix[0].code <= it }.let { if (it < 0) 9 else it }
        val shard = cache[shardNumber] ?: load(shardNumber)?.also { cache[shardNumber] = it }
            ?: return emptyList()
        val needle = "$prefix\t"
        val lineStart = if (shard.startsWith(needle)) 0 else shard.indexOf("\n$needle").let {
            if (it < 0) return emptyList() else it + 1
        }
        val valueStart = lineStart + needle.length
        val lineEnd = shard.indexOf('\n', valueStart).let { if (it < 0) shard.length else it }
        return decodeMckCandidates(shard.substring(valueStart, lineEnd), simplified = false)
    }

    private fun load(number: Int): String? = runCatching {
        assets.open("mck/phrases/phrase_$number.cs2").use { input ->
            ZipInputStream(input).use { zip ->
                requireNotNull(zip.nextEntry)
                ObjectInputStream(zip).use { it.readObject() as String }
            }
        }
    }.getOrNull()
}
