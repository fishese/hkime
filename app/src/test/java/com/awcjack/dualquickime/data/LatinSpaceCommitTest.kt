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

    @Test fun swallowedSpaceIsDeferredOnlyWhenRestoreIsAlsoOn() {
        assertTrue(LatinSpaceCommit.deferCommitSpace(
            ignoreCommitSpace = true, restoreBetweenLatin = true, committedRawLatin = true))
        assertFalse(LatinSpaceCommit.deferCommitSpace(
            ignoreCommitSpace = true, restoreBetweenLatin = false, committedRawLatin = true))
        assertFalse(LatinSpaceCommit.deferCommitSpace(
            ignoreCommitSpace = true, restoreBetweenLatin = true, committedRawLatin = false))
        assertFalse(LatinSpaceCommit.deferCommitSpace(
            ignoreCommitSpace = false, restoreBetweenLatin = true, committedRawLatin = true))
    }

    @Test fun deferredSpaceReturnsOnlyBeforeAnotherLatinWord() {
        assertTrue(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = true, nextCommitIsLatin = true))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = false, deferred = true, nextCommitIsLatin = true))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = true, nextCommitIsLatin = false))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = false, nextCommitIsLatin = true))
    }

    @Test fun deferredSpaceReturnsBeforeANumberButNotOnceAlreadyUsed() {
        assertTrue(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = true,
            nextCommitIsLatin = false, nextCommitIsNumber = true))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = false,
            nextCommitIsLatin = false, nextCommitIsNumber = true))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = false, deferred = true,
            nextCommitIsLatin = false, nextCommitIsNumber = true))
    }

    @Test fun unrecognizedLetterStringsStayLatin() {
        assertTrue(LatinSpaceCommit.keptAsLatin("check"))
        assertTrue(LatinSpaceCommit.keptAsLatin("asdf"))
        assertTrue(LatinSpaceCommit.keptAsLatin("don't"))
        assertFalse(LatinSpaceCommit.keptAsLatin("左"))
        assertFalse(LatinSpaceCommit.keptAsLatin("check左"))
        assertTrue(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = true,
            nextCommitIsLatin = LatinSpaceCommit.keptAsLatin("asdf")))
        assertFalse(LatinSpaceCommit.restoreDeferredSpace(
            restoreBetweenLatin = true, deferred = true,
            nextCommitIsLatin = LatinSpaceCommit.keptAsLatin("左")))
    }
}
