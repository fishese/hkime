package com.awcjack.dualquickime.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerformanceDataRegressionTest {
    @Test fun indexedLookupPreservesEveryBundledKeyAndValue() {
        val assets = RuntimeEnvironment.getApplication().assets
        val names = assets.list("mck")!!.filter { it.startsWith("mix_map_ext_") && it.endsWith(".cs2") }.map { "mck/$it" } +
            assets.list("mck/phrases")!!.filter { it.endsWith(".cs2") }.map { "mck/phrases/$it" }
        var checked = 0
        for (name in names) {
            val text = assets.open(name).use { input -> ZipInputStream(input).use { zip ->
                zip.nextEntry; ObjectInputStream(zip).use { it.readObject() as String }
            } }
            val expected = linkedMapOf<String, String>()
            text.lineSequence().filter { '\t' in it }.forEach { row -> expected.putIfAbsent(row.substringBefore('\t'), row.substringAfter('\t')) }
            val indexed = ShardCache(assets, 1, 8 * 1024 * 1024).get(name)!!
            for ((key, value) in expected) { assertEquals(value, indexed.value(key)); checked++ }
            assertNull(indexed.value("not-a-bundled-key!"))
        }
        println("Indexed shard parity: $checked keys across ${names.size} shards")
        assertTrue(checked > 100_000)
    }

    @Test fun completionsRetainLegacyRankingPluralRulesAndCase() {
        val bytes = RuntimeEnvironment.getApplication().assets.open("english-autocomplete.txt").use { it.readBytes() }
        val legacy = LegacyEnglishAutocomplete.parse(bytes.inputStream())
        val current = EnglishAutocomplete.parse(bytes.inputStream())
        val queries = (current.allWords().map { it.take(2) } + current.allWords().take(2000).flatMap { listOf(it, it.take(4)) } +
            listOf("co", "does", "uses", "news", "stah", "sanf", "AP", "App", "aa!", "")).distinct()
        queries.forEach { assertEquals(legacy.completions(it), current.completions(it)) }
    }

    @Test fun learnedRankingAndEvictionRemainEquivalentAtCapacity() {
        val now = 1_800_000_000_000L
        val initial = (0 until 2000).map { LearnedPhraseModel.Entry("字" + (0x4e00 + it).toChar(), "樓", 1.0 + it % 9, now - it * 123L) }
        val legacy = LegacyLearnedPhraseModel(initial.map { LegacyLearnedPhraseModel.Entry(it.prefix, it.next, it.score, it.updated) })
        val current = LearnedPhraseModel(initial)
        val random = Random(8042)
        var time = now
        repeat(160) {
            time += if (it % 29 == 0) 100L * 86_400_000 else random.nextLong(-2000, 2000)
            val prefix = (0 until random.nextInt(1, 9)).map { (0x4e00 + random.nextInt(30)).toChar() }.joinToString("")
            val selected = (0 until random.nextInt(1, 6)).map { (0x4e00 + random.nextInt(30)).toChar() }.joinToString("")
            legacy.recordAppend(prefix, selected, time); current.recordAppend(prefix, selected, time)
            assertEquals(legacy.suggestions(prefix, time), current.suggestions(prefix, time))
            assertEquals(legacy.entries().map { listOf(it.prefix, it.next, it.score, it.updated) },
                current.entries().map { listOf(it.prefix, it.next, it.score, it.updated) })
        }
    }

    @Test fun longSwipeIsBoundedAndRetainsEndpointsAndTiming() {
        val trace = BoundedSwipeTrace()
        repeat(10_000) { trace.add(SwipeTypingDecoder.Point((it % 180).toFloat(), (it % 47).toFloat(), it.toLong())) }
        assertTrue(trace.size <= BoundedSwipeTrace.MAX_POINTS)
        assertEquals(0L, trace.first().timeMs)
        assertEquals(9999L, trace.last().timeMs)
        assertTrue(trace.zipWithNext().all { (a,b) -> a.timeMs < b.timeMs })
        assertNotNull(SwipeTypingDecoder.decode(trace, mapOf('a' to SwipeTypingDecoder.Point(0f,0f),
            'b' to SwipeTypingDecoder.Point(100f,20f)), 100f, listOf("ab", "ba")))
    }
}
