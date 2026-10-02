package com.awcjack.dualquickime.theme

/**
 * Data class holding all keyboard theme colors.
 */
data class KeyboardColors(
    val keyboardBackground: Int,
    val keyBackground: Int,
    val keyBackgroundPressed: Int,
    val specialKeyBackground: Int,
    val specialKeyBackgroundPressed: Int,
    val spaceKeyBackground: Int,
    val candidateBarBackground: Int,
    val candidatePillBackground: Int,
    val candidatePillBackgroundPressed: Int,
    val keyTextPrimary: Int,
    val keyTextSecondary: Int,
    val candidateText: Int,
    val compositionText: Int,
    val englishPillText: Int,
    val pageIndicatorText: Int,
    val noMatchText: Int,
    val keyShadowColor: Int,
    val emojiCategoryBackground: Int,
    val emojiCategorySelectedBackground: Int,
    val emojiCategoryText: Int,
    val emojiCategorySelectedText: Int,
    // Clipboard colors
    val clipboardItemBackground: Int,
    val clipboardItemBackgroundPressed: Int,
    val clipboardItemText: Int,
    val clipboardPinnedIcon: Int,
    val clipboardDeleteIcon: Int,
    val clipboardEmptyText: Int
) {
    val learnedCandidateText: Int get() =
        if (androidx.core.graphics.ColorUtils.calculateLuminance(candidatePillBackground) < 0.5)
            0xFF80CBC4.toInt() else 0xFF00796B.toInt()

    // Convenience property for accent color (same as compositionText)
    val accentColor: Int get() = compositionText
}
