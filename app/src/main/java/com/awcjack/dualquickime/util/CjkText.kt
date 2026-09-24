package com.awcjack.dualquickime.util

/**
 * Shared helpers for detecting CJK characters and converting only the CJK runs
 * of a string (leaving Latin / digits / whitespace untouched).
 *
 * Fullwidth Latin letters and digits are deliberately excluded from OpenCC
 * conversion, even though they share a Unicode block with CJK punctuation.
 */
object CjkText {

    /**
     * Whether [ch] should be treated as CJK for OpenCC conversion / segmentation.
     * Covers the ideograph blocks and CJK symbols / punctuation. The Halfwidth &
     * Fullwidth Forms block is included ONLY for its punctuation / sign ranges —
     * fullwidth Latin letters and fullwidth digits are deliberately excluded so
     * they are neither grouped into a CJK run nor fed to OpenCC.
     */
    fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs (main block)
            code in 0x3400..0x4DBF ||      // Extension A
            code in 0x2E80..0x2FDF ||      // Radicals Supplement / Kangxi Radicals
            code in 0x3000..0x303F ||      // CJK Symbols and Punctuation
            code in 0xF900..0xFAFF ||      // Compatibility Ideographs
            code in 0xFE30..0xFE4F ||      // Compatibility Forms
            isFullwidthPunctuation(code)
    }

    /**
     * Halfwidth & Fullwidth Forms (0xFF00..0xFFEF) minus the fullwidth ASCII
     * alphanumerics: digits 0xFF10..0xFF19, uppercase 0xFF21..0xFF3A and
     * lowercase 0xFF41..0xFF5A. Everything else in the block is punctuation /
     * signs (！ ？ （ ） ￥ …) that belongs with the surrounding CJK run.
     */
    private fun isFullwidthPunctuation(code: Int): Boolean {
        if (code !in 0xFF00..0xFFEF) return false
        if (code in 0xFF10..0xFF19) return false
        if (code in 0xFF21..0xFF3A) return false
        if (code in 0xFF41..0xFF5A) return false
        return true
    }

    /**
     * Apply [convert] to the maximal CJK runs of [text], copying non-CJK runs
     * through unchanged. Used so English embedded in a transcription / selection
     * is never handed to OpenCC.
     */
    fun convertCjkOnly(text: String, convert: (String) -> String): String {
        if (text.isEmpty()) return text
        val result = StringBuilder(text.length)
        val segment = StringBuilder()
        var inCjk = false
        for (ch in text) {
            val cjk = isCjk(ch)
            if (cjk == inCjk) {
                segment.append(ch)
            } else {
                if (segment.isNotEmpty()) {
                    result.append(if (inCjk) convert(segment.toString()) else segment)
                    segment.clear()
                }
                segment.append(ch)
                inCjk = cjk
            }
        }
        if (segment.isNotEmpty()) {
            result.append(if (inCjk) convert(segment.toString()) else segment)
        }
        return result.toString()
    }
}
