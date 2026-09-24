package com.awcjack.dualquickime

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.awcjack.dualquickime.convert.ChineseConverter
import com.awcjack.dualquickime.data.AssociatedPhrasesParser
import com.awcjack.dualquickime.data.MckRelatedPhrases
import com.awcjack.dualquickime.data.EnglishSuggestions
import com.awcjack.dualquickime.data.EnglishAutocomplete
import com.awcjack.dualquickime.data.UnicodeWordSuggestions
import com.awcjack.dualquickime.data.SymbolCatalogue
import com.awcjack.dualquickime.data.PendingSymbol
import com.awcjack.dualquickime.data.AssociatedPhrasesTable
import com.awcjack.dualquickime.data.AssociatedPhraseSuggestions
import com.awcjack.dualquickime.data.CuratedAssociatedPhrases
import com.awcjack.dualquickime.data.CinParser
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.data.CompositionSelection
import com.awcjack.dualquickime.data.ContextualPunctuation
import com.awcjack.dualquickime.data.CustomDictionaryManager
import com.awcjack.dualquickime.data.prioritizeCustomCandidates
import com.awcjack.dualquickime.data.promoteReviewedCharacter
import com.awcjack.dualquickime.data.MixedDictionary
import com.awcjack.dualquickime.data.NumericPadSpec
import com.awcjack.dualquickime.data.MethodMembership
import com.awcjack.dualquickime.data.RecentCandidateManager
import com.awcjack.dualquickime.data.SimplexTable
import com.awcjack.dualquickime.data.ShortcutPhraseManager
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import com.awcjack.dualquickime.ui.VoiceInputView
import com.awcjack.dualquickime.voice.ModelDownloadManager
import com.awcjack.dualquickime.voice.VoiceInputManager
import com.awcjack.dualquickime.voice.VoiceModelType
import kotlin.concurrent.thread

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

    private lateinit var simplexTable: SimplexTable
    private lateinit var mixedDictionary: MixedDictionary
    private lateinit var methodMembership: MethodMembership
    private var englishAutocomplete = EnglishAutocomplete.EMPTY
    private lateinit var associatedPhrasesTable: AssociatedPhrasesTable
    private var curatedAssociatedPhrases = CuratedAssociatedPhrases.EMPTY
    private lateinit var mckRelatedPhrases: MckRelatedPhrases
    private var composition = CompositionState.EMPTY
    private var pendingSymbol: PendingSymbol? = null

    private var keyboardView: KeyboardView? = null

    // Associated phrases mode: when true, candidate bar shows associated phrases
    private var isAssociatedPhrasesMode = false
    private var associatedPhrases = listOf<String>()
    private var lastCommittedChar = ""

    // Track current keyboard mode
    private var isSymbolMode = false

    // Track original case of letters for English output
    // Maps index position to whether it was uppercase
    private var letterCases = mutableListOf<Boolean>()

    // Email domain suggestion mode: triggered when @ is typed
    private var isEmailSuggestionsMode = false
    private var emailTypedSoFar = ""

    // Whether the currently focused field is a password field
    private var isPasswordField = false
    private var isEmailField = false
    // Whether password masking is active (user can toggle with the eye button)
    private var isPasswordMaskEnabled = true

    // Track which character set is currently loaded
    private var currentCharsetExtended: Boolean? = null

    // System clipboard manager and listener
    private var clipboardManager: ClipboardManager? = null

    // Voice input components
    private var voiceInputManager: VoiceInputManager? = null
    private var voiceInputView: VoiceInputView? = null
    private var rootContainer: FrameLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleSystemClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        // Load simplex data based on user setting (extended by default)
        loadSimplexTable()
        mixedDictionary = MixedDictionary(assets)
        methodMembership = assets.open("method-membership.tsv").bufferedReader().use { membership ->
            assets.open("method-phrase-overrides.tsv").bufferedReader().use { overrides ->
                MethodMembership(membership.lineSequence(), overrides.lineSequence())
            }
        }
        englishAutocomplete = runCatching {
            EnglishAutocomplete.parse(assets.open("english-autocomplete.txt"))
        }.getOrDefault(EnglishAutocomplete.EMPTY)
        mckRelatedPhrases = MckRelatedPhrases(assets)
        // Load associated phrases table
        loadAssociatedPhrasesTable()

        // Register clipboard listener to capture system clipboard changes
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    /**
     * Load the simplex table based on user's character set preference.
     */
    private fun loadSimplexTable() {
        val filename = ThemeManager.getSimplexFilename(this)
        try {
            simplexTable = CinParser().parse(assets.open(filename))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            simplexTable = SimplexTable(emptyList())
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
        if (!ClipboardHistoryManager.isEnabled(this)) return

        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        val text = item.coerceToText(this)?.toString()

        if (!text.isNullOrBlank()) {
            ClipboardHistoryManager.addItem(this, text)
        }
    }

    override fun onCreateInputView(): View {
        // Create root container to hold keyboard and voice input overlay
        rootContainer = FrameLayout(this)

        keyboardView = KeyboardView(this).apply {
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
                // Switching keyboard pages accepts the visible Latin text.
                finishEnglishComposition()
            }
            setOnPageIndicatorClickedListener {
                handlePageIndicatorClicked()
            }
            setOnCandidateRefreshRequestedListener {
                // Refresh candidate view when returning from symbol/emoji/clipboard/grid mode
                updateCandidateView()
            }
            setOnMaskToggleListener {
                isPasswordMaskEnabled = !isPasswordMaskEnabled
                updateCandidateView()
            }
        }
        rootContainer?.addView(keyboardView)

        // Create voice input overlay (initially hidden)
        voiceInputView = VoiceInputView(this).apply {
            setOnCancelListener {
                // Cancel: close voice input without committing
                closeVoiceInput()
            }
            setOnResetListener {
                // Reset: clear the pending text but keep listening
                clearVoiceTranscript()
            }
            setOnFinishListener {
                // Stop: stop listening, flush buffered audio, show for review
                finishVoiceInput()
            }
            setOnCommitListener { text ->
                // Commit: commit the text and close voice input
                commitVoiceText(text)
            }
        }
        rootContainer?.addView(voiceInputView)

        return rootContainer!!
    }

    /**
     * Never use fullscreen mode so keyboard is always visible properly.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // The framework default hides the soft keyboard when it thinks a hardware
    // keyboard is attached (docks, Bluetooth keyboards, some OEMs that report one
    // spuriously). Always show our IME — if the user invoked us they want to type.
    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Invalidate caches to pick up any settings changes
        ThemeManager.invalidateCache()
        ClipboardHistoryManager.invalidateCache()
        RecentCandidateManager.invalidateCache()
        CustomDictionaryManager.invalidateCache()

        // Pre-bind the :voice process if the user has Qwen3-ASR selected.
        // Saves the 500 ms – 2 s bindService cold start off the mic-tap path,
        // while still leaving the 700 MB model load lazy.
        maybePreBindVoiceService()

        // Check if character set setting changed - reload if needed
        val useExtended = ThemeManager.getUseExtendedCharset(this)
        if (currentCharsetExtended != useExtended) {
            currentCharsetExtended = useExtended
            loadSimplexTable()
        }

        isPasswordField = isPasswordInputField(info)
        keyboardView?.setSensitiveField(isPasswordField)
        isEmailField = info?.inputType?.let { type ->
            (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
                (type and InputType.TYPE_MASK_VARIATION) in setOf(
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        } ?: false
        isPasswordMaskEnabled = true
        // Refresh theme in case it changed in settings
        keyboardView?.refreshTheme()
        // Clear composition when starting new input
        pendingSymbol = null
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
    }

    private fun isPasswordInputField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun handleKeyEvent(event: KeyboardView.KeyEvent) {
        if (pendingSymbol != null && event !is KeyboardView.KeyEvent.Symbol) {
            pendingSymbol = null
            keyboardView?.clearCandidates()
        }
        when (event) {
            is KeyboardView.KeyEvent.Letter -> handleLetter(event.char)
            is KeyboardView.KeyEvent.Number -> handleNumber(event.digit)
            is KeyboardView.KeyEvent.ShortcutPhrase -> handleShortcutPhrase(event.digit)
            is KeyboardView.KeyEvent.Symbol -> handleSymbol(event)
            is KeyboardView.KeyEvent.Emoji -> handleEmoji(event.emoji)
            is KeyboardView.KeyEvent.ClipboardPaste -> handleClipboardPaste(event.text)
            is KeyboardView.KeyEvent.CalculatorInsert -> if (!isPasswordField) {
                finishEnglishComposition()
                commitText(event.text)
            }
            KeyboardView.KeyEvent.Space -> handleSpace()
            KeyboardView.KeyEvent.Backspace -> handleBackspace()
            KeyboardView.KeyEvent.Enter -> handleEnter()
            KeyboardView.KeyEvent.HideKeyboard -> {
                finishEnglishComposition()
                pendingSymbol = null
                clearAssociatedPhrases()
                clearEmailSuggestions()
                requestHideSelf(0)
            }
            KeyboardView.KeyEvent.VoiceInput -> handleVoiceInput()
            KeyboardView.KeyEvent.OpenSettings -> {
                finishEnglishComposition()
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            is KeyboardView.KeyEvent.ConvertChinese -> handleConvertChinese(event.direction)
        }
    }

    /**
     * Convert Chinese text between Simplified and Traditional using OpenCC.
     *
     * If the user has a selection, convert the selection. Otherwise fall
     * back to the last sentence before the cursor so the user doesn't have
     * to select first — handy for fixing a sentence they just typed.
     */
    private fun handleConvertChinese(direction: KeyboardView.KeyEvent.ConvertDirection) {
        if (!ChineseConverter.isAvailable()) return

        // Commit any pending composition first so it isn't lost.
        finishEnglishComposition()
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
        if (isEmailSuggestionsMode) {
            val lowerChar = char.lowercaseChar()
            commitText(lowerChar.toString())
            emailTypedSoFar += lowerChar
            updateEmailSuggestionsView()
            return
        }
        // Exit associated phrases mode when typing
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        val isUpperCase = char.isUpperCase()
        val lowerChar = char.lowercaseChar()
        val newRawKeys = composition.rawKeys + lowerChar

        // Keep the Latin text visible in the app while Chinese options are open.
        letterCases.add(isUpperCase)
        currentInputConnection?.setComposingText(getDisplayKeys(newRawKeys), 1)
        updateComposition(newRawKeys)
    }

    private fun handleNumber(digit: Int) {
        if (isEmailSuggestionsMode) {
            val digitStr = digit.toString()
            commitText(digitStr)
            emailTypedSoFar += digitStr
            updateEmailSuggestionsView()
            return
        }
        finishEnglishComposition()
        // Then commit the number
        val text = digit.toString()
        commitText(text)
        if (isSymbolMode) {
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
        if (composition.rawKeys.isNotEmpty() && CompositionSelection.movedOutside(
                newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // The literal Latin text is already displayed by setComposingText.
            // Finish it in place without inserting a space or choosing a candidate.
            finishEnglishComposition()
        }
    }

    private fun handleShortcutPhrase(digit: Int) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        finishEnglishComposition()
        val phrase = ShortcutPhraseManager.get(this, digit)
        commitText(phrase.ifEmpty { digit.toString() })
    }

    private fun handleSymbol(event: KeyboardView.KeyEvent.Symbol) {
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        pendingSymbol = null
        finishEnglishComposition()
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val choice = ContextualPunctuation.choose(event.char, beforeCursor, event.forceLiteral)
        val inserted = (choice?.inserted ?: event.char).toString()
        val alternatives = (listOfNotNull(choice?.alternative?.toString()) +
            SymbolCatalogue.candidatesForSymbol(inserted)).filterNot { it == inserted }.distinct()
        commitText(inserted)
        if (event.char == '@' && isEmailField && !isPasswordField) {
            // Email-domain mode owns the candidate bar after @; do not leave a
            // pending symbol that would intercept domain selection.
            enterEmailSuggestionsMode()
        } else if (keyboardView?.isNumberPadMode() != true) {
            showSymbolCandidates(inserted, alternatives)
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
        pendingSymbol = null
        val ic = currentInputConnection
        if (pending.canReplace(candidate, ic?.getSelectedText(0)?.toString(),
                ic?.getTextBeforeCursor(pending.insertedText.length, 0)?.toString())) {
            ic?.beginBatchEdit()
            ic?.deleteSurroundingText(pending.insertedText.length, 0)
            ic?.commitText(candidate, 1)
            ic?.endBatchEdit()
        }
        keyboardView?.clearCandidates()
    }

    private fun handleEmoji(emoji: String) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        finishEnglishComposition()
        // Then commit the emoji
        commitText(emoji)
    }

    private fun handleClipboardPaste(text: String) {
        if (isEmailSuggestionsMode) clearEmailSuggestions()
        finishEnglishComposition()
        // Commit the clipboard text
        commitText(text)
    }

    private fun handleSpace() {
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        if (isAssociatedPhrasesMode) clearAssociatedPhrases()
        finishEnglishComposition()
        commitText(" ")
    }

    private fun handleBackspace() {
        if (isEmailSuggestionsMode) {
            if (emailTypedSoFar.isNotEmpty()) {
                emailTypedSoFar = emailTypedSoFar.dropLast(1)
                deleteOneGrapheme()
                updateEmailSuggestionsView()
            } else {
                clearEmailSuggestions()
                deleteOneGrapheme()
            }
            return
        }
        // Exit associated phrases mode on backspace
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
            // Also delete the character in text field (grapheme-aware)
            deleteOneGrapheme()
            return
        }

        if (composition.rawKeys.isNotEmpty()) {
            val newKeys = composition.rawKeys.dropLast(1)
            // Also remove the case tracking for the deleted letter
            if (letterCases.isNotEmpty()) {
                letterCases.removeAt(letterCases.lastIndex)
            }
            if (newKeys.isEmpty()) {
                currentInputConnection?.setComposingText("", 1)
                currentInputConnection?.finishComposingText()
                clearComposition()
            } else {
                currentInputConnection?.setComposingText(getDisplayKeys(newKeys), 1)
                updateComposition(newKeys)
            }
        } else {
            // Delete character in text field (grapheme-aware for emojis)
            deleteOneGrapheme()
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
        var end = breakIterator.last()
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
        if (isEmailSuggestionsMode) {
            clearEmailSuggestions()
        }
        // Exit associated phrases mode on enter
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        finishEnglishComposition()
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
        // Record usage for recent candidates feature
        if (ThemeManager.getRecentCandidatesEnabled(this) && composition.rawKeys.isNotEmpty()) {
            RecentCandidateManager.recordUsage(this, composition.rawKeys, text)
        }
        val latinWord = EnglishSuggestions.isLatinWord(text)
        val needsSpace = latinWord && ThemeManager.getSpaceAfterEnglishCandidate(this) &&
            currentInputConnection?.getTextAfterCursor(1, 0)?.firstOrNull()?.isWhitespace() != true
        commitText(text + if (needsSpace) " " else "")
        clearComposition()
        // Associated phrases follow Chinese selections, not Latin autocomplete.
        if (!latinWord) showAssociatedPhrases(text.lastOrNull()?.toString() ?: "")
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

    private fun finishEnglishComposition() {
        if (composition.rawKeys.isEmpty()) return
        currentInputConnection?.finishComposingText()
        clearComposition()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    // ==================== ASSOCIATED PHRASES ====================

    /**
     * Show associated phrases for the given character.
     * This is called after committing a Chinese character.
     */
    private fun showAssociatedPhrases(character: String) {
        if (character.isEmpty()) {
            clearAssociatedPhrases()
            return
        }

        // The original keyboard indexes related words by the full preceding
        // prefix as well as one character (e.g. 抗病 -> 毒). Prefer that order.
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val phrases = AssociatedPhraseSuggestions.merge(
            beforeCursor, character, curatedAssociatedPhrases,
            mckRelatedPhrases::lookup, associatedPhrasesTable::lookup
        )
        if (phrases.isEmpty()) {
            clearAssociatedPhrases()
            return
        }

        // Enter associated phrases mode
        isAssociatedPhrasesMode = true
        associatedPhrases = phrases
        lastCommittedChar = character

        updateAssociatedPhrasesView()
    }

    /**
     * Clear associated phrases mode and return to normal input.
     */
    private fun clearAssociatedPhrases() {
        isAssociatedPhrasesMode = false
        associatedPhrases = emptyList()
        lastCommittedChar = ""
        keyboardView?.clearCandidates()
    }

    /**
     * Update the candidate bar to show associated phrases.
     */
    private fun updateAssociatedPhrasesView() {
        keyboardView?.let { view ->
            // Clear composition display since we're showing associated phrases
            view.setComposition("", "")
            view.setCandidates(associatedPhrases)
        }
    }

    /**
     * Handle associated phrase selection.
     */
    private fun handleAssociatedPhraseSelected(phrase: String) {
        commitText(phrase)
        // Show associated phrases for the last character of the selected phrase
        val lastChar = phrase.lastOrNull()?.toString() ?: ""
        showAssociatedPhrases(lastChar)
    }

    // ==================== EMAIL DOMAIN SUGGESTIONS ====================

    private fun enterEmailSuggestionsMode() {
        isEmailSuggestionsMode = true
        emailTypedSoFar = ""
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
        keyboardView?.clearCandidates()
    }

    private fun updateEmailSuggestionsView() {
        val filtered = EMAIL_DOMAINS.filter { it.startsWith(emailTypedSoFar) }
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
        val remaining = domain.drop(emailTypedSoFar.length)
        if (remaining.isNotEmpty()) {
            commitText(remaining)
        }
        clearEmailSuggestions()
    }

    private fun updateComposition(rawKeys: String) {
        val pageSize = ThemeManager.getCandidatesPerPage(this)

        // Filter the merged dictionary using method-specific membership hints,
        // retaining its ranking and the uninterrupted Latin composition.
        val lookupKeys = rawKeys
        val enabledMethods = buildSet {
            if (ThemeManager.getMethodCantonese(this@HkInputMethodService))
                add(MethodMembership.Method.CANTONESE)
            if (ThemeManager.getMethodCangjie(this@HkInputMethodService))
                add(MethodMembership.Method.CANGJIE)
            if (ThemeManager.getMethodQuick(this@HkInputMethodService))
                add(MethodMembership.Method.QUICK)
            if (ThemeManager.getMethodEnglish(this@HkInputMethodService))
                add(MethodMembership.Method.ENGLISH)
        }
        var candidates = methodMembership.filter(lookupKeys,
            mixedDictionary.lookup(lookupKeys), enabledMethods)
        candidates = (candidates + methodMembership.supplementalCandidates(lookupKeys, enabledMethods)).distinct()
        candidates = promoteReviewedCharacter(lookupKeys, candidates)

        // Retain the original OpenVanilla Quick table as a resilient fallback.
        if (candidates.isEmpty() && rawKeys.length <= 2 && ThemeManager.getMethodQuick(this)) {
            candidates = simplexTable.lookup(rawKeys)
        }

        // User entries precede bundled results, but learned selection counts
        // below can still lift any frequently used candidate above them.
        candidates = prioritizeCustomCandidates(
            CustomDictionaryManager.lookup(this, lookupKeys), candidates)

        // English remains visible in the editor. Offer only unambiguous one-edit
        // spelling fixes, and never silently replace what the user typed.
        if (!isPasswordField && ThemeManager.getMethodEnglish(this) && ThemeManager.getEnglishSpellCheck(this)) {
            EnglishSuggestions.correction(getDisplayKeys(rawKeys))?.let { correction ->
                candidates = listOf(correction) + candidates.filterNot { it.equals(correction, ignoreCase = true) }
            }
        }

        // English autocomplete is below the bundled Chinese choices by default.
        // Learned per-code usage can lift a frequently selected word above them.
        if (!isPasswordField && ThemeManager.getMethodEnglish(this)) {
            val typed = getDisplayKeys(rawKeys)
            val english = if (englishAutocomplete.size > 0) englishAutocomplete.completions(typed)
                else EnglishSuggestions.completions(typed)
            candidates = (candidates + english).distinct()
        }
        if (ThemeManager.getRecentCandidatesEnabled(this)) {
            candidates = RecentCandidateManager.reorderCandidates(this, lookupKeys, candidates)
        }

        // Symbols are exact English keyword matches and deliberately follow
        // the Chinese dictionary (and any spelling fix).
        if (!isPasswordField) {
            candidates = (candidates + UnicodeWordSuggestions.lookupEnglish(rawKeys)).distinct()
        }
        // Cangjie and Quick often identify the same text; show it only once.
        candidates = candidates.distinct()

        composition = CompositionState(
            rawKeys = rawKeys,
            candidates = candidates,
            currentPage = 0,
            pageSize = pageSize,
            activeKeyLength = rawKeys.length
        )
        updateUI()
    }

    private fun clearComposition() {
        composition = CompositionState.EMPTY
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
            isAssociatedPhrasesMode -> associatedPhrases
            else -> composition.candidates
        }

        if (allCandidates.isEmpty()) return

        keyboardView?.showCandidateGrid(allCandidates)
    }

    // ==================== VOICE INPUT ====================

    private fun handleVoiceInput() {
        // Check if voice input is enabled in settings
        if (!ThemeManager.getVoiceInputEnabled(this)) {
            return
        }

        // Get the user's selected model type
        val selectedModelType = VoiceModelType.fromId(ThemeManager.getVoiceModelType(this))

        // Check if the selected model is downloaded
        if (!ModelDownloadManager.isModelDownloaded(this, selectedModelType)) {
            // Start model download for the selected model
            startModelDownload(selectedModelType)
            return
        }

        // Check audio permission
        if (!hasAudioPermission()) {
            // Open settings to request permission (IME can't directly request permissions)
            requestAudioPermission()
            return
        }

        // Start voice recognition
        startVoiceRecognition()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        // IME services cannot directly request runtime permissions
        // Open the app settings activity which can request the permission
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("request_audio_permission", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            voiceInputView?.setState(VoiceInputView.State.ERROR)
            voiceInputView?.setErrorMessage(getString(R.string.voice_permission_required))
        }
    }

    private fun startModelDownload(modelType: VoiceModelType) {
        voiceInputView?.setState(VoiceInputView.State.DOWNLOADING)
        voiceInputView?.setDownloadProgress(0, getString(R.string.voice_download_starting))

        ModelDownloadManager.downloadModel(this, modelType, object : ModelDownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) {
                val progress = ((bytesDownloaded.toFloat() / totalBytes) * 100).toInt()
                val mbDownloaded = bytesDownloaded / 1_000_000
                val mbTotal = totalBytes / 1_000_000
                val message = "$mbDownloaded / $mbTotal MB"

                mainHandler.post {
                    voiceInputView?.setDownloadProgress(progress, message)
                }
            }

            override fun onComplete() {
                mainHandler.post {
                    voiceInputView?.setState(VoiceInputView.State.HIDDEN)
                    // After download complete, check permission and start
                    if (hasAudioPermission()) {
                        startVoiceRecognition()
                    } else {
                        requestAudioPermission()
                    }
                }
            }

            override fun onError(message: String) {
                mainHandler.post {
                    voiceInputView?.setState(VoiceInputView.State.ERROR)
                    voiceInputView?.setErrorMessage(message)
                }
            }
        })
    }

    // Guards against tapping the mic twice while the previous start is still
    // initializing — Qwen3-ASR's 2–5 s bind+load+warmup window otherwise lets
    // a second tap queue another full init in parallel.
    @Volatile
    private var voiceStartInProgress = false

    // One-shot per IME-process lifetime. Once we've kicked off a Qwen3-ASR
    // service pre-bind we don't repeat it on every onStartInputView. Reset
    // implicitly when the IME service is destroyed and recreated.
    @Volatile
    private var voiceServicePreBindAttempted = false

    /**
     * Speculatively bind the :voice process while the user is still typing,
     * so that when they eventually tap the mic the bindService cold start
     * (typically 0.5 – 2 s on the user-visible path) is already paid. Cheap
     * — the bind brings up the process but doesn't load the 700 MB model.
     *
     * Triggered from [onStartInputView] the first time it runs in a given
     * IME lifecycle. No-op if voice is disabled, the selected model isn't
     * Qwen3-ASR, or the model files haven't been downloaded yet (no point
     * spinning up a process that would have nothing to load).
     */
    private fun maybePreBindVoiceService() {
        if (voiceServicePreBindAttempted) return
        if (!ThemeManager.getVoiceInputEnabled(this)) return

        val selectedModel = VoiceModelType.fromId(ThemeManager.getVoiceModelType(this))
        if (selectedModel != VoiceModelType.QWEN3_ASR) return
        if (!ModelDownloadManager.isModelDownloaded(this, selectedModel)) return

        voiceServicePreBindAttempted = true

        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(this)
        }
        voiceInputManager?.prepareForModel(selectedModel)
    }

    private fun startVoiceRecognition() {
        // Debounce: if a start is already in flight, ignore. The Cancel button
        // on the voice overlay is the way out, not a second mic tap.
        if (voiceStartInProgress) return
        voiceStartInProgress = true

        // Show LOADING immediately so the user knows the keyboard responded.
        // The previous flow left the overlay HIDDEN until init finished, which
        // made the first tap look dead during the 2–5 s Qwen3-ASR cold start.
        voiceInputView?.setState(VoiceInputView.State.LOADING)

        // Get the user's selected model type
        val selectedModelType = VoiceModelType.fromId(ThemeManager.getVoiceModelType(this))

        // Initialize voice input manager if needed
        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(this)
        }

        val manager = voiceInputManager ?: run {
            voiceStartInProgress = false
            return
        }

        // Initialize recognizer on background thread. setModelType is included
        // because switching to a different model triggers release()+initialize()
        // internally — for Qwen3-ASR that's bind + 700 MB load + warmup, which
        // would block the IME UI thread for seconds if left on the caller.
        thread {
            try {
                manager.setModelType(selectedModelType)
                val initialized = manager.initialize()

                mainHandler.post {
                    try {
                        if (!initialized) {
                            voiceInputView?.setState(VoiceInputView.State.ERROR)
                            voiceInputView?.setErrorMessage(getString(R.string.voice_init_failed))
                            return@post
                        }

                        // Set up callbacks
                        manager.setOnResultListener { text, isFinal ->
                            mainHandler.post {
                                // Update the transcript display (don't auto-commit).
                                // Interim (pseudo-streaming) results arrive with
                                // isFinal=false and are shown dimmed.
                                voiceInputView?.setTranscript(text, isFinal)
                            }
                        }

                        manager.setOnErrorListener { error ->
                            mainHandler.post {
                                voiceInputView?.setState(VoiceInputView.State.ERROR)
                                voiceInputView?.setErrorMessage(error)
                            }
                        }

                        // Surface per-segment decode state as a "Transcribing…"
                        // hint while still listening, so the gap between speech
                        // end and text (several seconds for Qwen3-ASR) doesn't
                        // look like a frozen keyboard.
                        manager.setOnProcessingStateListener { processing ->
                            mainHandler.post {
                                voiceInputView?.setListeningStatus(processing)
                            }
                        }

                        // Start recording
                        if (manager.startRecording()) {
                            voiceInputView?.setState(VoiceInputView.State.LISTENING)
                        } else {
                            voiceInputView?.setState(VoiceInputView.State.ERROR)
                            voiceInputView?.setErrorMessage(getString(R.string.voice_start_failed))
                        }
                    } finally {
                        voiceStartInProgress = false
                    }
                }
            } catch (e: Exception) {
                // Background thread crashed before posting back — clear the
                // debounce flag so the user can retry.
                mainHandler.post {
                    voiceStartInProgress = false
                    voiceInputView?.setState(VoiceInputView.State.ERROR)
                    voiceInputView?.setErrorMessage(getString(R.string.voice_init_failed))
                }
            }
        }
    }

    /**
     * Close voice input without committing any pending text.
     */
    private fun closeVoiceInput() {
        // stopRecording() does a blocking join on the capture thread (which may
        // be mid-decode for Qwen3-ASR); run it off the UI thread so Cancel never
        // janks the keyboard. Hiding the overlay immediately keeps Cancel feeling
        // instant.
        voiceInputManager?.let { manager -> thread { manager.stopRecording() } }
        voiceInputView?.setState(VoiceInputView.State.HIDDEN)
        // Clear the start-debounce flag so the user can immediately retry if
        // they cancelled while we were still loading. The in-flight init
        // thread will still finish and harmlessly post LISTENING back, which
        // is benign because the voice overlay is hidden again.
        voiceStartInProgress = false
    }

    /**
     * Stop listening immediately and force-transcribe whatever audio is still
     * buffered, then show the result in the transcript field for review. This
     * is the manual escape hatch for noisy environments where automatic
     * endpoint detection never fires and the speech would otherwise stay
     * stranded in the VAD buffer. The text is not inserted yet — the user
     * reviews it and taps Commit (or Reset) from the stopped state.
     */
    private fun finishVoiceInput() {
        val manager = voiceInputManager ?: run {
            closeVoiceInput()
            return
        }
        // Show a processing indicator while the trailing audio is decoded — for
        // Qwen3-ASR this blocking decode can take several seconds.
        voiceInputView?.setState(VoiceInputView.State.PROCESSING)
        manager.finishRecording { finalText ->
            mainHandler.post {
                voiceInputView?.setState(VoiceInputView.State.STOPPED)
                voiceInputView?.setTranscript(finalText)
            }
        }
    }

    /**
     * Clear the pending transcript but keep listening.
     */
    private fun clearVoiceTranscript() {
        voiceInputView?.clearTranscript()
        voiceInputManager?.clearAccumulatedText()
    }

    /**
     * Commit the recognized voice text and close voice input.
     */
    private fun commitVoiceText(text: String) {
        if (text.isNotEmpty()) {
            commitText(text)
        }
        closeVoiceInput()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release voice input resources off the main thread: release() makes a
        // synchronous cross-process releaseModel() Binder call that can block on
        // the :voice recognizerLock for the length of an in-flight decode
        // (several seconds for Qwen3-ASR). Doing it on the UI thread here would
        // be an ANR-class teardown stall. The :voice process memory is reclaimed
        // by the OS regardless.
        voiceInputManager?.let { manager -> thread { manager.release() } }
        voiceInputManager = null
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

        private val EMAIL_DOMAINS = listOf(
            "gmail.com", "protonmail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "icloud.com", "me.com", "live.com", "msn.com"
        )
    }
}
