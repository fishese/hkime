package com.awcjack.dualquickime.data

import org.junit.Assert.*
import org.junit.Test

class LearnedPhraseModelTest {
    private val month = LearnedPhraseModel.HALF_LIFE_MS.toLong()

    @Test fun learnsEachStepOfASelectedPhraseAndSeparateSelections() {
        val whole = LearnedPhraseModel()
        whole.recordAppend("", "落樓話我知", 0)
        val steps = LearnedPhraseModel()
        var prefix = ""
        for (char in LearnedPhraseModel.characters("落樓話我知")) {
            steps.recordAppend(prefix, char, 0)
            prefix += char
        }
        assertEquals(whole.entries(), steps.entries())
        for ((context, next) in listOf("落" to "樓", "落樓" to "話", "落樓話" to "我", "落樓話我" to "知")) {
            assertEquals(next, whole.suggestions(context, 0).first())
        }
    }

    @Test fun frequencyOutranksNewEntryThenDecaysWithoutReuse() {
        val model = LearnedPhraseModel()
        repeat(8) { model.recordAppend("落", "樓", 0) }
        model.recordAppend("落", "雨", month)
        assertEquals(listOf("樓", "雨"), model.suggestions("落", month))
        model.recordAppend("落", "雨", month * 4)
        assertEquals(listOf("雨", "樓"), model.suggestions("落", month * 4))
    }

    @Test fun specificContextWinsOverPopularSingleCharacterMatchAndDeduplicates() {
        val model = LearnedPhraseModel()
        repeat(10) { model.recordAppend("高", "樓上", 0) }
        model.recordAppend("落樓", "話", 0)
        model.recordAppend("樓", "話", 0)
        assertEquals(listOf("話", "上"), model.suggestions("落樓", 0))
    }

    @Test fun supplementaryHanIsOneCharacterAndContextIsBounded() {
        val rare = "𠵱"
        val model = LearnedPhraseModel()
        model.recordAppend("落", rare + "家", 0)
        assertEquals(listOf(rare), model.suggestions("落", 0))
        assertEquals(listOf("家"), model.suggestions("落$rare", 0))
        assertEquals(8, LearnedPhraseModel.characters(LearnedPhraseModel.tail(rare.repeat(10))).size)
    }

    @Test fun ignoresLatinPunctuationAndMalformedStoredEntries() {
        val model = LearnedPhraseModel(listOf(
            LearnedPhraseModel.Entry("a", "樓", 1.0, 0),
            LearnedPhraseModel.Entry("落", "樓", Double.NaN, 0),
            LearnedPhraseModel.Entry("落", "樓", 1.0, -1),
            LearnedPhraseModel.Entry("落", "樓話", 1.0, 0)))
        model.recordAppend("落", "hello", 0)
        model.recordAppend("落", "樓。", 0)
        assertTrue(model.entries().isEmpty())
    }

    @Test fun staleEntriesDisappearAndClockRollbackDoesNotInflateScores() {
        val model = LearnedPhraseModel()
        model.recordAppend("落", "樓", month)
        assertEquals(listOf("樓"), model.suggestions("落", 0))
        assertTrue(model.suggestions("落", month * 5).isEmpty())
        model.recordAppend("落", "雨", month * 5)
        assertEquals(listOf("雨"), model.suggestions("落", month * 5))
        assertEquals(1, model.entries().size)
    }

    @Test fun capsGlobalStorageAndPerPrefixAlternatives() {
        val model = LearnedPhraseModel()
        for (i in 0 until 3000) {
            model.recordAppend("落", String(Character.toChars(0x4e00 + i)), i.toLong())
        }
        assertEquals(LearnedPhraseModel.MAX_PER_PREFIX, model.entries().size)
        for (i in 0 until 2500) {
            model.recordAppend(String(Character.toChars(0x4e00 + i)), "樓", i.toLong())
        }
        assertEquals(LearnedPhraseModel.MAX_ENTRIES, model.entries().size)
    }
}
