package com.awcjack.dualquickime.data

private val BLOCKED_CANDIDATES = setOf("愛國愛港", "爱国爱港")

/** Removes blocked, invisible, and duplicate entries before candidates reach the keyboard UI. */
internal fun sanitizeCandidates(candidates: List<String>): List<String> =
    candidates.filter { candidate ->
        candidate !in BLOCKED_CANDIDATES &&
            candidate.codePoints().anyMatch { codePoint ->
                val type = Character.getType(codePoint)
                !Character.isWhitespace(codePoint) &&
                    !Character.isSpaceChar(codePoint) &&
                    !Character.isISOControl(codePoint) &&
                    type != Character.FORMAT.toInt() &&
                    type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.ENCLOSING_MARK.toInt() &&
                    type != Character.SURROGATE.toInt()
            }
    }.distinct()

/** Also blocks suffix suggestions that would complete a blocked full phrase. */
internal fun sanitizeAssociatedPhraseCandidates(
    context: String,
    candidates: List<String>
): List<String> = sanitizeCandidates(candidates).filterNot { candidate ->
    BLOCKED_CANDIDATES.any { blocked -> (context + candidate).endsWith(blocked) }
}
