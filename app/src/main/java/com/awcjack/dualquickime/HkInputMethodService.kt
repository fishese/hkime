package com.awcjack.dualquickime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.awcjack.dualquickime.data.EditorAnchor
import com.awcjack.dualquickime.convert.ChineseConverter
import com.awcjack.dualquickime.data.AssociatedPhrasesParser
import com.awcjack.dualquickime.data.MckRelatedPhrases
import com.awcjack.dualquickime.data.EnglishSuggestions
import com.awcjack.dualquickime.data.EnglishAutocomplete
import com.awcjack.dualquickime.data.EnglishTypoMatcher
import com.awcjack.dualquickime.data.UnicodeWordSuggestions
import com.awcjack.dualquickime.data.SymbolCatalogue
import com.awcjack.dualquickime.data.PendingSymbol
import com.awcjack.dualquickime.data.AssociatedPhrasesTable
import com.awcjack.dualquickime.data.AssociatedPhraseSuggestions
import com.awcjack.dualquickime.data.CuratedAssociatedPhrases
import com.awcjack.dualquickime.data.CinParser
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.ClipboardCapture
import com.awcjack.dualquickime.data.CursorMotion
import com.awcjack.dualquickime.data.SwipeDeletion
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.data.CompositionSelection
import com.awcjack.dualquickime.data.LatinSentenceCase
import com.awcjack.dualquickime.data.LatinSpaceCommit
import com.awcjack.dualquickime.data.EmailDomains
import com.awcjack.dualquickime.data.ContextualPunctuation
import com.awcjack.dualquickime.data.PunctuationSpacing
import com.awcjack.dualquickime.data.CustomDictionaryManager
import com.awcjack.dualquickime.data.sanitizeCandidates
import com.awcjack.dualquickime.data.prioritizeCustomCandidates
import com.awcjack.dualquickime.data.promoteReviewedCharacter
import com.awcjack.dualquickime.data.mergeTypoCandidates
import com.awcjack.dualquickime.data.typedCandidates
import com.awcjack.dualquickime.data.promoteEverydayCantonese
import com.awcjack.dualquickime.data.MixedDictionary
import com.awcjack.dualquickime.data.NumericPadSpec
import com.awcjack.dualquickime.data.MethodMembership
import com.awcjack.dualquickime.data.RecentCandidateManager
import com.awcjack.dualquickime.data.LearnedPhraseManager
import com.awcjack.dualquickime.data.LearnedPhraseModel
import com.awcjack.dualquickime.data.PhraseLearningPolicy
import com.awcjack.dualquickime.data.SimplexTable
import com.awcjack.dualquickime.data.ShortcutPhraseManager
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import java.util.Locale
import android.os.Handler
import android.os.Looper
import com.awcjack.dualquickime.data.CandidateWorker
import com.awcjack.dualquickime.data.rankCandidates
import java.util.concurrent.atomic.AtomicLong

/**
 * Quick (速成) Input Method Service for Android.
 *
 * Supports dual-mode Chinese/English input:
 * - TAP on candidate pill to commit Chinese character
 * - SPACE to navigate candidate pages
 * - Full Cantonese/Cangjie/Quick codes remain in one composition buffer
 * - English stays available as a candidate; ENTER commits it before the editor action
 * - Numbers commit composition as English first, then the number
 *
 * Uses embedded Gboard-style candidate bar (not system candidates view).
 */
class HkInputMethodService : InputMethodService() {

    private val candidateWorker = CandidateWorker()
    private val candidateMain = Handler(Looper.getMainLooper())
    private val candidateRevision = AtomicLong()
    private var resourcesReady = false
    private var destroyed = false
    private var lookupPending = false
    private val deferredSwipeEvents = java.util.ArrayDeque<KeyboardView.KeyEvent>()
    private var pendingClipboardEvent = false
    private val clipboardStorageListener: () -> Unit = {
        if (pendingClipboardEvent && ClipboardHistoryManager.hasResolvedEnabledSetting(this)) {
            pendingClipboardEvent = false
            handleSystemClipboardChange()
        }
    }
    private var associatedAnchor: EditorAnchor? = null
    private var simplexTable = SimplexTable(emptyList())
    private lateinit var mixedDictionary: MixedDictionary
    private var methodMembership = MethodMembership(emptySequence())
    private var englishAutocomplete = EnglishAutocomplete.EMPTY
    private var englishTypoMatcher = EnglishTypoMatcher(emptyList(), emptySet())
    private var associatedPhrasesTable = AssociatedPhrasesTable.EMPTY
    private var curatedAssociatedPhrases = CuratedAssociatedPhrases.EMPTY
    private lateinit var mckRelatedPhrases: MckRelatedPhrases
    private var composition = CompositionState.EMPTY
    private var swipeEnglishWords: List<String> = emptyList()
    private var swipeChineseCodes: List<String> = emptyList()
    private var pendingSwipeChoice = false
    private var deferredSpaceAnchor: EditorAnchor? = null
    private var pendingLatinSpace = false
    private var pendingPunctuationSpace = false
    private var pendingPunctuationSpaceAnchor: EditorAnchor? = null
    private var suppressedPunctuationSpaceAnchor: EditorAnchor? = null
    private var spaceDeferredUntilNextLatin = false
        set(value) {
            field = value
            if (!value) pendingLatinSpace = false
            deferredSpaceAnchor = if (value) captureEditorAnchor() else null
        }
    private var lastSelectedText = ""
    private var pendingSymbol: PendingSymbol? = null

    private var keyboardView: KeyboardView? = null
    private var spaceCursorInputConnection: android.view.inputmethod.InputConnection? = null

    // Associated phrases mode: when true, candidate bar shows associated phrases
    private var isAssociatedPhrasesMode = false
    private var associatedPhrases = listOf<String>()
    private var learnedAssociatedCandidates = emptySet<String>()
    private var learnedPhraseContext = ""
    private var learnedPhraseAnchor: EditorAnchor? = null
    private var lastCommittedChar = ""

    // Track current keyboard mode
    private var isSymbolMode = false

    // Track original case of letters for English output
    // Maps index position to whether it was uppercase
    private var letterCases = mutableListOf<Boolean>()

    // Email domain suggestion mode: triggered when @ is typed
    private var isEmailSuggestionsMode = false
    private var emailTypedSoFar = ""
    private var emailAnchor: EditorAnchor? = null

    // Whether the currently focused field is a password field
    private var isPasswordField = false
    private var isEmailField = false
    private var isUsernameField = false
    // Whether password masking is active (user can toggle with the eye button)
    private var isPasswordMaskEnabled = true

    // Track which character set is currently loaded
    private var currentCharsetExtended: Boolean? = null

    // System clipboard manager and listener
    private var clipboardManager: ClipboardManager? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleSystemClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        mixedDictionary = MixedDictionary(assets)
        mckRelatedPhrases = MckRelatedPhrases(assets)
        currentCharsetExtended = ThemeManager.getUseExtendedCharset(this)
        val filename = ThemeManager.getSimplexFilename(this)
        candidateWorker.executor.execute {
            simplexTable = readSimplexTable(filename)
            methodMembership = runCatching {
                assets.open("method-membership.tsv").bufferedReader().use { membership ->
                    assets.open("method-phrase-overrides.tsv").bufferedReader().use { overrides ->
                        MethodMembership(membership.lineSequence(), overrides.lineSequence())
                    }
                }
            }.getOrElse { MethodMembership(emptySequence()) }
            englishAutocomplete = runCatching { EnglishAutocomplete.parse(assets.open("english-autocomplete.txt")) }
                .getOrDefault(EnglishAutocomplete.EMPTY)
            englishTypoMatcher = EnglishTypoMatcher(englishAutocomplete.allWords(), EnglishSuggestions.typoWords())
            loadAssociatedPhrasesTable()
            candidateMain.post {
                if (!destroyed) {
                    resourcesReady = true
                    keyboardView?.setSwipeWords(englishAutocomplete.allWords())
                    refreshSwipeVocabulary()
                }
            }
        }

        // Register clipboard listener to capture system clipboard changes
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        ClipboardHistoryManager.addListener(clipboardStorageListener)
        ClipboardHistoryManager.isEnabled(this) // Start settings migration before the first copy when possible.
    }

    /**
     * Load the simplex table based on user's character set preference.
     */
    private fun readSimplexTable(filename: String): SimplexTable = runCatching {
        CinParser().parse(assets.open(filename))
    }.getOrElse { SimplexTable(emptyList()) }

    private fun loadSimplexTable() {
        currentCharsetExtended = ThemeManager.getUseExtendedCharset(this)
        val filename = ThemeManager.getSimplexFilename(this)
        candidateWorker.executor.execute { simplexTable = readSimplexTable(filename) }
    }

    private fun refreshSwipeVocabulary() {
        if (!resourcesReady || !ThemeManager.getSwipeTyping(this)) {
            keyboardView?.setSwipeCodes(emptySet())
            return
        }
        val methods = enabledMethods()
        val view = keyboardView
        candidateWorker.executor.execute {
            val codes = methodMembership.swipeCodes(methods)
            candidateMain.post {
                if (!destroyed && view === keyboardView && methods == enabledMethods() && ThemeManager.getSwipeTyping(this))
                    view?.setSwipeCodes(codes)
            }
        }
    }

    /**
     * Reload the simplex table (call when character set setting changes).
     */
    fun reloadSimplexTable() {
        loadSimplexTable()
        // Clear current composition since character mappings may have changed
        clearComposition()
    }

    /**
     * Load the associated phrases table from assets.
     */
    private fun loadAssociatedPhrasesTable() {
        try {
            associatedPhrasesTable = AssociatedPhrasesParser().parse(assets.open("associated-phrases.cin"))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            associatedPhrasesTable = AssociatedPhrasesTable.EMPTY
        }
        curatedAssociatedPhrases = try {
            CuratedAssociatedPhrases.parse(assets.open("curated-associated-phrases.tsv"))
        } catch (e: Exception) {
            CuratedAssociatedPhrases.EMPTY
        }
    }

    /**
     * Handle system clipboard changes (text copied from other apps).
     */
    private fun handleSystemClipboardChange() {
        if (!ClipboardHistoryManager.isEnabled(this)) {
            val unknown = !ClipboardHistoryManager.hasResolvedEnabledSetting(this)
            val sensitive = clipboardManager?.primaryClipDescription?.extras
                ?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            pendingClipboardEvent = unknown && !sensitive &&
                !(ClipboardHistoryManager.isSkipPasswordFieldsEnabled(this) &&
                    ClipboardHistoryManager.isPasswordField(currentInputEditorInfo))
            return
        }
        pendingClipboardEvent = false

        runCatching {
            val clip = clipboardManager?.primaryClip ?: return
            val text = ClipboardCapture.text(clip) ?: return
            ClipboardHistoryManager.addItem(this, text, currentInputEditorInfo)
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).apply {
            setSwipeWords(if (resourcesReady) englishAutocomplete.allWords() else emptyList())
            setSwipeCodes(emptySet())
            setOnKeyPressListener { event ->
                handleKeyEvent(event)
            }
            setOnModeChangeListener { symbolMode ->
                isSymbolMode = symbolMode
            }
            setOnCandidateSelectedListener { candidate ->
                if (pendingSymbol != null) {
                    handleSymbolCandidateSelected(candidate)
                } else if (isEmailSuggestionsMode) {
                    handleEmailSuggestionSelected(candidate)
                } else if (isAssociatedPhrasesMode) {
                    // User TAPPED an associated phrase - commit it
                    handleAssociatedPhraseSelected(candidate)
                } else {
                    // The typed Latin code is already visible as composing text.
                    // Committing the candidate replaces that span in the editor.
                    commitCandidate(candidate)
                }
            }
            setOnEnglishSelectedListener { _ ->
                // Switching keyboard pages accepts the assumed swipe choice,
                // or the visible Latin text when that choice is not pending.
                finishLatinOrDropDeferredSpace()
            }
            setOnPageIndicatorClickedListener {
                handlePageIndicatorClicked()
            }
            setOnCandidateRefreshRequestedListener {
                // Refresh candidate view when returning from symbol/emoji/clipboard/grid mode
                invalidateEditorAnchors()
                updateCandidateView()
            }
            setOnMaskToggleListener {
                isPasswordMaskEnabled = !isPasswordMaskEnabled
                updateCandidateView()
            }
        }
        refreshSwipeVocabulary()
        return keyboardView!!
    }

    /**
     * Never use fullscreen mode so keyboard is always visible properly.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // The framework default hides the soft keyboard when it thinks a hardware
    // keyboard is attached (docks, Bluetooth keyboards, some OEMs that report one
    // spuriously). Always show our IME — if the user invoked us they want to type.
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        invalidateCandidateWork()
        deferredSwipeEvents.clear()
        pendingClipboardEvent = false
        super.onStartInput(info, restarting)
        isPasswordField = isPasswordInputField(info)
        isEmailField = isTextVariation(info, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        isUsernameField = isUsernameInputField(info)
        pendingSymbol = null
        spaceCursorInputConnection = null
        clearPendingPunctuationSpaceState()
        suppressedPunctuationSpaceAnchor = null
        spaceDeferredUntilNextLatin = false
        clearComposition()
        clearEmailSuggestions()
        resetLearnedPhraseContext()
        clearAssociatedPhrases()
    }

    override fun onFinishInput() {
        invalidateCandidateWork()
        deferredSwipeEvents.clear()
        LearnedPhraseManager.flush()
        resetLearnedPhraseContext()
        clearAssociatedPhrases()
        super.onFinishInput()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        invalidateCandidateWork()
        deferredSwipeEvents.clear()
        super.onStartInputView(info, restarting)
        resetLearnedPhraseContext()
        // Invalidate caches to pick up any settings changes
        ThemeManager.invalidateCache()
        refreshSwipeVocabulary()

        // Check if character set setting changed - reload if needed
        val useExtended = ThemeManager.getUseExtendedCharset(this)
        if (currentCharsetExtended != useExtended) {
            currentCharsetExtended = useExtended
            loadSimplexTable()
        }

        isPasswordField = isPasswordInputField(info)
        keyboardView?.setSensitiveField(isPasswordField)
        isEmailField = isTextVariation(info,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        isUsernameField = isUsernameInputField(info)
        isPasswordMaskEnabled = true
        // Refresh theme in case it changed in settings
        keyboardView?.refreshTheme()
        // Clear composition when starting new input
        pendingSymbol = null
        clearPendingPunctuationSpaceState()
        suppressedPunctuationSpaceAnchor = null
        lastSelectedText = ""
        spaceDeferredUntilNextLatin = false
        clearComposition()
        // Clear associated phrases mode
        clearAssociatedPhrases()
        clearEmailSuggestions()
        // Caps lock from a previous field shouldn't leak into this one — the
        // shift indicator was already redrawn by buildKeyboard via refreshTheme,
        // but the underlying state needs to be cleared explicitly.
        keyboardView?.resetShiftState()
        // Numeric editors have their own large, input-type-aware keypad.
        val numericSpec = info?.inputType?.let(NumericPadSpec::fromInputType)
        if (numericSpec != null) {
            isSymbolMode = false
            keyboardView?.setNumberPadMode(numericSpec)
        } else {
            isSymbolMode = false
            keyboardView?.setLetterMode()
        }
        refreshLatinCase()
    }

    private fun isPasswordInputField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun isTextVariation(info: EditorInfo?, vararg variations: Int): Boolean {
        val type = info?.inputType ?: return false
        return (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (type and InputType.TYPE_MASK_VARIATION) in variations
    }

    /** Android has no dedicated username inputType, so use the editor's hint/label when provided. */
    private fun isUsernameInputField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        if ((type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false

        val text = listOfNotNull(info.hintText?.toString(), info.label?.toString())
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val normalized = text.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        val commonHints = listOf(
            "username", "user name", "user id", "login", "account name", "account id",
            "用戶名", "用戶名稱", "登入名", "登入名稱", "帳戶名", "帳戶名稱", "帳號"
        )
        return commonHints.any(normalized::contains)
    }

    private fun isPunctuationSpacingDisabledField(): Boolean =
        isPasswordField || isEmailField || isUsernameField

    private fun handleKeyEvent(event: KeyboardView.KeyEvent) {
        // A following key must act on the resolved swipe choice, independent of
        // whether dictionary work happened to finish before that key arrived.
        if (pendingSwipeChoice && lookupPending && event !is KeyboardView.KeyEvent.MoveCursor &&
            event !is KeyboardView.KeyEvent.StartCursorDrag && event !is KeyboardView.KeyEvent.EndCursorDrag) {
            deferredSwipeEvents.addLast(event)
            return
        }
        if (event is KeyboardView.KeyEvent.MoveCursor) {
            val connection = spaceCursorInputConnection ?: return
            if (currentInputConnection !== connection) return
            moveCursorBy(event.delta, event.vertical, connection)
            return
        }
        when (event) {
            KeyboardView.KeyEvent.StartCursorDrag -> {
                spaceCursorInputConnection = currentInputConnection
                return
            }
            KeyboardView.KeyEvent.EndCursorDrag -> {
                spaceCursorInputConnection = null
                return
            }
            else -> Unit
        }
        invalidateEditorAnchors()
        if (pendingSymbol != null && event !is KeyboardView.KeyEvent.Symbol) {
            pendingSymbol = null
            keyboardView?.clearCandidates()
        }
        when (event) {
            is KeyboardView.KeyEvent.Letter -> handleLetter(event.char)
            is KeyboardView.KeyEvent.SwipeCode -> handleSwipeCode(event)
            KeyboardView.KeyEvent.SwipeDelete -> handleSwipeDelete()
            is KeyboardView.KeyEvent.MoveCursor -> Unit
            is KeyboardView.KeyEvent.Number -> handleNumber(event.digit, event.offerSymbolCandidates)
            is KeyboardView.KeyEvent.ShortcutPhrase -> handleShortcutPhrase(event.digit)
            is KeyboardView.KeyEvent.Symbol -> handleSymbol(event)
            is KeyboardView.KeyEvent.Emoji -> handleEmoji(event.emoji)
            is KeyboardView.KeyEvent.ClipboardPaste -> handleClipboardPaste(event.text)
            is KeyboardView.KeyEvent.CalculatorInsert -> if (!isPasswordField) {
                finishLatinOrDropDeferredSpace()
                commitText(event.text)
            }
            KeyboardView.KeyEvent.Space -> handleSpace()
            KeyboardView.KeyEvent.StartCursorDrag, KeyboardView.KeyEvent.EndCursorDrag -> Unit
            KeyboardView.KeyEvent.Backspace -> handleBackspace()
            KeyboardView.KeyEvent.Enter -> handleEnter()
            KeyboardView.KeyEvent.HideKeyboard -> {
                finishLatinOrDropDeferredSpace()
                pendingSymbol = null
                clearAssociatedPhrases()
                clearEmailSuggestions()
                requestHideSelf(0)
            }
            KeyboardView.KeyEvent.OpenSettings -> {
                finishLatinOrDropDeferredSpace()
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            is KeyboardView.KeyEvent.ConvertChinese -> handleConvertChinese(event.direction)
        }
        refreshLatinCase()
    }

    private fun refreshLatinCase() {
        val view = keyboardView ?: return
        val enabled = ThemeManager.getLatinSentenceCase(this) &&
            !isPasswordField && !isEmailField && !isUsernameField
        val before = if (enabled && composition.rawKeys.isEmpty()) {
            currentInputConnection?.getTextBeforeCursor(160, 0)?.toString()
        } else {
            null
        }
        view.setLatinCase(
            enabled,
            enabled && before != null &&
                LatinSentenceCase.contextCapital(before, composition.rawKeys.length)
        )
    }

    /**
     * Convert Chinese text between Simplified and Traditional using OpenCC.
     *
     * If the user has a selection, convert the selection. Otherwise fall
     * back to the last sentence before the cursor so the user doesn't have
     * to select first — handy for fixing a sentence they just typed.
     */
    private fun handleConvertChinese(direction: KeyboardView.KeyEvent.ConvertDirection) {
        resetLearnedPhraseContext()
        if (!ChineseConverter.isAvailable()) return

        // Commit any pending composition first so it isn't lost.
        finishLatinOrDropDeferredSpace()
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        if (isEmailSuggestionsMode) clearEmailSuggestions()

        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            val converted = convertWithDirection(selected, direction)
            if (converted != selected) ic.commitText(converted, 1)
            return
        }

        // No selection — grab the last sentence before the cursor.
        // 1024 chars covers the vast majority of short-form writing without
        // pulling unreasonable amounts of surrounding text.
        val before = ic.getTextBeforeCursor(1024, 0)?.toString() ?: return
        val sentence = extractLastSentence(before)
        if (sentence.isEmpty()) return

        val converted = convertWithDirection(sentence, direction)
        if (converted == sentence) return

        ic.beginBatchEdit()
        ic.deleteSurroundingText(sentence.length, 0)
        ic.commitText(converted, 1)
        ic.endBatchEdit()
    }

    private fun convertWithDirection(
        text: String,
        direction: KeyboardView.KeyEvent.ConvertDirection
    ): String = when (direction) {
        KeyboardView.KeyEvent.ConvertDirection.TO_SIMPLIFIED -> ChineseConverter.toSimplified(text)
        KeyboardView.KeyEvent.ConvertDirection.TO_TRADITIONAL -> ChineseConverter.toTraditional(text)
        KeyboardView.KeyEvent.ConvertDirection.AUTO -> ChineseConverter.convertAuto(text)
    }

    /**
     * Return the last sentence in [text] — everything after the last
     * sentence-ending delimiter, trimmed of any trailing delimiters /
     * whitespace so that a final "。" doesn't collapse the result to empty.
     * Falls back to the full string when no delimiter is present.
     */
    private fun extractLastSentence(text: String): String {
        val delimiters = setOf('.', '。', '!', '！', '?', '？', '\n', ';', '；', '…')
        var end = text.length
        while (end > 0 && (text[end - 1] in delimiters || text[end - 1].isWhitespace())) {
            end--
        }
        if (end == 0) return ""
        var start = end - 1
        while (start >= 0 && text[start] !in delimiters) {
            start--
        }
        return text.substring(start + 1)
    }

    private fun handleLetter(char: Char) {
        // A new letter accepts the underlined swipe choice, then starts fresh.
        confirmPendingSwipeChoice()
        if (composition.rawKeys.isEmpty() && !anchorMatches(learnedPhraseAnchor)) resetLearnedPhraseContext()
        swipeEnglishWords = emptyList()
        swipeChineseCodes = emptyList()
        if (isEmailSuggestionsMode) {
            if (LatinSpaceCommit.restoreDeferredSpace(
                    restoresSpaceBetweenLatin(), spaceDeferredUntilNextLatin, true)) {
                commitText(" ")
            }
            spaceDeferredUntilNextLatin = false
            val lowerChar = char.lowercaseChar()
            commitText(lowerChar.toString())
            emailTypedSoFar += lowerChar
            emailAnchor = captureEditorAnchor()
            updateEmailSuggestionsView()
            return
        }
        // Exit associated phrases mode when typing
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        if (composition.rawKeys.isEmpty()) insertSpaceAfterHalfPunctuation(char.toString())

        val isUpperCase = char.isUpperCase()
        val lowerChar = char.lowercaseChar()
        val newRawKeys = composition.rawKeys + lowerChar

        // Keep the Latin text visible in the app while Chinese options are open.
        letterCases.add(isUpperCase)
        pendingLatinSpace = false
        currentInputConnection?.setComposingText(deferredLatinPrefix() + getDisplayKeys(newRawKeys), 1)
        updateComposition(newRawKeys)
    }

    private fun handleSwipeCode(event: KeyboardView.KeyEvent.SwipeCode) {
        if (isPasswordField || event.code.isEmpty()) return
        confirmPendingSwipeChoice()
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        if (!pendingLatinSpace) finishEnglishComposition()
        insertSpaceAfterHalfPunctuation(event.code)
        swipeEnglishWords = event.englishWords.filterNot { it == event.code }
        swipeChineseCodes = event.chineseCodes.filterNot { it == event.code }
        letterCases.clear()
        repeat(event.code.length) { letterCases.add(false) }
        updateComposition(event.code)
        val display = event.code
        composition = composition.copy(candidates = listOf(display))
        pendingLatinSpace = false
        currentInputConnection?.setComposingText(
            (if (LatinSpaceCommit.keptAsLatin(display)) deferredLatinPrefix() else "") + display, 1)
        pendingSwipeChoice = true
    }

    private fun moveCursorBy(
        delta: Int,
        vertical: Boolean,
        connection: android.view.inputmethod.InputConnection
    ) {
        if (delta == 0) return
        if (currentInputConnection !== connection) return
        deferredSwipeEvents.clear()
        resetLearnedPhraseContext()
        pendingSwipeChoice = false
        suppressedPunctuationSpaceAnchor = null
        finishPendingPunctuationSpace()
        finishEnglishComposition()
        spaceDeferredUntilNextLatin = false
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        if (vertical) {
            val inputType = currentInputEditorInfo?.inputType ?: return
            val isMultilineText = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (!isMultilineText) return

            // If text is selected, collapse it toward the direction of travel first.
            val selection = connection.getExtractedText(ExtractedTextRequest(), 0)
                ?.takeIf { it.partialStartOffset < 0 && it.text != null &&
                    it.startOffset >= 0 && it.selectionStart >= 0 && it.selectionEnd >= 0 }
            if (!connection.getSelectedText(0).isNullOrEmpty()) {
                if (selection == null) return
                val localOffset = if (delta < 0) minOf(selection.selectionStart, selection.selectionEnd)
                    else maxOf(selection.selectionStart, selection.selectionEnd)
                if (localOffset !in 0..(selection.text?.length ?: 0)) return
                val absoluteOffset = selection.startOffset + localOffset
                connection.setSelection(absoluteOffset, absoluteOffset)
            }

            val key = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_UP
                else android.view.KeyEvent.KEYCODE_DPAD_DOWN
            for (step in 0 until kotlin.math.abs(delta)) {
                // Never send a navigation key at the input's document boundary,
                // where an editor could pass focus to another part of the page.
                val textBefore = connection.getTextBeforeCursor(1, 0)?.toString()
                val textAfter = connection.getTextAfterCursor(1, 0)?.toString()
                if (!CursorMotion.canMoveVertically(delta, isMultilineText, textBefore, textAfter)) break
                if (!sendCursorNavigationKey(connection, key)) break
            }
            return
        }

        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val next = extracted?.takeIf { it.partialStartOffset < 0 && it.text != null }?.let {
            CursorMotion.selectionInExcerpt(it.text?.toString().orEmpty(), it.startOffset,
                it.selectionStart, it.selectionEnd, delta)
        }
        if (next != null) {
            connection.setSelection(next, next)
        } else {
            // Use native horizontal navigation for editors without full offsets,
            // while guarding both document edges against focus leaving the field.
            val key = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_LEFT
                else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            for (step in 0 until kotlin.math.abs(delta)) {
                val adjacentText = if (delta < 0) connection.getTextBeforeCursor(1, 0)?.toString()
                    else connection.getTextAfterCursor(1, 0)?.toString()
                if (adjacentText.isNullOrEmpty()) break
                if (!sendCursorNavigationKey(connection, key)) break
            }
        }
    }

    /** Send drag navigation only to the editor connection that began this move. */
    private fun sendCursorNavigationKey(
        connection: android.view.inputmethod.InputConnection,
        keyCode: Int
    ): Boolean {
        if (currentInputConnection !== connection) return false
        val downTime = android.os.SystemClock.uptimeMillis()
        val flags = android.view.KeyEvent.FLAG_SOFT_KEYBOARD or
            android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        fun keyEvent(action: Int, eventTime: Long) = android.view.KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            0,
            0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            flags,
            android.view.InputDevice.SOURCE_KEYBOARD
        )

        val downHandled = connection.sendKeyEvent(
            keyEvent(android.view.KeyEvent.ACTION_DOWN, downTime)
        )
        connection.sendKeyEvent(
            keyEvent(android.view.KeyEvent.ACTION_UP, android.os.SystemClock.uptimeMillis())
        )
        return downHandled && currentInputConnection === connection
    }

    private fun handleSwipeDelete() {
        resetLearnedPhraseContext()
        // A delete gesture drops the assumed choice instead of accepting it.
        pendingSwipeChoice = false
        if (isPasswordField) return
        if (pendingPunctuationSpace) {
            cancelPendingPunctuationSpace()
            suppressedPunctuationSpaceAnchor = captureEditorAnchor()
            return
        }
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        if (composition.rawKeys.isNotEmpty()) {
            currentInputConnection?.setComposingText("", 1)
            currentInputConnection?.finishComposingText()
            clearComposition()
            return
        }
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            spaceDeferredUntilNextLatin = false
            ic.commitText("", 1)
            lastSelectedText = ""
            return
        }
        val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        val length = SwipeDeletion.lengthBeforeCursor(before, lastSelectedText)
        lastSelectedText = ""
        spaceDeferredUntilNextLatin = false
        if (length > 0) ic.deleteSurroundingText(length, 0) else deleteOneGrapheme()
    }

    private fun handleNumber(digit: Int, offerSymbolCandidates: Boolean = false) {
        if (isEmailSuggestionsMode) {
            finishPendingPunctuationSpace()
            val digitStr = digit.toString()
            commitText(digitStr)
            emailTypedSoFar += digitStr
            emailAnchor = captureEditorAnchor()
            updateEmailSuggestionsView()
            return
        }
        finishPendingPunctuationSpace()
        if (pendingLatinSpace) {
            resolvePendingLatinSpace(keep = true)
            spaceDeferredUntilNextLatin = false
        }
        val committingRawLatin = composition.rawKeys.isNotEmpty()
        finishEnglishComposition()
        // A held space returns once before the number. Later digits stay attached,
        // so 20 is not written as 2 0. A space between numbers still requires a space press.
        if (!committingRawLatin && LatinSpaceCommit.restoreDeferredSpace(
                restoresSpaceBetweenLatin(), spaceDeferredUntilNextLatin,
                nextCommitIsLatin = false, nextCommitIsNumber = true)) {
            commitText(" ")
        }
        spaceDeferredUntilNextLatin = false
        val text = digit.toString()
        commitText(text)
        if (isSymbolMode || offerSymbolCandidates) {
            // Use the whole contiguous number, so 12 can offer Ⅻ while 13
            // does not incorrectly fall back to the alternatives for 3.
            val number = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString()
                ?.takeLastWhile { it in '0'..'9' }.orEmpty().ifEmpty { text }
            showSymbolCandidates(number, SymbolCatalogue.candidatesForSymbol(number))
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd)
        if (pendingPunctuationSpace && CompositionSelection.movedOutside(
                newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            finishPendingPunctuationSpace()
        }
        if (composition.rawKeys.isNotEmpty() && CompositionSelection.movedOutside(
                newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            deferredSwipeEvents.clear()
            resetLearnedPhraseContext()
            // Finish in place; navigation must not insert a held space or move the caret back.
            spaceDeferredUntilNextLatin = false
            currentInputConnection?.finishComposingText()
            clearComposition()
        }
        invalidateEditorAnchors()
        refreshLatinCase()
    }

    private fun editorCursor(): Int? {
        val extracted = currentInputConnection?.getExtractedText(ExtractedTextRequest(), 0)
            ?: return null
        if (extracted.startOffset < 0 || extracted.selectionStart < 0 ||
            extracted.selectionStart != extracted.selectionEnd) return null
        return extracted.startOffset + extracted.selectionEnd
    }

    private fun captureEditorAnchor(): EditorAnchor? {
        val connection = currentInputConnection ?: return null
        if (!connection.getSelectedText(0).isNullOrEmpty()) return null
        val prefix = connection.getTextBeforeCursor(64, 0)?.toString() ?: return null
        return EditorAnchor(editorCursor(), prefix)
    }

    private fun anchorMatches(anchor: EditorAnchor?): Boolean {
        val connection = currentInputConnection ?: return false
        return anchor?.matches(editorCursor(), connection.getSelectedText(0)?.toString(),
            connection.getTextBeforeCursor(64, 0)?.toString()) == true
    }

    private fun invalidateEditorAnchors() {
        if (isAssociatedPhrasesMode && associatedAnchor != null && !anchorMatches(associatedAnchor)) clearAssociatedPhrases()
        if (learnedPhraseAnchor != null && composition.rawKeys.isEmpty() && !anchorMatches(learnedPhraseAnchor)) {
            resetLearnedPhraseContext()
            if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        }
        // Read current editor state instead of trusting delayed selection callbacks from our own edits.
        if (pendingPunctuationSpace && !anchorMatches(pendingPunctuationSpaceAnchor)) {
            finishPendingPunctuationSpace()
        }
        if (composition.rawKeys.isEmpty() && spaceDeferredUntilNextLatin &&
            !anchorMatches(deferredSpaceAnchor)) {
            resolvePendingLatinSpace(keep = true)
            spaceDeferredUntilNextLatin = false
        }
        if (suppressedPunctuationSpaceAnchor != null &&
            !suppressedPunctuationSpaceStillApplies(suppressedPunctuationSpaceAnchor)) {
            suppressedPunctuationSpaceAnchor = null
        }
        if (isEmailSuggestionsMode && !anchorMatches(emailAnchor)) clearEmailSuggestions()
    }

    private fun handleShortcutPhrase(digit: Int) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        finishLatinOrDropDeferredSpace()
        val phrase = ShortcutPhraseManager.get(this, digit)
        val text = phrase.ifEmpty { digit.toString() }
        insertSpaceAfterHalfPunctuation(text)
        commitText(text)
    }

    private fun handleSymbol(event: KeyboardView.KeyEvent.Symbol) {
        // A new symbol starts a new punctuation context; a prior Backspace choice
        // should not suppress spacing after this symbol.
        suppressedPunctuationSpaceAnchor = null
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        pendingSymbol = null
        val separatingLatin = spaceDeferredUntilNextLatin && restoresSpaceBetweenLatin()
        finishLatinOrDropDeferredSpace()
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val choice = ContextualPunctuation.choose(event.char, beforeCursor, event.forceLiteral)
        val inserted = (choice?.inserted ?: event.char).toString()
        val catalogue = SymbolCatalogue.candidatesForSymbol(inserted)
        val alternatives = (listOfNotNull(choice?.alternative?.toString()) + catalogue)
            .filterNot { it == inserted }
            .distinct()
            .let { rest ->
                // Script pickers lead with their own glyph. Keep that first even
                // though the key has already committed it.
                if (catalogue.firstOrNull() == inserted) listOf(inserted) + rest else rest
            }
        if (separatingLatin && LatinSpaceCommit.isOpeningPunctuation(beforeCursor, inserted)) commitText(" ")
        else insertSpaceAfterHalfPunctuation(inserted)
        commitText(inserted)
        if (event.char == '@' && !isPasswordField &&
            EmailDomains.shouldOffer(isEmailField, beforeCursor)) {
            // Domain suggestions own the candidate bar after @; do not leave a
            // pending symbol that would intercept domain selection.
            enterEmailSuggestionsMode()
        } else if (keyboardView?.isNumberPadMode() != true) {
            showSymbolCandidates(inserted, alternatives)
            startPendingPunctuationSpace()
        }
    }

    private fun showSymbolCandidates(inserted: String, alternatives: List<String>) {
        keyboardView?.clearCandidates()
        if (alternatives.isEmpty() || isPasswordField) return
        pendingSymbol = PendingSymbol(inserted, alternatives)
        keyboardView?.setCandidates(alternatives)
    }

    private fun handleSymbolCandidateSelected(candidate: String) {
        val pending = pendingSymbol ?: return
        cancelPendingPunctuationSpace()
        pendingSymbol = null
        val ic = currentInputConnection
        var replaced = false
        if (ic != null && pending.canReplace(candidate, ic.getSelectedText(0)?.toString(),
                ic.getTextBeforeCursor(pending.insertedText.length, 0)?.toString())) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(pending.insertedText.length, 0)
            ic.commitText(candidate, 1)
            ic.endBatchEdit()
            replaced = true
        }
        keyboardView?.clearCandidates()
        if (replaced) startPendingPunctuationSpace()
    }

    private fun handleEmoji(emoji: String) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        finishLatinOrDropDeferredSpace()
        insertSpaceAfterHalfPunctuation(emoji)
        commitText(emoji)
    }

    private fun handleClipboardPaste(text: String) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        finishLatinOrDropDeferredSpace()
        insertSpaceAfterHalfPunctuation(text)
        commitText(text)
    }

    private fun handleSpace() {
        suppressedPunctuationSpaceAnchor = null
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        if (pendingPunctuationSpace) {
            finishPendingPunctuationSpace()
            spaceDeferredUntilNextLatin = false
            return
        }
        if (pendingLatinSpace) {
            resolvePendingLatinSpace(keep = true)
            spaceDeferredUntilNextLatin = false
            return
        }
        val ignoreCommitSpace = ThemeManager.getIgnoreSpaceAfterLatin(this)
        val rawLatin = composition.rawKeys.isNotEmpty() && !pendingSwipeChoice
        val hadComposition = composition.rawKeys.isNotEmpty()
        val candidateInsertedSpace = pendingSwipeChoice &&
            composition.candidates.firstOrNull()?.let(::candidateNeedsSpace) == true
        finishEnglishComposition()
        if (LatinSpaceCommit.deferCommitSpace(
                ignoreCommitSpace, ThemeManager.getRestoreSpaceBetweenLatin(this), rawLatin)) {
            pendingLatinSpace = currentInputConnection?.setComposingText(" ", 1) == true
            spaceDeferredUntilNextLatin = true
        } else if (LatinSpaceCommit.insertsSpace(ignoreCommitSpace, hadComposition,
                candidateInsertedSpace)) {
            commitText(" ")
            spaceDeferredUntilNextLatin = false
        } else {
            spaceDeferredUntilNextLatin = false
        }
    }

    private fun handleBackspace() {
        resetLearnedPhraseContext()
        if (pendingLatinSpace) {
            resolvePendingLatinSpace(keep = false)
            spaceDeferredUntilNextLatin = false
            return
        }
        swipeEnglishWords = emptyList()
        swipeChineseCodes = emptyList()
        lastSelectedText = ""
        if (pendingPunctuationSpace) {
            spaceDeferredUntilNextLatin = false
            cancelPendingPunctuationSpace()
            suppressedPunctuationSpaceAnchor = captureEditorAnchor()
            return
        }
        if (isEmailSuggestionsMode) {
            spaceDeferredUntilNextLatin = false
            if (emailTypedSoFar.isNotEmpty()) {
                emailTypedSoFar = emailTypedSoFar.dropLast(1)
                deleteOneGrapheme()
                emailAnchor = captureEditorAnchor()
                updateEmailSuggestionsView()
            } else {
                clearEmailSuggestions()
                deleteOneGrapheme()
            }
            return
        }
        // Exit associated phrases mode on backspace
        if (isAssociatedPhrasesMode) {
            spaceDeferredUntilNextLatin = false
            clearAssociatedPhrases()
            // Also delete the character in text field (grapheme-aware)
            deleteOneGrapheme()
            return
        }

        if (composition.rawKeys.isNotEmpty()) {
            pendingSwipeChoice = false
            val newKeys = composition.rawKeys.dropLast(1)
            // Also remove the case tracking for the deleted letter
            if (letterCases.isNotEmpty()) {
                letterCases.removeAt(letterCases.lastIndex)
            }
            if (newKeys.isEmpty()) {
                val prefix = deferredLatinPrefix()
                currentInputConnection?.setComposingText(prefix, 1)
                if (prefix.isEmpty()) currentInputConnection?.finishComposingText()
                else {
                    pendingLatinSpace = true
                    deferredSpaceAnchor = captureEditorAnchor()
                }
                clearComposition()
            } else {
                currentInputConnection?.setComposingText(deferredLatinPrefix() + getDisplayKeys(newKeys), 1)
                updateComposition(newKeys)
            }
        } else {
            // Editing already committed text cancels a space held for the next word.
            spaceDeferredUntilNextLatin = false
            val removesPunctuationSpace = backspaceWillRemovePunctuationSpace()
            deleteOneGrapheme()
            if (removesPunctuationSpace) {
                suppressedPunctuationSpaceAnchor = captureEditorAnchor()
            }
        }
    }

    /**
     * Delete one grapheme cluster (visual character) before the cursor.
     * This handles emojis with skin tones, ZWJ sequences, and other multi-codepoint characters.
     *
     * If the user has a non-empty selection, the entire selection is deleted in
     * one shot (matches the platform expectation that backspace replaces a selection).
     */
    private fun deleteOneGrapheme() {
        val ic = currentInputConnection ?: return

        // If there's a selection, delete the whole selection instead of one char.
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }

        // Get text before cursor (enough to cover longest emoji sequences)
        val textBefore = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        if (textBefore.isEmpty()) return

        // Use BreakIterator to find grapheme cluster boundaries
        val breakIterator = android.icu.text.BreakIterator.getCharacterInstance()
        breakIterator.setText(textBefore)

        // Find the last grapheme cluster boundary
        breakIterator.last()
        var start = breakIterator.previous()

        if (start == android.icu.text.BreakIterator.DONE) {
            // Only one grapheme cluster, delete all
            start = 0
        }

        // Calculate how many UTF-16 code units to delete
        val charsToDelete = textBefore.length - start

        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
    }

    private fun handleEnter() {
        suppressedPunctuationSpaceAnchor = null
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        // Exit associated phrases mode on enter
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        finishLatinOrDropDeferredSpace()
        resetLearnedPhraseContext()
        dispatchEditorActionOrEnter()
    }

    /**
     * Honor the target editor's requested IME action, while still inserting a
     * real line break in multiline fields. Some apps ignore synthetic Enter
     * key events; others ignore a committed newline in single-line fields, so
     * the fallback is deliberately based on EditorInfo instead of one global
     * behavior.
     */
    private fun dispatchEditorActionOrEnter() {
        val inputConnection = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info == null) {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
            return
        }

        inputConnection.finishComposingText()
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val isText = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val isMultiline = isText && info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

        val actionHandled = if (!noEnterAction) {
            when {
                info.actionId != 0 -> inputConnection.performEditorAction(info.actionId)
                action in EDITOR_ACTIONS -> inputConnection.performEditorAction(action)
                else -> false
            }
        } else {
            false
        }

        if (actionHandled) return
        if (isMultiline && inputConnection.commitText("\n", 1)) return
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
    }

    private fun commitCandidate(text: String) {
        val visibleComposition = if (pendingSwipeChoice) {
            composition.candidates.firstOrNull().orEmpty().let {
                (if (LatinSpaceCommit.keptAsLatin(it)) deferredLatinPrefix() else "") + it
            }
        } else if (composition.rawKeys.isNotEmpty()) deferredLatinPrefix() + getDisplayKeys(composition.rawKeys)
        else ""
        pendingSwipeChoice = false
        val keptLatin = LatinSpaceCommit.keptAsLatin(text)
        val latinWord = EnglishSuggestions.isLatinWord(text)
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val punctSpace = if (composition.rawKeys.isEmpty() &&
            !isPunctuationSpaceSuppressed() &&
            PunctuationSpacing.needsSpaceBefore(beforeCursor, text)) " " else ""
        val leadingSpace = if (LatinSpaceCommit.restoreDeferredSpace(
                restoresSpaceBetweenLatin(), spaceDeferredUntilNextLatin, keptLatin)) " " else ""
        spaceDeferredUntilNextLatin = false
        val needsSpace = candidateNeedsSpace(text)
        lastSelectedText = punctSpace + leadingSpace + text + if (needsSpace) " " else ""
        val committed = commitChineseCandidate(text, lastSelectedText, visibleComposition)
        if (committed && canPersonalize() && ThemeManager.getRecentCandidatesEnabled(this) &&
            composition.rawKeys.isNotEmpty()) {
            RecentCandidateManager.recordUsage(this, composition.rawKeys, text)
        }
        clearComposition()
        // Associated phrases follow Chinese selections, not Latin autocomplete.
        if (committed && !latinWord) showAssociatedPhrases(LearnedPhraseModel.characters(text).lastOrNull().orEmpty())
    }

    private fun getDisplayKeys(text: String): String {
        val result = StringBuilder()
        for (i in text.indices) {
            val char = text[i]
            val shouldBeUpper = letterCases.getOrNull(i) ?: false
            result.append(if (shouldBeUpper) char.uppercaseChar() else char.lowercaseChar())
        }
        return result.toString()
    }

    private fun candidateNeedsSpace(text: String): Boolean =
        EnglishSuggestions.isLatinWord(text) && ThemeManager.getSpaceAfterEnglishCandidate(this) &&
            currentInputConnection?.getTextAfterCursor(1, 0)?.firstOrNull()?.isWhitespace() != true

    private fun confirmPendingSwipeChoice() {
        if (!pendingSwipeChoice) return
        // An unresolved or unmatched code remains a literal Latin fallback.
        val choice = composition.candidates.firstOrNull() ?: composition.rawKeys.takeIf { it.isNotEmpty() }
        if (choice != null) commitCandidate(choice) else pendingSwipeChoice = false
    }

    /**
     * Commits visible Latin letters in place. Returns true when that raw string
     * was committed, including an unrecognized string left as typed letters.
     * The provisional separator stays in front of that string.
     */
    private fun finishEnglishComposition(): Boolean {
        if (pendingSwipeChoice) {
            confirmPendingSwipeChoice()
            return false
        }
        if (composition.rawKeys.isEmpty()) {
            if (pendingLatinSpace) {
                resolvePendingLatinSpace(keep = true)
                spaceDeferredUntilNextLatin = false
            }
            return false
        }
        resetLearnedPhraseContext()
        if (LatinSpaceCommit.restoreDeferredSpace(
                restoresSpaceBetweenLatin(), spaceDeferredUntilNextLatin, true)) {
            spaceDeferredUntilNextLatin = false
            currentInputConnection?.setComposingText(" " + getDisplayKeys(composition.rawKeys), 1)
        } else if (!restoresSpaceBetweenLatin()) {
            spaceDeferredUntilNextLatin = false
        }
        currentInputConnection?.finishComposingText()
        clearComposition()
        return true
    }

    private fun restoresSpaceBetweenLatin(): Boolean {
        return ThemeManager.getIgnoreSpaceAfterLatin(this) &&
            ThemeManager.getRestoreSpaceBetweenLatin(this)
    }

    /** Keep the separator inside the next composition so Chinese selection can remove it. */
    private fun deferredLatinPrefix(): String =
        if (spaceDeferredUntilNextLatin && restoresSpaceBetweenLatin()) " " else ""

    private fun resolvePendingLatinSpace(keep: Boolean) {
        if (!pendingLatinSpace) return
        if (!keep && anchorMatches(deferredSpaceAnchor)) currentInputConnection?.setComposingText("", 1)
        currentInputConnection?.finishComposingText()
        pendingLatinSpace = false
    }

    /** Finish raw Latin if any; otherwise drop its provisional trailing separator. */
    private fun finishLatinOrDropDeferredSpace() {
        resolvePendingLatinSpace(keep = false)
        cancelPendingPunctuationSpace()
        if (!finishEnglishComposition()) spaceDeferredUntilNextLatin = false
    }

    private fun commitText(text: String): Boolean {
        resetLearnedPhraseContext()
        return currentInputConnection?.commitText(text, 1) == true
    }

    private fun resetLearnedPhraseContext() {
        learnedPhraseContext = ""
        learnedPhraseAnchor = null
    }

    private fun canPersonalize(): Boolean = PhraseLearningPolicy.allows(currentInputEditorInfo) &&
        !isPasswordField && !isEmailField && !isUsernameField

    private fun allowsPhraseLearning(): Boolean = ThemeManager.getLearnedPhrasesEnabled(this) && canPersonalize()

    /** Only our own successful Chinese selections train the model, never surrounding app text. */
    private fun commitChineseCandidate(selected: String, committed: String = selected, visibleComposition: String = ""): Boolean {
        val canLearn = allowsPhraseLearning() && committed == selected && LearnedPhraseModel.isChinese(selected)
        if (!canLearn) return commitText(committed)
        val connection = currentInputConnection
        val before = connection?.getTextBeforeCursor(64 + visibleComposition.length, 0)?.toString()
        val prefix = before?.takeIf { it.endsWith(visibleComposition) }?.dropLast(visibleComposition.length)?.takeLast(64)
        val cursor = editorCursor()?.minus(visibleComposition.length)
        val previous = if (learnedPhraseAnchor?.matches(cursor, connection?.getSelectedText(0)?.toString(), prefix) == true)
            learnedPhraseContext else ""
        val success = commitText(committed)
        if (success && canLearn) {
            LearnedPhraseManager.recordAppend(this, previous, selected)
            learnedPhraseContext = LearnedPhraseModel.tail(previous + selected)
            learnedPhraseAnchor = captureEditorAnchor()
        }
        return success
    }

    private fun insertSpaceAfterHalfPunctuation(nextText: String) {
        if (isEmailSuggestionsMode || isPunctuationSpacingDisabledField()) return
        val connection = currentInputConnection ?: return
        if (pendingPunctuationSpace) {
            if (!anchorMatches(pendingPunctuationSpaceAnchor)) {
                finishPendingPunctuationSpace()
                return
            }
            val before = connection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val beforeWithoutPendingSpace = before.dropLast(1)
            if (PunctuationSpacing.needsSpaceBefore(beforeWithoutPendingSpace, nextText)) {
                finishPendingPunctuationSpace()
            } else {
                cancelPendingPunctuationSpace()
            }
            return
        }
        if (isPunctuationSpaceSuppressed()) return
        val before = connection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (PunctuationSpacing.needsSpaceBefore(before, nextText)) commitText(" ")
    }

    private fun isPunctuationSpaceSuppressed(): Boolean {
        val anchor = suppressedPunctuationSpaceAnchor ?: return false
        if (!suppressedPunctuationSpaceStillApplies(anchor)) {
            suppressedPunctuationSpaceAnchor = null
            return false
        }
        return true
    }

    private fun backspaceWillRemovePunctuationSpace(): Boolean {
        val connection = currentInputConnection ?: return false
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false
        val before = connection.getTextBeforeCursor(64, 0)?.toString() ?: return false
        return before.endsWith(' ') &&
            PunctuationSpacing.shouldOfferSpaceAfter(before.dropLast(1))
    }

    private fun suppressedPunctuationSpaceStillApplies(anchor: EditorAnchor?): Boolean {
        if (anchor == null) return false
        val connection = currentInputConnection ?: return false
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false

        val currentCursor = editorCursor()
        val charsAfterAnchor = if (anchor.cursor != null && currentCursor != null) {
            currentCursor - anchor.cursor
        } else {
            null
        }
        if (charsAfterAnchor != null && charsAfterAnchor < 0) return false
        if (charsAfterAnchor != null && charsAfterAnchor > 1024) return false

        val contextLength = if (charsAfterAnchor != null) {
            anchor.prefix.length + charsAfterAnchor
        } else {
            maxOf(512, anchor.prefix.length)
        }
        val context = connection.getTextBeforeCursor(contextLength, 0)?.toString() ?: return false
        if (!context.startsWith(anchor.prefix)) return false
        // Keep the user's choice while they edit the adjacent word, but let a
        // separating space end that context.
        return context.drop(anchor.prefix.length).none { it.isWhitespace() }
    }

    private fun startPendingPunctuationSpace() {
        if (isPunctuationSpacingDisabledField() || pendingPunctuationSpace ||
            keyboardView?.isNumberPadMode() == true) return
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (!PunctuationSpacing.shouldOfferSpaceAfter(before)) return
        if (connection.setComposingText(" ", 1)) {
            pendingPunctuationSpace = true
            pendingPunctuationSpaceAnchor = captureEditorAnchor()
        } else {
            // Preserve spacing in editors that reject composing text.
            connection.commitText(" ", 1)
        }
    }

    private fun finishPendingPunctuationSpace() {
        if (!pendingPunctuationSpace) return
        currentInputConnection?.finishComposingText()
        clearPendingPunctuationSpaceState()
    }

    private fun cancelPendingPunctuationSpace() {
        if (!pendingPunctuationSpace) return
        currentInputConnection?.let { connection ->
            connection.setComposingText("", 1)
            connection.finishComposingText()
        }
        clearPendingPunctuationSpaceState()
    }

    private fun clearPendingPunctuationSpaceState() {
        pendingPunctuationSpace = false
        pendingPunctuationSpaceAnchor = null
    }

    // ==================== ASSOCIATED PHRASES ====================

    /**
     * Show associated phrases for the given character.
     * This is called after committing a Chinese character.
     */
    private fun showAssociatedPhrases(character: String) {
        if (character.isEmpty()) { clearAssociatedPhrases(); return }
        val revision = candidateRevision.incrementAndGet()
        val editor = currentInputConnection
        val info = currentInputEditorInfo
        val beforeCursor = editor?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val learned = if (allowsPhraseLearning() && anchorMatches(learnedPhraseAnchor))
            LearnedPhraseManager.suggestions(this, learnedPhraseContext).toList() else emptyList()
        associatedAnchor = learnedPhraseAnchor ?: captureEditorAnchor()
        isAssociatedPhrasesMode = true
        associatedPhrases = learned
        learnedAssociatedCandidates = learned.toSet()
        lastCommittedChar = character
        updateAssociatedPhrasesView()
        candidateWorker.submit {
            if (candidateRevision.get() != revision) return@submit
            val phrases = runCatching { (learned + AssociatedPhraseSuggestions.merge(beforeCursor, character,
                curatedAssociatedPhrases, mckRelatedPhrases::lookup, associatedPhrasesTable::lookup)).distinct() }.getOrDefault(learned)
            candidateMain.post {
                if (destroyed || candidateRevision.get() != revision || currentInputConnection !== editor ||
                    currentInputEditorInfo !== info || !isAssociatedPhrasesMode) return@post
                if (associatedAnchor != null && !anchorMatches(associatedAnchor)) { clearAssociatedPhrases(); return@post }
                if (phrases.isEmpty()) { clearAssociatedPhrases(); return@post }
                associatedPhrases = phrases
                updateAssociatedPhrasesView()
            }
        }
    }

    /**
     * Clear associated phrases mode and return to normal input.
     */
    private fun clearAssociatedPhrases() {
        invalidateCandidateWork()
        associatedAnchor = null
        isAssociatedPhrasesMode = false
        associatedPhrases = emptyList()
        learnedAssociatedCandidates = emptySet()
        lastCommittedChar = ""
        keyboardView?.clearCandidates()
    }

    /**
     * Update the candidate bar to show associated phrases.
     */
    private fun updateAssociatedPhrasesView() {
        if (learnedAssociatedCandidates.isNotEmpty() && !allowsPhraseLearning()) {
            showAssociatedPhrases(lastCommittedChar)
            return
        }
        keyboardView?.let { view ->
            // Clear composition display since we're showing associated phrases
            view.setComposition("", "")
            view.setCandidates(associatedPhrases, learnedAssociatedCandidates)
        }
    }

    /**
     * Handle associated phrase selection.
     */
    private fun handleAssociatedPhraseSelected(phrase: String) {
        spaceDeferredUntilNextLatin = false
        insertSpaceAfterHalfPunctuation(phrase)
        lastSelectedText = phrase
        commitChineseCandidate(phrase)
        // Show associated phrases for the last character of the selected phrase
        val lastChar = LearnedPhraseModel.characters(phrase).lastOrNull().orEmpty()
        showAssociatedPhrases(lastChar)
    }

    // ==================== EMAIL DOMAIN SUGGESTIONS ====================

    private fun enterEmailSuggestionsMode() {
        isEmailSuggestionsMode = true
        emailTypedSoFar = ""
        emailAnchor = captureEditorAnchor()
        // @ is on the symbol keyboard; return to letter mode so the candidate bar is visible
        if (isSymbolMode) {
            isSymbolMode = false
            keyboardView?.setLetterMode()
        }
        updateEmailSuggestionsView()
    }

    private fun clearEmailSuggestions() {
        isEmailSuggestionsMode = false
        emailTypedSoFar = ""
        emailAnchor = null
        keyboardView?.clearCandidates()
    }

    private fun updateEmailSuggestionsView() {
        val filtered = EmailDomains.matching(emailTypedSoFar)
        if (filtered.isEmpty()) {
            clearEmailSuggestions()
            return
        }
        keyboardView?.let { view ->
            view.setComposition("", "")
            view.setCandidates(filtered)
        }
    }

    private fun handleEmailSuggestionSelected(domain: String) {
        val expectedPrefix = "@" + emailTypedSoFar
        if (!anchorMatches(emailAnchor) || domain !in EmailDomains.matching(emailTypedSoFar) ||
            currentInputConnection?.getTextBeforeCursor(expectedPrefix.length, 0)?.toString() != expectedPrefix) {
            clearEmailSuggestions()
            return
        }
        val remaining = domain.drop(emailTypedSoFar.length)
        if (remaining.isNotEmpty()) {
            commitText(remaining)
        }
        clearEmailSuggestions()
    }

    private fun enabledMethods(): Set<MethodMembership.Method> = buildSet {
        if (ThemeManager.getMethodCantonese(this@HkInputMethodService))
            add(MethodMembership.Method.CANTONESE)
        if (ThemeManager.getMethodCangjie(this@HkInputMethodService))
            add(MethodMembership.Method.CANGJIE)
        if (ThemeManager.getMethodQuick(this@HkInputMethodService))
            add(MethodMembership.Method.QUICK)
        if (ThemeManager.getMethodEnglish(this@HkInputMethodService))
            add(MethodMembership.Method.ENGLISH)
    }

    private fun canSuggestTypos(): Boolean {
        if (isPasswordField || isEmailField || isUsernameField || pendingSwipeChoice ||
            swipeEnglishWords.isNotEmpty() || swipeChineseCodes.isNotEmpty()) return false
        val type = currentInputEditorInfo?.inputType ?: return true
        // Ordinary editors (including Google Keep) may request NO_SUGGESTIONS.
        // Our opt-in candidates require explicit selection and never auto-replace;
        // honor the keyboard's recovery toggles, as with existing exact candidates.
        return (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (type and InputType.TYPE_MASK_VARIATION) != InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun updateComposition(rawKeys: String) {
        val revision = candidateRevision.incrementAndGet()
        val editor = currentInputConnection
        val info = currentInputEditorInfo
        val pageSize = ThemeManager.getCandidatesPerPage(this)
        val enabledMethods = enabledMethods().toSet()
        val typed = getDisplayKeys(rawKeys)
        val password = isPasswordField
        val quickEnabled = ThemeManager.getMethodQuick(this)
        val englishEnabled = ThemeManager.getMethodEnglish(this)
        val spellCheck = ThemeManager.getEnglishSpellCheck(this)
        val cantoneseRecovery = ThemeManager.getCantoneseTypoRecovery(this)
        val cangjieRecovery = ThemeManager.getCangjieTypoRecovery(this)
        val suggestTypos = canSuggestTypos()
        val custom = CustomDictionaryManager.lookup(this, rawKeys).toList()
        val counts = if (canPersonalize() && ThemeManager.getRecentCandidatesEnabled(this))
            RecentCandidateManager.countsForCode(this, rawKeys) else emptyMap()
        val swipeChineseCodes = this.swipeChineseCodes.toList()
        val swipeEnglishWords = this.swipeEnglishWords.toList()
        lookupPending = true
        composition = CompositionState(rawKeys = rawKeys, pageSize = pageSize, activeKeyLength = rawKeys.length)
        updateUI()
        candidateWorker.submit {
            if (candidateRevision.get() != revision) return@submit
            val results = runCatching {
                // Filter the merged dictionary using method-specific membership hints,
                // retaining its ranking and the uninterrupted Latin composition.
                val lookupKeys = rawKeys
                var candidates = methodMembership.filter(lookupKeys,
                    mixedDictionary.lookup(lookupKeys), enabledMethods)
                candidates = (candidates + methodMembership.supplementalCandidates(lookupKeys, enabledMethods)).distinct()
                candidates = promoteReviewedCharacter(lookupKeys, candidates)

                // A glide can be ambiguous between short Chinese codes. Keep the best
                // visible Latin code, but expose nearby valid codes' Chinese candidates.
                if (swipeChineseCodes.isNotEmpty()) {
                    val alternatives = swipeChineseCodes.flatMap { code ->
                        methodMembership.filter(code, mixedDictionary.lookup(code), enabledMethods) +
                            methodMembership.supplementalCandidates(code, enabledMethods)
                    }
                    candidates = (candidates + alternatives).distinct()
                }
                if (MethodMembership.Method.CANTONESE in enabledMethods) {
                    candidates = promoteEverydayCantonese(candidates)
                }

                // Retain the original OpenVanilla Quick table as a resilient fallback.
                if (candidates.isEmpty() && rawKeys.length <= 2 && quickEnabled) {
                    candidates = simplexTable.lookup(rawKeys)
                }

                // User entries precede bundled results, but learned selection counts
                // below can still lift any frequently used candidate above them.
                candidates = prioritizeCustomCandidates(
                    custom, candidates)

                // English autocomplete is below the bundled Chinese choices by default.
                // Learned per-code usage can lift a frequently selected word above them.
                if (!password && englishEnabled) {
                    val contractions = EnglishSuggestions.contractions(typed)
                    val english = if (englishAutocomplete.size > 0) englishAutocomplete.completions(typed)
                        else EnglishSuggestions.completions(typed)
                    candidates = (contractions + candidates + swipeEnglishWords + english).distinct()
                }
                if (counts.isNotEmpty()) {
                    candidates = rankCandidates(candidates, counts)
                }

                // Protect exact characters/full English words, but allow recovered characters
                // before exact phrase shorthand (nfo -> ngo/我 before nfo/年貨).
                if (suggestTypos) {
                    val englishFixes = if (MethodMembership.Method.ENGLISH in enabledMethods &&
                        spellCheck) englishTypoMatcher.candidates(typed)
                        else emptyList()
                    val recoveryMethods = enabledMethods.filterTo(mutableSetOf()) {
                        (it == MethodMembership.Method.CANTONESE && cantoneseRecovery) ||
                            (it == MethodMembership.Method.CANGJIE && cangjieRecovery)
                    }
                    val chineseFixes = methodMembership.recoverCodes(lookupKeys, recoveryMethods)
                        .flatMap { it.typedCandidates() }
                    candidates = mergeTypoCandidates(typed, candidates, englishFixes + chineseFixes) {
                        if (counts.isNotEmpty())
                            rankCandidates(it, counts) else it
                    }
                }

                // Symbols are exact English keyword matches and deliberately follow
                // the Chinese dictionary (and any spelling fix).
                if (!password) {
                    candidates = (candidates + UnicodeWordSuggestions.lookupEnglish(rawKeys)).distinct()
                }
                // Cangjie and Quick often identify the same text; show it only once.
                candidates = sanitizeCandidates(candidates)

                candidates
            }.getOrDefault(emptyList())
            candidateMain.post {
                if (destroyed || candidateRevision.get() != revision || currentInputConnection !== editor ||
                    currentInputEditorInfo !== info || composition.rawKeys != rawKeys) return@post
                val before = editor?.getTextBeforeCursor(128, 0)?.toString()
                if (before != null && !before.endsWith(typed.takeLast(128))) {
                    deferredSwipeEvents.clear()
                    clearComposition()
                    return@post
                }
                lookupPending = false
                composition = CompositionState(rawKeys = rawKeys, candidates = results,
                    pageSize = pageSize, activeKeyLength = rawKeys.length)
                if (pendingSwipeChoice && results.isNotEmpty()) {
                    val choice = results.first()
                    editor?.setComposingText((if (LatinSpaceCommit.keptAsLatin(choice)) deferredLatinPrefix() else "") + choice, 1)
                }
                updateUI()
                while (!lookupPending && deferredSwipeEvents.isNotEmpty()) {
                    handleKeyEvent(deferredSwipeEvents.removeFirst())
                }
            }
        }
    }

    private fun invalidateCandidateWork() {
        candidateRevision.incrementAndGet()
        candidateWorker.cancel()
        lookupPending = false
    }

    private fun clearComposition() {
        invalidateCandidateWork()
        composition = CompositionState.EMPTY
        pendingSwipeChoice = false
        swipeEnglishWords = emptyList()
        swipeChineseCodes = emptyList()
        letterCases.clear()
        keyboardView?.clearCandidates()
    }

    /**
     * Get the raw keys with proper case applied for display purposes.
     */
    private fun getDisplayKeys(): String {
        return getDisplayKeys(composition.rawKeys)
    }

    private fun updateUI() {
        updateCandidateView()
    }

    private fun updateCandidateView() {
        if (isEmailSuggestionsMode) {
            updateEmailSuggestionsView()
            return
        }
        if (isAssociatedPhrasesMode) {
            updateAssociatedPhrasesView()
            return
        }
        keyboardView?.let { view ->
            val isMasked = isPasswordField && isPasswordMaskEnabled
            // Full Cangjie codes have at most five keys; longer buffers are
            // usually English, where radical previews only crowd suggestions.
            val radicals = if (isMasked || composition.rawKeys.length > 5) "" else composition.radicalDisplay
            val keys = if (isMasked) "*".repeat(composition.rawKeys.length) else getDisplayKeys()
            view.setComposition(radicals, keys)
            view.setMaskToggle(
                show = isPasswordField && composition.rawKeys.isNotEmpty(),
                isMasked = isPasswordMaskEnabled
            )

            val symbol = pendingSymbol
            if (symbol != null) {
                view.setCandidates(symbol.alternatives)
            } else if (composition.hasCandidates) {
                view.setCandidates(composition.candidates)
            } else if (lookupPending && composition.rawKeys.isNotEmpty()) {
                view.setCandidates(emptyList())
            } else if (composition.rawKeys.isNotEmpty()) {
                if (isPasswordField) {
                    // Password fields never display a no-match hint.
                    view.clearCandidateSlotsOnly()
                } else {
                    view.showNoMatch()
                }
            } else {
                view.clearCandidates()
            }
        }
    }

    // ==================== VIEW ALL CANDIDATES ====================

    /**
     * Handle clicking the page indicator to show all candidates in a full grid view.
     */
    private fun handlePageIndicatorClicked() {
        val allCandidates = when {
            pendingSymbol != null -> pendingSymbol!!.alternatives
            isEmailSuggestionsMode -> EmailDomains.matching(emailTypedSoFar)
            isAssociatedPhrasesMode -> associatedPhrases
            else -> composition.candidates
        }

        if (allCandidates.isEmpty()) return

        keyboardView?.showCandidateGrid(allCandidates)
    }

    override fun onDestroy() {
        deferredSwipeEvents.clear()
        pendingClipboardEvent = false
        ClipboardHistoryManager.removeListener(clipboardStorageListener)
        destroyed = true
        invalidateCandidateWork()
        candidateWorker.close()
        candidateMain.removeCallbacksAndMessages(null)
        LearnedPhraseManager.flush()
        super.onDestroy()
        // Unregister clipboard listener to avoid memory leaks
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
    }

    companion object {
        private val EDITOR_ACTIONS = setOf(
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS
        )

    }
}
