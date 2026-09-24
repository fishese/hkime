package com.awcjack.dualquickime.data

/** Small offline English lexicon; only an unambiguous single-edit typo is suggested. */
object EnglishSuggestions {
    fun isLatinWord(candidate: String): Boolean = candidate.isNotEmpty() &&
        candidate.all { it in 'a'..'z' || it in 'A'..'Z' }

    private val commonWords = """
        an and are as at be by can do for has have he her him his how if in is it its may me my no not of on
        or our out she so that the they this to too us was we who why yes you
        cat dog fish bird rabbit horse cow pig bear mouse tiger lion whale dolphin butterfly
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

    /** A small, deterministic prefix list; the caller ranks it after Chinese candidates. */
    fun completions(typed: String): List<String> {
        if (typed.length !in 2..20 || typed.any { !it.isLetter() || it.code > 127 }) return emptyList()
        val lower = typed.lowercase()
        return commonWords.asSequence()
            .filter { it.startsWith(lower) && it.length >= lower.length }
            .sortedWith(compareBy<String> { it.length }.thenBy { it })
            .take(8)
            .map { word -> when {
                typed.all { it.isUpperCase() } -> word.uppercase()
                typed.first().isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                    word.replaceFirstChar { it.uppercase() }
                else -> word
            } }
            .toList()
    }

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
        "cat" to listOf("🐈", "🐈‍⬛", "🐱"),
        "dog" to listOf("🐕", "🐶", "🐕‍🦺"),
        "fish" to listOf("🐟", "🐠", "🐡"),
        "bird" to listOf("🐦", "🐤"),
        "rabbit" to listOf("🐇", "🐰"),
        "horse" to listOf("🐎", "🐴"),
        "cow" to listOf("🐄", "🐮"),
        "pig" to listOf("🐖", "🐷"),
        "bear" to listOf("🐻", "🐻‍❄️"),
        "mouse" to listOf("🐁", "🐭"),
        "tiger" to listOf("🐅", "🐯"),
        "lion" to listOf("🦁"),
        "whale" to listOf("🐋", "🐳"),
        "dolphin" to listOf("🐬"),
        "butterfly" to listOf("🦋"),
        "check" to listOf("✓", "✔", "☑"),
        "tick" to listOf("✓", "✔"),
        "cross" to listOf("✗", "✘", "×"),
        "circle" to listOf("○", "●", "◯"),
        "sun" to listOf("☀", "☼"),
        "moon" to listOf("☾", "☽"),
    )

    fun lookupEnglish(word: String): List<String> =
        (words[word.lowercase()].orEmpty() + SymbolCatalogue.lookupEnglish(word)).distinct()
}

/** Compatibility entry point for symbol-key alternatives. */
object SymbolAlternatives {
    fun forKey(key: Char): List<String> = SymbolCatalogue.candidatesForSymbol(key.toString())
}
