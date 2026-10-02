package com.awcjack.dualquickime.data

import android.content.res.AssetManager

/** Related-word suffixes from the Apache-2.0 MCK Plus v2.1 phrase shards. */
class MckRelatedPhrases(private val assets: AssetManager) {
    private val boundaries = intArrayOf(21413, 23460, 25238, 26999, 29281, 31757, 33769, 36076, 38287)
    private val cache = ShardCache(assets, 3, 4 * 1024 * 1024)

    @Synchronized
    fun lookup(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val shardNumber = boundaries.indexOfFirst { prefix[0].code <= it }.let { if (it < 0) 9 else it }
        val value = cache.get("mck/phrases/phrase_$shardNumber.cs2")?.value(prefix) ?: return emptyList()
        return decodeMckCandidates(value, simplified = false)
    }

}
