package com.awcjack.dualquickime.data

/**
 * Space is the key that finishes uncommitted Latin letters. When the option is
 * on, that press commits the letters and is not also inserted. A later space,
 * once nothing is left to commit, still inserts a space.
 */
object LatinSpaceCommit {
    fun insertsSpace(ignoreCommitSpace: Boolean, committingLatin: Boolean): Boolean {
        return !(ignoreCommitSpace && committingLatin)
    }
}
