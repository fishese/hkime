package com.awcjack.dualquickime.data

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

/** Opt-in desktop comparison, not a phone performance gate or a timed CI assertion. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OptimizationWorkloadTest {
    private fun samples(action: () -> Unit): List<Long> {
        repeat(100) { action() }
        return List(500) { val start = System.nanoTime(); action(); System.nanoTime() - start }.sorted()
    }
    private fun stats(values: List<Long>): String = "p50Us=${values[values.size/2]/1000}, p95Us=${values[values.size*95/100]/1000}"
    @Test fun compareSyntheticWorkAgainstFrozenReferenceImplementations() {
        val assets = RuntimeEnvironment.getApplication().assets
        val text = assets.open("mck/mix_map_ext_y1.cs2").use { input -> ZipInputStream(input).use { zip ->
            zip.nextEntry; ObjectInputStream(zip).use { it.readObject() as String }
        } }
        val keys = text.lineSequence().filter { '\t' in it }.map { it.substringBefore('\t') }.toList()
        val indexStart = System.nanoTime()
        val indexed = IndexedShard(text)
        println("WORKLOAD indexBuildUs=${(System.nanoTime()-indexStart)/1000}, indexBytes=${indexed.byteSize}")
        var counter = 0
        val legacyLookup = samples {
            val key = keys[counter++ % keys.size]
            val needle = "$key\t"
            val start = if (text.startsWith(needle)) 0 else text.indexOf("\n$needle").let { if (it < 0) -1 else it+1 }
            if (start >= 0) { val value = start+needle.length; text.substring(value, text.indexOf('\n', value).let { if(it<0) text.length else it }) }
        }
        counter=0
        val indexedLookup = samples { indexed.value(keys[counter++ % keys.size]) }
        println("WORKLOAD dictionaryScan ${stats(legacyLookup)}; indexedLookup ${stats(indexedLookup)}")
        val bytes = assets.open("english-autocomplete.txt").use { it.readBytes() }
        val legacy = LegacyEnglishAutocomplete.parse(bytes.inputStream())
        val current = EnglishAutocomplete.parse(bytes.inputStream())
        println("WORKLOAD completionLegacy ${stats(samples { legacy.completions("co") })}; boundedCompletion ${stats(samples { current.completions("co") })}")
        val now=1_800_000_000_000L
        val entries=(0 until 2000).map { LearnedPhraseModel.Entry("字"+(0x4e00+it).toChar(),"樓",1.0,now) }
        val oldModel=LegacyLearnedPhraseModel(entries.map { LegacyLearnedPhraseModel.Entry(it.prefix,it.next,it.score,it.updated) })
        val model=LearnedPhraseModel(entries)
        println("WORKLOAD learnedLegacy ${stats(samples { oldModel.suggestions(entries.last().prefix,now) })}; indexedLearned ${stats(samples { model.suggestions(entries.last().prefix,now) })}")
    }
}
