package com.awcjack.dualquickime.data

/** Small offline English lexicon; only an unambiguous single-edit typo is suggested. */
object EnglishSuggestions {
    private val commonWords = """
        about above across action active actual address after again against agree almost alone along already always
        amount answer anyone anything appear around arrive article artist asked asking attention available away because
        become before began begin behind believe below better between beyond black blue board book both bottom break
        bring brother business called camera cannot care careful carry cause centre certain change changed changes
        character check child children choice choose city clear close coffee colour coming common company complete
        computer concern condition consider contact continue correct correction could country course create current
        customer daily dark data daughter decide decided decision default define degree delete design detail different
        difficult direct direction display distance document does doing dollar done download early easily easy effect
        effort either email enough enter entire example except experience explain extra family father feature feel
        feeling field figure file final finally find first follow following food form found friend from further future
        game general getting give given global going good government great green group growing guess happy hard having
        head health heart hello help helpful here high history hold home house however human idea important improve
        include including increase information input inside instead interest interesting into issue item itself
        keyboard language large later learn learning left letter level likely line list little local long longer look
        looking make making manage many matter maybe meaning member message method middle might minute model money
        month more morning mother move much music must name natural near need never next night normal note nothing
        number office often once only open option order original other outside over page paper parent part people
        perhaps person phone phrase place please point possible power present pretty preview previous private
        problem process product project provide public question quick quite rather read reading ready really reason
        receive recent record reduce reference remember report request response result return right room same save
        saying school screen search second select selected selection send sentence separate service setting settings
        several should show simple since single sister small software some someone something sometimes source space
        special specific spell spelling start started state still stop store story string student study such support
        sure symbol system table take taking talk teacher tell test text than thank thanks their them there these
        thing think thinking this those though through time today together tomorrow tool total touch toward track
        traditional translate true trying turn type typed typing under understand update usage used useful user
        using usually value version very view voice wait want wanted warning water were what when where whether
        which while white whole will window with within without woman word words work working world would write
        writing wrong year yellow yesterday young your yourself candidate candidates cantonese cangjie chinese
        english keyboard clipboard shortcut convert conversion correction dictionary frequency suggestion
    """.trimIndent().split(Regex("\\s+")).toSet()

    fun correction(typed: String): String? {
        if (typed.length !in 5..20 || typed.any { it !in 'a'..'z' && it !in 'A'..'Z' }) return null
        val lower = typed.lowercase()
        if (lower in commonWords) return null
        val matches = commonWords.asSequence()
            .filter { kotlin.math.abs(it.length - lower.length) <= 1 && oneEditApart(lower, it) }
            .take(2).toList()
        if (matches.size != 1) return null
        val suggestion = matches.single()
        return when {
            typed.all { it.isUpperCase() } -> suggestion.uppercase()
            typed.first().isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                suggestion.replaceFirstChar { it.uppercase() }
            typed.all { it.isLowerCase() } -> suggestion
            else -> null
        }
    }

    /** One insertion, deletion, substitution, or adjacent transposition. */
    internal fun oneEditApart(input: String, word: String): Boolean {
        if (input == word || kotlin.math.abs(input.length - word.length) > 1) return false
        if (input.length == word.length) {
            val mismatches = input.indices.filter { input[it] != word[it] }
            return mismatches.size == 1 ||
                (mismatches.size == 2 && mismatches[1] == mismatches[0] + 1 &&
                    input[mismatches[0]] == word[mismatches[1]] &&
                    input[mismatches[1]] == word[mismatches[0]])
        }
        val longer = if (input.length > word.length) input else word
        val shorter = if (input.length > word.length) word else input
        var i = 0
        var j = 0
        var skipped = false
        while (i < longer.length && j < shorter.length) {
            if (longer[i] == shorter[j]) { i++; j++ }
            else if (!skipped) { skipped = true; i++ }
            else return false
        }
        return true
    }
}

/** Exact English keywords only; these always follow the dictionary candidates. */
object UnicodeWordSuggestions {
    private val words = mapOf(
        "star" to listOf("★", "☆"),
        "heart" to listOf("♥", "♡"),
        "arrow" to listOf("→", "←", "↑", "↓", "↔"),
        "check" to listOf("✓", "✔", "☑"),
        "tick" to listOf("✓", "✔"),
        "cross" to listOf("✗", "✘", "×"),
        "circle" to listOf("○", "●", "◯"),
        "square" to listOf("□", "■", "▪"),
        "triangle" to listOf("△", "▲", "▽", "▼"),
        "diamond" to listOf("◇", "◆"),
        "music" to listOf("♪", "♫", "♬"),
        "note" to listOf("♪", "♫"),
        "sun" to listOf("☀", "☼"),
        "moon" to listOf("☾", "☽"),
        "degree" to listOf("°", "℃", "℉"),
        "infinity" to listOf("∞"),
        "copyright" to listOf("©"),
        "registered" to listOf("®"),
        "trademark" to listOf("™"),
        "bullet" to listOf("•", "◦"),
        "bracket" to listOf("[", "]", "(", ")", "{", "}", "«", "»", "「", "」", "『", "』", "【", "】"),
        "quote" to listOf("'", "\"", "‘", "’", "“", "”", "«", "»", "「", "」")
    )

    fun lookupEnglish(word: String): List<String> = words[word.lowercase()].orEmpty()
}

/** Alternatives shown after tapping a bracket or quote key. */
object SymbolAlternatives {
    private val opening = listOf("[", "(", "{", "（", "「", "『", "【", "〈", "《", "«")
    private val closing = listOf("]", ")", "}", "）", "」", "』", "】", "〉", "》", "»")

    fun forKey(key: Char): List<String> = when (key.toString()) {
        in opening -> opening.filterNot { it == key.toString() }
        in closing -> closing.filterNot { it == key.toString() }
        "'" -> listOf("‘", "’", "′")
        "\"" -> listOf("“", "”", "「", "」")
        else -> emptyList()
    }
}
