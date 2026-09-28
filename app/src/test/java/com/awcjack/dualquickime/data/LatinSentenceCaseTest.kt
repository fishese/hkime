package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinSentenceCaseTest {
    @Test fun capitalizesABlankLineAndTheLetterAfterAFullStopAndSpace() {
        assertTrue(LatinSentenceCase.contextCapital("", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("\n", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("   ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("hello\n", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("hello. ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("hello.  ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("你好。 ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("blah blah? ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("wait! ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("真的？ ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("stop！ ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("done… ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("really?! ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("hello?\" ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("你好？」 ", composingLength = 0))
        assertTrue(LatinSentenceCase.contextCapital("ended at 3. ", composingLength = 0))
    }

    @Test fun leavesOtherLatinLowercase() {
        assertFalse(LatinSentenceCase.contextCapital("hello", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello ", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello.", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello?", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello, ", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello; ", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("3.14 ", composingLength = 0))
        assertFalse(LatinSentenceCase.contextCapital("hello. ", composingLength = 1))
    }

    @Test fun optionOffKeepsKeyCapitalsAndTypesShiftCase() {
        assertTrue(LatinSentenceCase.keyShowsUpper(capsLock = false, manualShift = false, sentenceCase = false, contextCapital = false))
        assertFalse(LatinSentenceCase.typedIsUpper(capsLock = false, manualShift = false, sentenceCase = false, contextCapital = true))
        assertTrue(LatinSentenceCase.typedIsUpper(capsLock = false, manualShift = true, sentenceCase = false, contextCapital = false))
        assertTrue(LatinSentenceCase.typedIsUpper(capsLock = true, manualShift = false, sentenceCase = false, contextCapital = false))
    }

    @Test fun optionOnShowsTheCaseThatWillBeTypedAndShiftFlipsIt() {
        assertTrue(LatinSentenceCase.typedIsUpper(capsLock = false, manualShift = false, sentenceCase = true, contextCapital = true))
        assertTrue(LatinSentenceCase.keyShowsUpper(capsLock = false, manualShift = false, sentenceCase = true, contextCapital = true))
        assertFalse(LatinSentenceCase.typedIsUpper(capsLock = false, manualShift = true, sentenceCase = true, contextCapital = true))
        assertFalse(LatinSentenceCase.keyShowsUpper(capsLock = false, manualShift = true, sentenceCase = true, contextCapital = true))
        assertFalse(LatinSentenceCase.keyShowsUpper(capsLock = false, manualShift = false, sentenceCase = true, contextCapital = false))
        assertTrue(LatinSentenceCase.keyShowsUpper(capsLock = true, manualShift = false, sentenceCase = true, contextCapital = false))
        assertEquals('H', LatinSentenceCase.letter('h', upper = true))
        assertEquals('h', LatinSentenceCase.letter('h', upper = false))
    }
}
