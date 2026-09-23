package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDictionaryManagerTest {
    @Test fun customChoicesLeadBundledWithoutDuplicates() {
        assertEquals(listOf("唔", "你", "呢"),
            prioritizeCustomCandidates(listOf("唔", "你"), listOf("你", "呢")))
    }

    @Test fun learnedFrequencyCanStillBeatCustomChoices() {
        val merged = prioritizeCustomCandidates(listOf("唔"), listOf("你", "呢"))
        assertEquals(listOf("你", "唔", "呢"), rankCandidates(merged, mapOf("你" to 3)))
    }
}
