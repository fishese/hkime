package com.awcjack.dualquickime.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

/**
 * Manages keyboard theme settings (light/dark/auto).
 */
object ThemeManager {
    private const val PREFS_NAME = "dualquick_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_SHOW_COMPOSITION = "show_composition"
    private const val KEY_CANDIDATES_PER_PAGE = "candidates_per_page"
    private const val KEY_USE_EXTENDED_CHARSET = "use_extended_charset"
    private const val KEY_RECENT_CANDIDATES_ENABLED = "recent_candidates_enabled"
    private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    private const val KEY_DEFAULT_SKIN_TONE = "default_skin_tone"
    private const val KEY_CHINESE_CONVERT_ENABLED = "chinese_convert_enabled"
    private const val KEY_CANDIDATE_PILL_PADDING = "candidate_pill_padding"
    private const val KEY_KEY_HEIGHT = "key_height_dp"
    private const val KEY_CANDIDATE_TEXT_SIZE = "candidate_text_sp"
    private const val KEY_SHOW_KEY_RADICALS = "show_key_radicals"
    private const val KEY_ENGLISH_SPELL_CHECK = "english_spell_check"
    private const val KEY_SPACE_AFTER_ENGLISH_CANDIDATE = "space_after_english_candidate"
    private const val KEY_METHOD_CANTONESE = "method_cantonese_enabled"
    private const val KEY_METHOD_CANGJIE = "method_cangjie_enabled"
    private const val KEY_METHOD_QUICK = "method_quick_enabled"
    private const val KEY_METHOD_ENGLISH = "method_english_enabled"
    private const val KEY_SETTINGS_CHINESE = "settings_chinese_enabled"

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_AUTO = 2

    // Default candidates per page (affects pill sizing)
    const val CANDIDATES_MIN = 4
    const val CANDIDATES_MAX = 10
    const val CANDIDATES_DEFAULT = 6

    // Horizontal padding inside each candidate pill, in dp. Smaller = more candidates fit per row.
    const val CANDIDATE_PADDING_MIN = 5
    const val CANDIDATE_PADDING_MAX = 15
    const val CANDIDATE_PADDING_DEFAULT = 10

    const val KEY_HEIGHT_MIN = 46
    const val KEY_HEIGHT_MAX = 76
    const val KEY_HEIGHT_DEFAULT = 55
    const val CANDIDATE_TEXT_MIN = 15
    const val CANDIDATE_TEXT_MAX = 30
    const val CANDIDATE_TEXT_DEFAULT = 22

    private var cachedTheme: Int = -1
    private var cachedShowComposition: Boolean? = null
    private var cachedCandidatesPerPage: Int = -1
    private var cachedUseExtendedCharset: Boolean? = null
    private var cachedRecentCandidatesEnabled: Boolean? = null
    private var cachedHapticFeedbackEnabled: Boolean? = null
    private var cachedDefaultSkinTone: Int = -1
    private var cachedChineseConvertEnabled: Boolean? = null
    private var cachedCandidatePillPadding: Int = -1
    private var cachedKeyHeight: Int = -1
    private var cachedCandidateTextSize: Int = -1
    private var cachedShowKeyRadicals: Boolean? = null
    private var cachedEnglishSpellCheck: Boolean? = null
    private var cachedSpaceAfterEnglishCandidate: Boolean? = null

    fun getMethodCantonese(context: Context) = getPrefs(context).getBoolean(KEY_METHOD_CANTONESE, true)
    fun getMethodCangjie(context: Context) = getPrefs(context).getBoolean(KEY_METHOD_CANGJIE, true)
    fun getMethodQuick(context: Context) = getPrefs(context).getBoolean(KEY_METHOD_QUICK, true)
    fun getMethodEnglish(context: Context) = getPrefs(context).getBoolean(KEY_METHOD_ENGLISH, true)

    fun setMethodCantonese(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_METHOD_CANTONESE, enabled).apply()
    fun setMethodCangjie(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_METHOD_CANGJIE, enabled).apply()
    fun setMethodQuick(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_METHOD_QUICK, enabled).apply()
    fun setMethodEnglish(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_METHOD_ENGLISH, enabled).apply()

    fun getSettingsChinese(context: Context) =
        getPrefs(context).getBoolean(KEY_SETTINGS_CHINESE,
            context.resources.configuration.locales[0].language == "zh")
    fun setSettingsChinese(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_SETTINGS_CHINESE, enabled).apply()

    fun getThemeMode(context: Context): Int {
        if (cachedTheme == -1) {
            cachedTheme = getPrefs(context).getInt(KEY_THEME, THEME_AUTO)
        }
        return cachedTheme
    }

    fun setThemeMode(context: Context, mode: Int) {
        cachedTheme = mode
        getPrefs(context).edit().putInt(KEY_THEME, mode).apply()
    }

    // Composition display settings
    fun getShowComposition(context: Context): Boolean {
        if (cachedShowComposition == null) {
            cachedShowComposition = getPrefs(context).getBoolean(KEY_SHOW_COMPOSITION, false)
        }
        return cachedShowComposition!!
    }

    fun setShowComposition(context: Context, show: Boolean) {
        cachedShowComposition = show
        getPrefs(context).edit().putBoolean(KEY_SHOW_COMPOSITION, show).apply()
    }

    // Candidates per page settings
    fun getCandidatesPerPage(context: Context): Int {
        if (cachedCandidatesPerPage == -1) {
            cachedCandidatesPerPage = getPrefs(context).getInt(KEY_CANDIDATES_PER_PAGE, CANDIDATES_DEFAULT)
        }
        return cachedCandidatesPerPage
    }

    fun setCandidatesPerPage(context: Context, count: Int) {
        cachedCandidatesPerPage = count.coerceIn(CANDIDATES_MIN, CANDIDATES_MAX)
        getPrefs(context).edit().putInt(KEY_CANDIDATES_PER_PAGE, cachedCandidatesPerPage).apply()
    }

    // Extended character set settings (default: true for extended)
    fun getUseExtendedCharset(context: Context): Boolean {
        if (cachedUseExtendedCharset == null) {
            cachedUseExtendedCharset = getPrefs(context).getBoolean(KEY_USE_EXTENDED_CHARSET, true)
        }
        return cachedUseExtendedCharset!!
    }

    fun setUseExtendedCharset(context: Context, useExtended: Boolean) {
        cachedUseExtendedCharset = useExtended
        getPrefs(context).edit().putBoolean(KEY_USE_EXTENDED_CHARSET, useExtended).apply()
    }

    /**
     * Get the appropriate simplex.cin filename based on settings.
     */
    fun getSimplexFilename(context: Context): String {
        return if (getUseExtendedCharset(context)) "simplex-ext.cin" else "simplex.cin"
    }

    // Learned candidate frequency is enabled for new installations.
    fun getRecentCandidatesEnabled(context: Context): Boolean {
        if (cachedRecentCandidatesEnabled == null) {
            cachedRecentCandidatesEnabled = getPrefs(context).getBoolean(KEY_RECENT_CANDIDATES_ENABLED, true)
        }
        return cachedRecentCandidatesEnabled!!
    }

    fun setRecentCandidatesEnabled(context: Context, enabled: Boolean) {
        cachedRecentCandidatesEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_RECENT_CANDIDATES_ENABLED, enabled).apply()
    }

    // Haptic feedback settings (default: true to enable)
    fun getHapticFeedbackEnabled(context: Context): Boolean {
        if (cachedHapticFeedbackEnabled == null) {
            cachedHapticFeedbackEnabled = getPrefs(context).getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        }
        return cachedHapticFeedbackEnabled!!
    }

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        cachedHapticFeedbackEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
    }

    // Default skin tone for emojis (0 = yellow/default, 1-5 = light to dark)
    fun getDefaultSkinTone(context: Context): Int {
        if (cachedDefaultSkinTone == -1) {
            cachedDefaultSkinTone = getPrefs(context).getInt(KEY_DEFAULT_SKIN_TONE, 0)
        }
        return cachedDefaultSkinTone
    }

    fun setDefaultSkinTone(context: Context, skinToneIndex: Int) {
        cachedDefaultSkinTone = skinToneIndex.coerceIn(0, 5)
        getPrefs(context).edit().putInt(KEY_DEFAULT_SKIN_TONE, cachedDefaultSkinTone).apply()
    }

    // Chinese conversion button (Simplified ⇄ Traditional). Default on.
    fun getChineseConvertEnabled(context: Context): Boolean {
        if (cachedChineseConvertEnabled == null) {
            cachedChineseConvertEnabled = getPrefs(context).getBoolean(KEY_CHINESE_CONVERT_ENABLED, true)
        }
        return cachedChineseConvertEnabled!!
    }

    fun setChineseConvertEnabled(context: Context, enabled: Boolean) {
        cachedChineseConvertEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_CHINESE_CONVERT_ENABLED, enabled).apply()
    }

    // Candidate pill horizontal padding (in dp). Smaller packs more candidates per row.
    fun getCandidatePillPadding(context: Context): Int {
        if (cachedCandidatePillPadding == -1) {
            cachedCandidatePillPadding = getPrefs(context)
                .getInt(KEY_CANDIDATE_PILL_PADDING, CANDIDATE_PADDING_DEFAULT)
                .coerceIn(CANDIDATE_PADDING_MIN, CANDIDATE_PADDING_MAX)
        }
        return cachedCandidatePillPadding
    }

    fun setCandidatePillPadding(context: Context, dp: Int) {
        cachedCandidatePillPadding = dp.coerceIn(CANDIDATE_PADDING_MIN, CANDIDATE_PADDING_MAX)
        getPrefs(context).edit().putInt(KEY_CANDIDATE_PILL_PADDING, cachedCandidatePillPadding).apply()
    }

    fun getKeyHeight(context: Context): Int {
        if (cachedKeyHeight == -1) {
            cachedKeyHeight = getPrefs(context).getInt(KEY_KEY_HEIGHT, KEY_HEIGHT_DEFAULT)
                .coerceIn(KEY_HEIGHT_MIN, KEY_HEIGHT_MAX)
        }
        return cachedKeyHeight
    }

    fun setKeyHeight(context: Context, dp: Int) {
        cachedKeyHeight = dp.coerceIn(KEY_HEIGHT_MIN, KEY_HEIGHT_MAX)
        getPrefs(context).edit().putInt(KEY_KEY_HEIGHT, cachedKeyHeight).apply()
    }

    fun getCandidateTextSize(context: Context): Int {
        if (cachedCandidateTextSize == -1) {
            cachedCandidateTextSize = getPrefs(context)
                .getInt(KEY_CANDIDATE_TEXT_SIZE, CANDIDATE_TEXT_DEFAULT)
                .coerceIn(CANDIDATE_TEXT_MIN, CANDIDATE_TEXT_MAX)
        }
        return cachedCandidateTextSize
    }

    fun setCandidateTextSize(context: Context, sp: Int) {
        cachedCandidateTextSize = sp.coerceIn(CANDIDATE_TEXT_MIN, CANDIDATE_TEXT_MAX)
        getPrefs(context).edit().putInt(KEY_CANDIDATE_TEXT_SIZE, cachedCandidateTextSize).apply()
    }

    fun getShowKeyRadicals(context: Context): Boolean {
        if (cachedShowKeyRadicals == null) {
            cachedShowKeyRadicals = getPrefs(context).getBoolean(KEY_SHOW_KEY_RADICALS, true)
        }
        return cachedShowKeyRadicals!!
    }

    fun setShowKeyRadicals(context: Context, show: Boolean) {
        cachedShowKeyRadicals = show
        getPrefs(context).edit().putBoolean(KEY_SHOW_KEY_RADICALS, show).apply()
    }

    fun getEnglishSpellCheck(context: Context): Boolean {
        if (cachedEnglishSpellCheck == null) {
            cachedEnglishSpellCheck = getPrefs(context).getBoolean(KEY_ENGLISH_SPELL_CHECK, true)
        }
        return cachedEnglishSpellCheck!!
    }

    fun setEnglishSpellCheck(context: Context, enabled: Boolean) {
        cachedEnglishSpellCheck = enabled
        getPrefs(context).edit().putBoolean(KEY_ENGLISH_SPELL_CHECK, enabled).apply()
    }

    fun getSpaceAfterEnglishCandidate(context: Context): Boolean {
        if (cachedSpaceAfterEnglishCandidate == null) {
            cachedSpaceAfterEnglishCandidate = getPrefs(context)
                .getBoolean(KEY_SPACE_AFTER_ENGLISH_CANDIDATE, true)
        }
        return cachedSpaceAfterEnglishCandidate!!
    }

    fun setSpaceAfterEnglishCandidate(context: Context, enabled: Boolean) {
        cachedSpaceAfterEnglishCandidate = enabled
        getPrefs(context).edit().putBoolean(KEY_SPACE_AFTER_ENGLISH_CANDIDATE, enabled).apply()
    }

    /**
     * Returns true if dark theme should be used based on current settings.
     */
    fun isDarkTheme(context: Context): Boolean {
        return when (getThemeMode(context)) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            THEME_AUTO -> {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }
    }

    /**
     * Get the current theme colors.
     */
    fun getColors(context: Context): KeyboardColors {
        return if (isDarkTheme(context)) darkColors else lightColors
    }

    /**
     * Alias for getColors() for consistency.
     */
    fun getKeyboardColors(context: Context): KeyboardColors = getColors(context)

    /**
     * Get the accent color for the current theme.
     */
    fun getAccentColor(context: Context): Int {
        return if (isDarkTheme(context)) darkColors.compositionText else lightColors.compositionText
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Invalidate all caches (call when settings might have changed externally).
     */
    fun invalidateCache() {
        cachedTheme = -1
        cachedShowComposition = null
        cachedCandidatesPerPage = -1
        cachedUseExtendedCharset = null
        cachedRecentCandidatesEnabled = null
        cachedHapticFeedbackEnabled = null
        cachedDefaultSkinTone = -1
        cachedChineseConvertEnabled = null
        cachedCandidatePillPadding = -1
        cachedKeyHeight = -1
        cachedCandidateTextSize = -1
        cachedShowKeyRadicals = null
        cachedEnglishSpellCheck = null
        cachedSpaceAfterEnglishCandidate = null
    }

    // Modern Dark Theme Colors (Material You inspired)
    private val darkColors = KeyboardColors(
        keyboardBackground = Color.parseColor("#1F1F1F"),
        keyBackground = Color.parseColor("#3A3A3A"),
        keyBackgroundPressed = Color.parseColor("#525252"),
        specialKeyBackground = Color.parseColor("#2A2A2A"),
        specialKeyBackgroundPressed = Color.parseColor("#424242"),
        spaceKeyBackground = Color.parseColor("#3A3A3A"),
        candidateBarBackground = Color.parseColor("#1F1F1F"),
        candidatePillBackground = Color.parseColor("#2A2A2A"),
        candidatePillBackgroundPressed = Color.parseColor("#424242"),
        keyTextPrimary = Color.parseColor("#E8E8E8"),
        keyTextSecondary = Color.parseColor("#9E9E9E"),
        candidateText = Color.parseColor("#E8E8E8"),
        compositionText = Color.parseColor("#8AB4F8"),  // Google Blue
        englishPillText = Color.parseColor("#8AB4F8"),
        pageIndicatorText = Color.parseColor("#9E9E9E"),
        noMatchText = Color.parseColor("#757575"),
        keyShadowColor = Color.parseColor("#0D000000"),
        emojiCategoryBackground = Color.parseColor("#2A2A2A"),
        emojiCategorySelectedBackground = Color.parseColor("#424242"),
        emojiCategoryText = Color.parseColor("#9E9E9E"),
        emojiCategorySelectedText = Color.parseColor("#E8E8E8"),
        // Clipboard colors
        clipboardItemBackground = Color.parseColor("#2A2A2A"),
        clipboardItemBackgroundPressed = Color.parseColor("#424242"),
        clipboardItemText = Color.parseColor("#E8E8E8"),
        clipboardPinnedIcon = Color.parseColor("#8AB4F8"),
        clipboardDeleteIcon = Color.parseColor("#9E9E9E"),
        clipboardEmptyText = Color.parseColor("#757575")
    )

    // Modern Light Theme Colors (Material You inspired)
    private val lightColors = KeyboardColors(
        keyboardBackground = Color.parseColor("#E8EAF0"),
        keyBackground = Color.parseColor("#FFFFFF"),
        keyBackgroundPressed = Color.parseColor("#D0D0D0"),
        specialKeyBackground = Color.parseColor("#D4D8E0"),
        specialKeyBackgroundPressed = Color.parseColor("#B8BCC4"),
        spaceKeyBackground = Color.parseColor("#FFFFFF"),
        candidateBarBackground = Color.parseColor("#E8EAF0"),
        candidatePillBackground = Color.parseColor("#FFFFFF"),
        candidatePillBackgroundPressed = Color.parseColor("#D0D0D0"),
        keyTextPrimary = Color.parseColor("#1F1F1F"),
        keyTextSecondary = Color.parseColor("#5F6368"),
        candidateText = Color.parseColor("#1F1F1F"),
        compositionText = Color.parseColor("#1A73E8"),  // Google Blue
        englishPillText = Color.parseColor("#1A73E8"),
        pageIndicatorText = Color.parseColor("#5F6368"),
        noMatchText = Color.parseColor("#9E9E9E"),
        keyShadowColor = Color.parseColor("#1A000000"),
        emojiCategoryBackground = Color.parseColor("#FFFFFF"),
        emojiCategorySelectedBackground = Color.parseColor("#D4D8E0"),
        emojiCategoryText = Color.parseColor("#5F6368"),
        emojiCategorySelectedText = Color.parseColor("#1F1F1F"),
        // Clipboard colors
        clipboardItemBackground = Color.parseColor("#FFFFFF"),
        clipboardItemBackgroundPressed = Color.parseColor("#D0D0D0"),
        clipboardItemText = Color.parseColor("#1F1F1F"),
        clipboardPinnedIcon = Color.parseColor("#1A73E8"),
        clipboardDeleteIcon = Color.parseColor("#5F6368"),
        clipboardEmptyText = Color.parseColor("#9E9E9E")
    )
}
