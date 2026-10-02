package com.awcjack.dualquickime.data

import android.content.res.AssetManager
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** Retains one source string plus sorted line offsets, without a map of decoded values. */
internal class IndexedShard(private val text: String, precomputed: IntArray? = null) {
    private val starts: IntArray
    val byteSize: Int get() = text.length * 2 + starts.size * 4

    init {
        if (precomputed != null) {
            require(precomputed.all { start -> start in text.indices && (start == 0 || text[start - 1] == '\n') &&
                text.indexOf('\t', start).let { tab -> tab in start until text.indexOf('\n', start).let { if (it < 0) text.length else it } } })
            require((1 until precomputed.size).all { compareLines(precomputed[it - 1], precomputed[it]) <= 0 })
            starts = precomputed.copyOf()
        } else {
        val lines = ArrayList<Int>()
        var start = 0
        while (start < text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val tab = text.indexOf('\t', start)
            if (tab in start until end) lines.add(start)
            start = end + 1
        }
        starts = lines.sortedWith { a, b -> compareLines(a, b) }.toIntArray()
        }
    }

    private fun compareLines(a: Int, b: Int): Int {
        var x = a; var y = b
        while (text[x] != '\t' && text[y] != '\t') {
            val comparison = text[x++].compareTo(text[y++])
            if (comparison != 0) return comparison
        }
        return when { text[x] == '\t' && text[y] == '\t' -> a.compareTo(b)
            text[x] == '\t' -> -1; else -> 1 }
    }

    fun value(key: String): String? {
        var low = 0; var high = starts.size
        // Lower bound preserves the original first-line behavior for duplicate keys.
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareKey(starts[middle], key) < 0) low = middle + 1 else high = middle
        }
        if (low == starts.size || compareKey(starts[low], key) != 0) return null
        val valueStart = starts[low] + key.length + 1
        val end = text.indexOf('\n', valueStart).let { if (it < 0) text.length else it }
        return text.substring(valueStart, end)
    }

    private fun compareKey(start: Int, key: String): Int {
        var index = 0
        while (index < key.length && text[start + index] != '\t') {
            val comparison = text[start + index].compareTo(key[index])
            if (comparison != 0) return comparison
            index++
        }
        return when { index == key.length && text[start + index] == '\t' -> 0
            index == key.length -> 1; else -> -1 }
    }
}

/** A byte- and count-bounded LRU; failed bundled shards are not repeatedly reopened. */
internal class ShardCache(private val assets: AssetManager, private val limit: Int, private val byteLimit: Int) {
    private val shards = LinkedHashMap<String, IndexedShard>(limit, 0.75f, true)
    private val unavailable = mutableSetOf<String>()
    private var bytes = 0

    @Synchronized fun get(path: String): IndexedShard? {
        shards[path]?.let { return it }
        if (path in unavailable) return null
        val shard = runCatching { workTrace("shard-load") {
            val bytes = assets.open(path).use { it.readBytes() }
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                requireNotNull(zip.nextEntry)
                ObjectInputStream(zip).use { objects ->
                    val text = objects.readObject() as String
                    val precomputed = runCatching {
                        assets.open("$path.idx").use { source -> DataInputStream(source).use { data ->
                            require(data.readInt() == 0x484b4931 && data.readInt() == text.length)
                            val crc = CRC32().apply { update(bytes) }.value.toInt()
                            require(data.readInt() == crc)
                            val count = data.readInt()
                            require(count in 0..(text.length / 2 + 1))
                            IndexedShard(text, IntArray(count) { data.readInt() })
                        } }
                    }.getOrNull()
                    precomputed ?: IndexedShard(text)
                }
            }
        } }.getOrNull() ?: run { unavailable.add(path); return null }
        if (shard.byteSize > byteLimit) return shard
        shards[path] = shard
        bytes += shard.byteSize
        while ((shards.size > limit || bytes > byteLimit) && shards.size > 1) {
            val oldest = shards.entries.first()
            bytes -= oldest.value.byteSize
            shards.remove(oldest.key)
        }
        return shard
    }
}
