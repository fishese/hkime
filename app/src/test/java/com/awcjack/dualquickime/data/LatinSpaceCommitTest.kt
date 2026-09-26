package com.awcjack.dualquickime.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinSpaceCommitTest {
    @Test fun optionOffAlwaysInsertsSpace() {
        assertTrue(LatinSpaceCommit.insertsSpace(ignoreCommitSpace = false, committingLatin = true))
        assertTrue(LatinSpaceCommit.insertsSpace(ignoreCommitSpace = false, committingLatin = false))
    }

    @Test fun firstSpaceAfterLatinIsSwallowed() {
        assertFalse(LatinSpaceCommit.insertsSpace(ignoreCommitSpace = true, committingLatin = true))
    }

    @Test fun laterSpaceStillInserts() {
        assertTrue(LatinSpaceCommit.insertsSpace(ignoreCommitSpace = true, committingLatin = false))
    }
}
