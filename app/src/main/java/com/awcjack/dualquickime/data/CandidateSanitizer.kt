package com.awcjack.dualquickime.data

/** Removes malformed empty entries before candidates reach the keyboard UI. */
internal fun sanitizeCandidates(candidates: List<String>): List<String> =
    candidates.filter { candidate ->
        candidate.any { char ->
            !char.isWhitespace() &&
                !char.isISOControl() &&
                Character.getType(char) != Character.FORMAT
        }
    }.distinct()
