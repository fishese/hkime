package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ChineseTypoRecoveryTest {
    private val cantonese = setOf(MethodMembership.Method.CANTONESE)
    private val cangjie = setOf(MethodMembership.Method.CANGJIE)
    private fun fixture() = MethodMembership(sequenceOf(
        "ngo\tcantonese\t我餓鵝",
        "nfo\tquick\t甲",
        "dam\tcangjie\t查",
        "abc\tquick\t乙",
    ), sequenceOf("ngo\tcantonese\t我哋", "nfo\tcantonese\t年貨"))

    @Test fun nfoRecoversNgoAndItsCharactersFromTheBundledAsset() {
        val membership = File("src/main/assets/method-membership.tsv").bufferedReader().use {
            MethodMembership(it.lineSequence())
        }
        val recovered = membership.recoverCodes("nfo", cantonese)
        val ngo = recovered.single { it.code == "ngo" }
        assertTrue("我" in ngo.candidates)
        assertTrue("餓" in ngo.candidates)
    }

    @Test fun recoveryPreservesVerifiedCharactersAndOverrides() {
        val recovery = fixture().recoverCodes("nfo", cantonese).single()
        assertEquals("ngo", recovery.code)
        assertEquals(listOf("我", "餓", "鵝", "我哋"), recovery.candidates)
        assertEquals(listOf("年貨"), fixture().supplementalCandidates("nfo", cantonese))
    }

    @Test fun cangjieHandlesNeighbourMissingExtraAndTransposedKeys() {
        for (typo in listOf("sam", "daam", "adm")) {
            assertTrue(typo, fixture().recoverCodes(typo, cangjie).any { it.code == "dam" && "查" in it.candidates })
        }
        val fourKey = MethodMembership(sequenceOf("damo\tcangjie\t甲"))
        assertEquals("damo", fourKey.recoverCodes("dam", cangjie).single().code)
    }

    @Test fun quickDisabledMethodsShortInputsAndMultiEditCodesAreExcluded() {
        assertTrue(fixture().recoverCodes("nfo", emptySet()).isEmpty())
        assertTrue(fixture().recoverCodes("nfo", cangjie).isEmpty())
        assertTrue(fixture().recoverCodes("abx", setOf(MethodMembership.Method.QUICK)).isEmpty())
        assertTrue(fixture().recoverCodes("nf", cantonese).isEmpty())
        assertTrue(fixture().recoverCodes("qqo", cantonese).isEmpty())
        assertTrue(fixture().recoverCodes("nfoo", cantonese).isEmpty())
    }

    @Test fun capsCorrectedCodesButDoesNotTruncateTheirCharacterChoices() {
        val membership = MethodMembership(sequenceOf(
            "ngo\tcantonese\t我餓鵝臥俄傲哦娥",
            "nro\tcantonese\t甲", "nto\tcantonese\t乙", "nco\tcantonese\t丙"
        ))
        val results = membership.recoverCodes("nfo", cantonese)
        assertEquals(3, results.size)
        assertEquals(8, results.single { it.code == "ngo" }.candidates.size)
    }
}
