package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentCandidateManagerTest {
    @Test
    fun learnedNgSelectionBeatsDictionaryOrder() {
        val original = listOf("丑", "五", "嗯", "角", "唔")
        assertEquals(listOf("唔", "丑", "五", "嗯", "角"), rankCandidates(original, mapOf("唔" to 1)))
    }

    @Test
    fun higherSelectionCountWinsAndTiesKeepBaseOrder() {
        val original = listOf("甲", "乙", "丙", "丁")
        assertEquals(listOf("丙", "甲", "乙", "丁"), rankCandidates(original, mapOf("丙" to 3, "乙" to 2, "甲" to 2)))
    }
}
