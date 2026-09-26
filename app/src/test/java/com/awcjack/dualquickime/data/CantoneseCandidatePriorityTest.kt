package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CantoneseCandidatePriorityTest {
    @Test fun zoLiftsAboveRareCharactersButNotCommonOnes() {
        assertEquals(
            listOf("座", "助", "左", "坐", "阻", "咗", "佐", "詛", "俎"),
            promoteEverydayCantonese(listOf("座", "助", "左", "坐", "阻", "佐", "詛", "俎", "咗"))
        )
    }

    @Test fun laaStaysBehindCommonCharacters() {
        assertEquals(
            listOf("書", "那", "啦", "哪", "喇", "辣"),
            promoteEverydayCantonese(listOf("書", "那", "啦", "哪", "喇", "辣"))
        )
    }

    @Test fun mouMovesAheadOfUncommonCharacters() {
        val original = listOf(
            "仄", "無", "次", "兩", "歌", "務", "武", "舞", "靈", "模", "母", "莫", "毛", "冇"
        )
        assertEquals(
            listOf("無", "次", "兩", "歌", "務", "武", "舞", "模", "母", "毛", "冇", "仄", "靈", "莫"),
            promoteEverydayCantonese(original)
        )
    }

    @Test fun geMovesAheadOfRareHomophones() {
        assertEquals(
            listOf("報", "趣", "鼓", "嘅", "圾", "坡", "殼", "穀"),
            promoteEverydayCantonese(listOf("報", "趣", "鼓", "圾", "坡", "殼", "穀", "嘅"))
        )
    }

    @Test fun phrasesAndAlreadyHighParticlesStayPut() {
        assertEquals(
            listOf("無", "升降機", "冇"),
            promoteEverydayCantonese(listOf("無", "升降機", "冇"))
        )
        assertEquals(
            listOf("啲", "木"),
            promoteEverydayCantonese(listOf("啲", "木"))
        )
    }
}
