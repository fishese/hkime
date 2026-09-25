package com.awcjack.dualquickime.data

/** Removes entries with no visible glyph before candidates reach the keyboard UI. */
internal fun sanitizeCandidates(candidates: List<String>): List<String> =
    candidates.filter { candidate ->
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
