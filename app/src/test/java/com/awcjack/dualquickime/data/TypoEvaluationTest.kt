package com.awcjack.dualquickime.data

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixed synthetic inputs; invokes the production matcher and merge, never a scoring replica. */
class TypoEvaluationTest {
    @Test fun evaluateBoundedCorpus() {
        val assets = File("src/main/assets")
        val lexicon = File(assets, "english-autocomplete.txt").inputStream().use(EnglishAutocomplete::parse)
        val english = EnglishTypoMatcher(lexicon.allWords(), EnglishSuggestions.typoWords())
        val rows = File(assets, "method-membership.tsv").readLines().filterNot { it.startsWith('#') }
            .map { it.split('\t', limit = 3) }.filter { it.size == 3 }
        val membership = MethodMembership(rows.asSequence().map { it.joinToString("\t") },
            File(assets, "method-phrase-overrides.tsv").readLines().asSequence())
        val methods = setOf(MethodMembership.Method.CANTONESE, MethodMembership.Method.CANGJIE)
        fun candidates(typed: String): List<String> = mergeTypoCandidates(typed, emptyList(),
            english.candidates(typed) + membership.recoverCodes(typed, methods).flatMap { it.typedCandidates() })
        data class Case(val lane: String, val typed: String, val target: String)
        val cases = mutableListOf<Case>()
        val random = Random(73)
        val bands = listOf(4..4, 5..8, 9..16, 17..32)
        val selectedWords = (bands.flatMap { band -> lexicon.allWords().filter { it.length in band }
            .shuffled(random).take(25) } + lexicon.allWords().filter { it.length > 20 }).distinct()
        for (word in selectedWords) {
            for (typed in KeyProximity.codeVariants(word, false).keys) {
                if (!lexicon.contains(typed)) cases += Case("english", typed, word)
            }
        }
        for (name in listOf("cantonese", "cangjie")) {
            val allowed = rows.filter { it[1] == name && it[0].length in 3..(if (name == "cangjie") 5 else 12) }
            val exactCodes = allowed.map { it[0] }.toSet()
            for (row in allowed.shuffled(random).take(50)) {
                val target = String(Character.toChars(row[2].codePointAt(0)))
                for (typed in KeyProximity.codeVariants(row[0], false).keys) {
                    if (typed !in exactCodes) cases += Case(name, typed, target)
                }
            }
        }
        repeat(100) { candidates("sanf") } // Warm the same pipeline in both runs.
        val output = StringBuilder("lane,total,top6,top12,retained,mean_candidates,p50_us,p95_us,max_us,max_candidates\n")
        for ((lane, group) in cases.groupBy { it.lane }) {
            var top6 = 0; var top12 = 0; var retained = 0; var count = 0L; var maxCandidates = 0
            val timings = mutableListOf<Long>()
            for (case in group) {
                val start = System.nanoTime()
                val result = candidates(case.typed)
                timings += (System.nanoTime() - start) / 1000
                val index = result.indexOf(case.target)
                if (index in 0..5) top6++
                if (index in 0..11) top12++
                if (index >= 0) retained++
                count += result.size
                maxCandidates = maxOf(maxCandidates, result.size)
            }
            timings.sort()
            output.append("$lane,${group.size},$top6,$top12,$retained,${count.toDouble()/group.size}," +
                "${timings[timings.size/2]},${timings[(timings.size*95/100).coerceAtMost(timings.lastIndex)]},${timings.last()},$maxCandidates\n")
        }
        for (word in selectedWords) assertTrue("valid English $word", english.suggestions(word).isEmpty())
        val randomCounts = List(100) { (1..6).map { ('a'.code + random.nextInt(26)).toChar() }.joinToString("") }
            .map { candidates(it).size }
        output.append("random_controls,100,mean_candidates=${randomCounts.average()},max=${randomCounts.maxOrNull()}\n")
        for (typed in listOf("sanf", "stah", "oftrm", "sohnds", "nfo")) {
            output.append("example,$typed,${candidates(typed).joinToString("|")}\n")
        }
        File("build/typo-evaluation-current.csv").apply { parentFile?.mkdirs(); writeText(output.toString()) }
        println(output.toString())
    }
}
