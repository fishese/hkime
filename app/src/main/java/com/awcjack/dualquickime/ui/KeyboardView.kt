package com.awcjack.dualquickime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.convert.ChineseConverter
import com.awcjack.dualquickime.data.NumericPadSpec
import com.awcjack.dualquickime.data.CalculatorEngine
import com.awcjack.dualquickime.data.EnglishSuggestions
import com.awcjack.dualquickime.data.SwipeTypingDecoder
import com.awcjack.dualquickime.data.sanitizeCandidates
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.util.KeyMapping

/**
 * Custom keyboard view displaying QWERTY layout with Chinese radicals.
 * Includes an embedded candidate bar at the top (Gboard-style).
 * Supports dynamic light/dark theming via ThemeManager.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onKeyPress: ((KeyEvent) -> Unit)? = null
    private var onModeChange: ((Boolean) -> Unit)? = null
    private var onCandidateSelected: ((String) -> Unit)? = null
    private var onEnglishSelected: ((String) -> Unit)? = null
    private var onPageIndicatorClicked: (() -> Unit)? = null
    private var onCandidateRefreshRequested: (() -> Unit)? = null
    private var currentRawKeys: String = ""
    private var isShiftOn = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_INTERVAL = 300L  // milliseconds
    private var isSymbolMode = false
    private var isNumberPadMode = false
    private var numericPadSpec = NumericPadSpec(NumericPadSpec.Kind.INTEGER)
    private var returnToNumberPadFromClipboard = false
    private var calculatorMode = false
    private var calculatorReturnToSymbolPage: Int? = null
    private val calculator = CalculatorEngine()
    private var calculatorDisplay: TextView? = null
    private var retainedCalculatorResult: String? = null
    private var isSensitiveField = false
    private var symbolPage = 0  // 0–4 = symbol pages; 99 = emoji, 100 = clipboard
    private var shiftKey: TextView? = null
    private var modeToggleKey: TextView? = null
    private val letterKeyViews = mutableMapOf<Char, View>()
    private var spaceKeyView: View? = null
    private data class SwipeTouch(
        val fromSpace: Boolean,
        val initialLetter: Char?,
        val centers: Map<Char, SwipeTypingDecoder.Point>,
        val keyWidth: Float,
        val points: MutableList<SwipeTypingDecoder.Point>,
        var active: Boolean = false,
        var deleting: Boolean = false
    )
    private var swipeTouch: SwipeTouch? = null
    private var swipeWords: Collection<String> = EnglishSuggestions.swipeWords()

    fun setSwipeWords(words: Collection<String>) {
        swipeWords = words.ifEmpty { EnglishSuggestions.swipeWords() }
    }

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null

    // Long-press handler for punctuation keys
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false

    // Current theme colors
    private lateinit var colors: KeyboardColors

    // Settings
    private var showComposition = false
    private var candidatesPerPage = 6
    private var candidatePillPaddingDp = 8  // Horizontal padding inside each candidate pill (dp)
    private var keyHeightDp = ThemeManager.KEY_HEIGHT_DEFAULT
    private var candidateTextSizeSp = ThemeManager.CANDIDATE_TEXT_DEFAULT
    private var showKeyRadicals = true
    private var gestureDeleteEnabled = false
    private var swipeTypingEnabled = false

    // Candidate bar components (embedded, Gboard-style)
    private var candidateContainer: LinearLayout? = null
    private var compositionText: TextView? = null
    private var candidateRow: LinearLayout? = null
    private var candidateScroll: HorizontalScrollView? = null
    private var displayedCandidates: List<String> = emptyList()
    private var symbolUtilBar: View? = null
    private var symbolCandidateBar: View? = null
    private var pageIndicator: TextView? = null
    private val candidateSlots = mutableListOf<TextView>()
    private var englishPill: TextView? = null
    private var maskToggle: TextView? = null
    private var onMaskToggled: (() -> Unit)? = null

    // Number row overlay for quick number input when idle
    private var numberRow: LinearLayout? = null
    private val numberSlots = mutableListOf<TextView>()
    private var isNumberRowVisible = true  // Show number row by default when idle

    // Emoji keyboard view (full-featured)
    private var emojiKeyboardView: EmojiKeyboardView? = null
    private var clipboardKeyboardView: ClipboardKeyboardView? = null
    private var mainKeyboardContainer: LinearLayout? = null

    // Candidate grid view (view all candidates)
    private var candidateGridView: CandidateGridView? = null
    private var isCandidateGridMode = false

    // QWERTY layout rows
    private val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    private val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    private val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

    // Number/Symbol rows (page 1) - Gboard "?123" layout
    private val numRow1 = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    private val symRow2Page1 = listOf('@', '#', '$', '&', '-', '+', '*', '/', '(', ')')
    private val symRow3Page1 = listOf('<', '>', '×', '÷', '\'', '!', '?')

    // Utility/math symbols, shown after the common CJK punctuation page.
    private val symRow1Page2 = listOf('~', '`', '|', '•', '√', 'π', '§', '、', '“', '”')
    private val symRow2Page2 = listOf('£', '¢', '€', '¥', '^', '°', '=', '\\', ':', ';')
    private val symRow3Page2 = listOf('％', '‘', '’', '™', '℅', '[', ']')

    // Common Chinese punctuation; bracket variants are candidates.
    private val symRow1Page3 = listOf('「', '」', '，', '。', '：', '；')
    private val symRow2Page3 = listOf('！', '？', '—', '–', '_', '‖')
    private val symRow3Page3 = listOf('¦', '※', '·', '…', '±', '∞')

    // Symbol rows (page 4) - Currency and units
    private val symRow1Page4 = listOf('$', '¥', '€', '£', '¢', '₩', '₹', '฿', '₱', '₽')
    private val symRow2Page4 = listOf('%', '‰', '°', '℃', '℉', '≈', '≠')
    private val symRow3Page4 = listOf('≤', '≥', '∑', '∏', '†', '‡', '"', '\'', '©', '®')

    // Symbol rows (page 5) - Arrows, shapes, and cards
    private val symRow1Page5 = listOf('←', '→', '↑', '↓', '↔', '↕', '⇐', '⇒', '⇑', '⇓')
    private val symRow2Page5 = listOf('▲', '▼', '◀', '▶', '◆', '◇', 'Ω', '■', '△', '∆')
    private val symRow3Page5 = listOf('♠', '♣', '♥', '♦', '★', '╬', '♪')

    init {
        orientation = VERTICAL
        safeRebuild("init")
    }

    /**
     * Load theme colors and settings from ThemeManager.
     */
    private fun loadTheme() {
        colors = ThemeManager.getColors(context)
        showComposition = ThemeManager.getShowComposition(context)
        candidatesPerPage = ThemeManager.getCandidatesPerPage(context)
        candidatePillPaddingDp = ThemeManager.getCandidatePillPadding(context)
        keyHeightDp = ThemeManager.getKeyHeight(context)
        candidateTextSizeSp = ThemeManager.getCandidateTextSize(context)
        showKeyRadicals = ThemeManager.getShowKeyRadicals(context)
        gestureDeleteEnabled = ThemeManager.getGestureDelete(context)
        swipeTypingEnabled = ThemeManager.getSwipeTyping(context)
        setBackgroundColor(colors.keyboardBackground)
        // Key views fill rectangular row cells, including the transparent corners
        // of their rounded backgrounds. Keep outer dead space small as well.
        setPadding(dpToPx(3), 0, dpToPx(3), dpToPx(3))
    }

    /**
     * Refresh the keyboard when theme changes.
     */
    fun refreshTheme() {
        safeRebuild("refreshTheme")
    }

    // If loadTheme or buildKeyboard throws, removeAllViews() has already run and the
    // keyboard window would otherwise render empty. Reset to a known-good letter state
    // and retry once so the user still gets a usable keyboard.
    private fun safeRebuild(reason: String) {
        try {
            loadTheme()
            buildKeyboard()
        } catch (t: Throwable) {
            Log.e("HKIME", "KeyboardView $reason failed; recovering to letter mode", t)
            isSymbolMode = false
            isNumberPadMode = false
            calculatorMode = false
            symbolPage = 0
            isCandidateGridMode = false
            try {
                loadTheme()
                buildKeyboard()
            } catch (t2: Throwable) {
                Log.e("HKIME", "KeyboardView recovery also failed", t2)
            }
        }
    }

    private fun buildKeyboard() {
        removeAllViews()
        letterKeyViews.clear()
        spaceKeyView = null
        swipeTouch = null
        symbolUtilBar = null
        symbolCandidateBar = null

        // Check if we're in candidate grid mode (view all candidates)
        if (isCandidateGridMode) {
            if (candidateGridView == null) {
                candidateGridView = CandidateGridView(context).apply {
                    setOnCandidateSelectedListener { candidate ->
                        onCandidateSelected?.invoke(candidate)
                    }
                    setOnBackPressedListener {
                        isCandidateGridMode = false
                        buildKeyboard()
                        // Request candidate refresh after layout completes
                        this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
                    }
                }
            }
            candidateGridView?.refreshTheme()
            addView(candidateGridView)
            return
        }

        // Check if we're in full emoji mode (page 99 is emoji mode)
        if (isSymbolMode && symbolPage == 99) {
            // Show full-featured emoji keyboard
            if (emojiKeyboardView == null) {
                emojiKeyboardView = EmojiKeyboardView(context).apply {
                    setOnEmojiSelectedListener { emoji ->
                        onKeyPress?.invoke(KeyEvent.Emoji(emoji))
                    }
                    setOnBackspacePressedListener {
                        onKeyPress?.invoke(KeyEvent.Backspace)
                    }
                    setOnAbcPressedListener {
                        isSymbolMode = false
                        symbolPage = 0
                        onModeChange?.invoke(false)
                        buildKeyboard()
                        // Request candidate refresh after layout completes
                        this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
                    }
                }
            }
            emojiKeyboardView?.refreshTheme()
            addView(emojiKeyboardView)
            return
        }

        // Check if we're in clipboard mode (page 100 is clipboard mode)
        if (isSymbolMode && symbolPage == 100) {
            if (clipboardKeyboardView == null) {
                clipboardKeyboardView = ClipboardKeyboardView(context).apply {
                    setOnClipboardItemSelectedListener { text ->
                        onKeyPress?.invoke(KeyEvent.ClipboardPaste(text))
                    }
                    setOnBackspacePressedListener {
                        onKeyPress?.invoke(KeyEvent.Backspace)
                    }
                    setOnAbcPressedListener {
                        if (returnToNumberPadFromClipboard) {
                            returnToNumberPadFromClipboard = false
                            isSymbolMode = false
                            isNumberPadMode = true
                            symbolPage = 0
                            buildKeyboard()
                        } else {
                            isSymbolMode = false
                            symbolPage = 0
                            onModeChange?.invoke(false)
                            buildKeyboard()
                        }
                        // Request candidate refresh after layout completes
                        this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
                    }
                }
            }
            clipboardKeyboardView?.setReturnKeyLabel(if (returnToNumberPadFromClipboard) "123" else "ABC")
            clipboardKeyboardView?.refreshTheme()
            clipboardKeyboardView?.refreshContent()
            addView(clipboardKeyboardView)
            return
        }

        if (isNumberPadMode) {
            clearCandidateViewReferences()
            addView(createNumericUtilBar())
            numericPadSpec.rows.forEach { addView(createNumericRow(it)) }
            return
        }

        if (calculatorMode) {
            clearCandidateViewReferences()
            addView(createCalculatorTopBar())
            listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "-"),
                listOf("±", "0", ".", "+"),
                listOf("C", "⌫", "=", "↩"),
                listOf("Keep", "Insert"),
            ).forEach { addView(createCalculatorRow(it)) }
            return
        }

        // Top bar: candidate bar in letter mode (composition + candidates), or
        // a util-button bar in symbol mode (since composition isn't possible
        // there, the area is otherwise unused).
        if (isSymbolMode) {
            val topBar = FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, candidateBarHeightPx())
            }
            symbolUtilBar = createSymbolUtilBar().apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            topBar.addView(symbolUtilBar)
            symbolCandidateBar = createCandidateBar().apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                visibility = View.GONE
            }
            topBar.addView(symbolCandidateBar)
            addView(topBar)
            when (symbolPage) {
                0 -> {
                    addView(createSymbolRow(numRow1))
                    addView(createSymbolRow(symRow2Page1))
                    addView(createSymbolSpecialRow3(symRow3Page1))
                }
                1 -> {
                    addView(createSymbolRow(symRow1Page3))
                    addView(createSymbolRow(symRow2Page3, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page3))
                }
                2 -> {
                    addView(createSymbolRow(symRow1Page2))
                    addView(createSymbolRow(symRow2Page2, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page2))
                }
                3 -> {
                    addView(createSymbolRow(symRow1Page4))
                    addView(createSymbolRow(symRow2Page4, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page4))
                }
                4 -> {
                    addView(createSymbolRow(symRow1Page5))
                    addView(createSymbolRow(symRow2Page5, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page5))
                }
            }
            addView(createSymbolBottomRow())
        } else {
            addView(createCandidateBar())
            addView(createKeyRow(row1))
            addView(createKeyRow(row2, leftPadding = 0.5f))
            addView(createSpecialRow3())
            addView(createBottomRow())
        }
    }

    // ==================== CANDIDATE BAR ====================

    /**
     * Top bar shown in full symbol mode. The candidate bar is unused there
     * (no Cangjie composition can happen on a digit/symbol keyboard), so we
     * reuse the space for utility buttons that would otherwise crowd the
     * bottom row: emoji, clipboard, Chinese conversion.
     *
     * Candidate-bar field references are nulled out so any stray ?.-guarded
     * call (e.g. clearCandidates) doesn't touch detached views from the
     * previous letter-mode build.
     */
    private fun createSymbolUtilBar(): LinearLayout {
        clearCandidateViewReferences()

        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minimumHeight = dpToPx(46)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

            addView(createUtilBarButton("😀", textSizeSp = 21f) {
                symbolPage = 99
                buildKeyboard()
            })
            addView(createUtilBarButton("📋", textSizeSp = 21f) {
                symbolPage = 100
                buildKeyboard()
            })
            if (!isSensitiveField) addView(createCalculatorButton())
            if (ChineseConverter.isAvailable() && ThemeManager.getChineseConvertEnabled(context)) {
                addView(createUtilBarButton(
                    label = "簡⇄繁",
                    textSizeSp = 18f,
                    onLongClick = { anchor -> showConvertDirectionPopup(anchor) }
                ) {
                    onKeyPress?.invoke(KeyEvent.ConvertChinese(KeyEvent.ConvertDirection.AUTO))
                })
            }
            if (!isSensitiveField) retainedCalculatorResult?.let {
                addView(createRetainedResultPill(it))
            }

            // Trailing spacer keeps buttons left-aligned. Without it the row
            // would distribute children evenly and the buttons would balloon.
            addView(View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    private fun clearCandidateViewReferences() {
        candidateContainer = null
        compositionText = null
        candidateRow = null
        candidateScroll = null
        displayedCandidates = emptyList()
        pageIndicator = null
        englishPill = null
        maskToggle = null
        numberRow = null
        candidateSlots.clear()
        numberSlots.clear()
    }

    private fun createNumericUtilBar(): LinearLayout = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, candidateBarHeightPx())
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(colors.candidateBarBackground)
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        addView(createUtilBarButton("📋", textSizeSp = 21f) {
            returnToNumberPadFromClipboard = true
            isNumberPadMode = false
            isSymbolMode = true
            symbolPage = 100
            buildKeyboard()
        })
        addView(createUtilBarButton("ABC") {
            isNumberPadMode = false
            onModeChange?.invoke(false)
            buildKeyboard()
        })
        if (!numericPadSpec.password && !isSensitiveField) {
            addView(createCalculatorButton())
            retainedCalculatorResult?.let { addView(createRetainedResultPill(it)) }
        }
        addView(View(context).apply { layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f) })
    }

    private fun openCalculator() {
        calculator.clear()
        calculatorReturnToSymbolPage = if (isSymbolMode) symbolPage else null
        calculatorMode = true
        isNumberPadMode = false
        isSymbolMode = false
        buildKeyboard()
    }

    private fun createCalculatorButton(): FrameLayout = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(46), dpToPx(38)).apply {
            setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
        }
        background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
        contentDescription = "Calculator"
        addView(ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(21), dpToPx(21), Gravity.CENTER)
            setImageResource(R.drawable.ic_calculator)
            imageTintList = ColorStateList.valueOf(colors.keyTextPrimary)
        })
        setOnClickListener {
            performKeyHaptic(this)
            openCalculator()
        }
    }

    private fun createRetainedResultPill(value: String): LinearLayout = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
            setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
        }
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
        addView(TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            minWidth = dpToPx(46)
            gravity = Gravity.CENTER
            text = if (value.length > 10) value.take(9) + "…" else value
            textSize = 16f
            setTextColor(colors.keyTextPrimary)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            contentDescription = "Insert result $value"
            setOnClickListener {
                performKeyHaptic(this)
                onKeyPress?.invoke(KeyEvent.CalculatorInsert(value))
            }
        })
        addView(View(context).apply {
            layoutParams = LayoutParams(dpToPx(1), dpToPx(22))
            setBackgroundColor(colors.keyTextSecondary)
        })
        addView(TextView(context).apply {
            layoutParams = LayoutParams(dpToPx(38), LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            text = "×"
            textSize = 17f
            setTextColor(colors.keyTextSecondary)
            contentDescription = "Clear saved result"
            setOnClickListener {
                performKeyHaptic(this)
                retainedCalculatorResult = null
                buildKeyboard()
            }
        })
    }

    private fun returnFromCalculator() {
        calculatorMode = false
        val returnPage = calculatorReturnToSymbolPage
        calculatorReturnToSymbolPage = null
        if (returnPage != null) {
            isSymbolMode = true
            symbolPage = returnPage
        } else {
            isNumberPadMode = true
        }
        buildKeyboard()
    }

    private fun createCalculatorTopBar(): LinearLayout = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(55))
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(colors.specialKeyBackground)
        addView(createUtilBarButton("‹") { returnFromCalculator() })
        addView(TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.25f)
            gravity = Gravity.CENTER
            text = "calculator/計數機"
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(colors.keyTextPrimary)
        })
        val display = TextView(context).apply {
            layoutParams = LayoutParams(0, dpToPx(42), 1.4f).apply {
                setMargins(0, dpToPx(6), dpToPx(6), dpToPx(6))
            }
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            textSize = 24f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setTextColor(colors.keyTextPrimary)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            setPadding(dpToPx(8), 0, dpToPx(14), 0)
            text = calculator.summary
        }
        calculatorDisplay = display
        addView(display)
    }

    private fun createCalculatorRow(labels: List<String>): LinearLayout = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(maxOf(keyHeightDp, 52)))
        orientation = HORIZONTAL
        labels.forEach { label ->
            addView(createSpecialKey(label, 1f) {
                when (label) {
                    in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") -> calculator.digit(label[0])
                    "." -> calculator.decimal()
                    "±" -> calculator.toggleSign()
                    "+", "-", "×", "÷" -> calculator.operator(label[0])
                    "=" -> calculator.equals()
                    "C" -> calculator.clear()
                    "⌫" -> calculator.backspace()
                    "↩" -> returnFromCalculator()
                    "Keep" -> calculator.settledResult()?.let { retainedCalculatorResult = it; returnFromCalculator() }
                    "Insert" -> calculator.settledResult()?.let {
                        onKeyPress?.invoke(KeyEvent.CalculatorInsert(it))
                        returnFromCalculator()
                    }
                }
                calculatorDisplay?.text = calculator.summary
            }.apply { textSize = if (label.length > 1) 15f else 25f })
        }
    }

    private fun createNumericRow(keys: List<NumericPadSpec.Key>): LinearLayout = LinearLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(maxOf(keyHeightDp, 68)))
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        keys.forEach { key ->
            val button = createSpecialKey(key.label, key.weight) {
                when (key.action) {
                    NumericPadSpec.Action.DIGIT -> onKeyPress?.invoke(KeyEvent.Number(key.label.toInt()))
                    NumericPadSpec.Action.SYMBOL -> onKeyPress?.invoke(KeyEvent.Symbol(key.label[0], forceLiteral = true))
                    NumericPadSpec.Action.BACKSPACE -> onKeyPress?.invoke(KeyEvent.Backspace)
                    NumericPadSpec.Action.ENTER -> onKeyPress?.invoke(KeyEvent.Enter)
                    NumericPadSpec.Action.HIDE -> onKeyPress?.invoke(KeyEvent.HideKeyboard)
                    NumericPadSpec.Action.LETTERS -> {
                        isNumberPadMode = false
                        onModeChange?.invoke(false)
                        buildKeyboard()
                    }
                }
            }.apply {
                if (key.action == NumericPadSpec.Action.ENTER) styleEnterGlyph(this)
                else textSize = 26f
            }
            if (key.action == NumericPadSpec.Action.BACKSPACE) {
                setupKeyRepeat(button) { onKeyPress?.invoke(KeyEvent.Backspace) }
            }
            addView(button)
        }
    }

    private fun createUtilBarButton(
        label: String,
        textSizeSp: Float = 16f,
        onLongClick: ((View) -> Unit)? = null,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
                setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = textSizeSp
            typeface = Typeface.DEFAULT_BOLD
            minWidth = dpToPx(46)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            setTextColor(colors.keyTextPrimary)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick(it)
                    true
                }
            }
        }
    }

    private var convertDirectionPopup: PopupWindow? = null

    private fun showConvertDirectionPopup(anchor: View) {
        convertDirectionPopup?.dismiss()

        val popupContent = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            elevation = dpToPx(4).toFloat()
        }

        // Labels use "繁→簡" and "簡→繁" to make direction explicit.
        // Order: Traditional-to-Simplified first since that's the more common
        // direction for Hong Kong / Taiwan users reading Simplified content.
        val options = listOf(
            "繁→簡" to KeyEvent.ConvertDirection.TO_SIMPLIFIED,
            "簡→繁" to KeyEvent.ConvertDirection.TO_TRADITIONAL
        )

        options.forEach { (label, direction) ->
            popupContent.addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(38)
                ).apply { setMargins(dpToPx(3), 0, dpToPx(3), 0) }
                gravity = Gravity.CENTER
                text = label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dpToPx(64)
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                setTextColor(colors.keyTextPrimary)
                background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
                setOnClickListener {
                    onKeyPress?.invoke(KeyEvent.ConvertChinese(direction))
                    convertDirectionPopup?.dismiss()
                }
            })
        }

        convertDirectionPopup = PopupWindow(
            popupContent,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dpToPx(8).toFloat()
            isOutsideTouchable = true
            setOnDismissListener { convertDirectionPopup = null }

            val location = IntArray(2)
            anchor.getLocationInWindow(location)
            popupContent.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth = popupContent.measuredWidth
            val xOffset = location[0] + (anchor.width / 2) - (popupWidth / 2)

            showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                xOffset.coerceAtLeast(dpToPx(4)),
                location[1] - dpToPx(50)
            )
        }
    }

    private fun candidateBarHeightPx(): Int {
        val twoLineTextHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            (candidateTextSizeSp * 2).toFloat(),
            resources.displayMetrics
        ).toInt()
        return maxOf(dpToPx(50), twoLineTextHeight + dpToPx(12))
    }

    private fun createCandidateBar(): FrameLayout {
        candidateSlots.clear()
        numberSlots.clear()
        englishPill = null
        maskToggle = null

        // Both overlays have the same fixed height, so composing never moves the editor.
        val barHeight = candidateBarHeightPx()
        val wrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, barHeight)
        }

        candidateContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, barHeight)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(2), 0, dpToPx(2), 0)
        }

        // Mask toggle button (leftmost, password mode only)
        maskToggle = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(34)).apply {
                setMargins(dpToPx(2), 0, dpToPx(4), 0)
            }
            gravity = Gravity.CENTER
            minWidth = dpToPx(36)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            textSize = 17f
            text = "👁"
            setTextColor(colors.keyTextPrimary)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            visibility = View.GONE
            setOnClickListener { onMaskToggled?.invoke() }
        }
        candidateContainer?.addView(maskToggle)

        // Composition text (radicals display) - only if setting enabled
        compositionText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(10), 0)
            setTextColor(colors.compositionText)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        candidateContainer?.addView(compositionText)

        // Retained for layout compatibility but hidden while English is shown in the editor.
        englishPill = createEnglishPillSlot()
        candidateContainer?.addView(englishPill)

        // The strip holds every choice in order. HorizontalScrollView handles
        // pixel-by-pixel dragging/flinging instead of translating swipes to pages.
        candidateRow = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, barHeight)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        candidateScroll = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, barHeight, 1f)
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(candidateRow)
            setOnScrollChangeListener { _, _, _, _, _ -> updateCandidatePosition() }
        }
        candidateContainer?.addView(candidateScroll)

        // Page indicator (clickable to view all candidates)
        pageIndicator = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setTextColor(colors.pageIndicatorText)
            textSize = 11f
            visibility = View.GONE
            background = createPillBackground(colors.candidateBarBackground, colors.candidatePillBackgroundPressed)

            setOnClickListener {
                performKeyHaptic(this)
                onPageIndicatorClicked?.invoke()
            }
        }
        candidateContainer?.addView(pageIndicator)

        wrapper.addView(candidateContainer)

        // Number row overlay (10 number keys with equal width)
        numberRow = createNumberRowOverlay(barHeight)
        wrapper.addView(numberRow)

        // Show number row by default when keyboard starts
        if (isNumberRowVisible) {
            showNumberRow()
        }

        return wrapper
    }

    private fun createCandidatePillSlot(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, candidateBarHeightPx() - dpToPx(4)).apply {
                setMargins(dpToPx(1), 0, dpToPx(1), 0)
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dpToPx(candidatePillPaddingDp), 0, dpToPx(candidatePillPaddingDp), 0)
            textSize = candidateTextSizeSp.toFloat()
            setTextColor(colors.candidateText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            visibility = View.INVISIBLE  // Hidden by default
            // Allow text wrapping for long phrases
            maxLines = 2
            setLineSpacing(0f, 0.9f)
        }
    }

    private fun createEnglishPillSlot(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(34)).apply {
                setMargins(dpToPx(2), 0, dpToPx(4), 0)
            }
            gravity = Gravity.CENTER
            minWidth = dpToPx(36)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            textSize = 17f
            setTextColor(colors.englishPillText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            visibility = View.GONE  // Hidden by default

            setOnClickListener {
                if (currentRawKeys.isNotEmpty()) {
                    onEnglishSelected?.invoke(currentRawKeys)
                }
            }
        }
    }

    private fun createPillBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        // Drawable-only rounding: each View keeps its full rectangular touch area.
        val cornerRadius = dpToPx(40).toFloat()
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(normalColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ==================== PUBLIC CANDIDATE METHODS ====================

    fun setComposition(radicals: String, rawKeys: String) {
        currentRawKeys = rawKeys

        // English text is displayed at the editor cursor, not in this bar.
        englishPill?.let { pill ->
            // Latin letters are already shown at the editor cursor. Leaving
            // this pill hidden gives Chinese candidates the full bar width.
            pill.visibility = View.GONE
        }

        // Update composition text (only if showComposition is enabled)
        compositionText?.let { tv ->
            if (showComposition && radicals.isNotEmpty()) {
                tv.text = radicals
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    /** Show all choices in a continuously scrollable strip. */
    fun setCandidates(candidates: List<String>) {
        val visibleCandidates = sanitizeCandidates(candidates)
        if (isSymbolMode && symbolCandidateBar != null) {
            symbolUtilBar?.visibility = View.GONE
            symbolCandidateBar?.visibility = View.VISIBLE
        }
        hideNumberRow()
        if (displayedCandidates != visibleCandidates) {
            displayedCandidates = visibleCandidates
            candidateRow?.removeAllViews()
            candidateSlots.clear()
            visibleCandidates.forEach { candidate ->
                val slot = createCandidatePillSlot().apply {
                    text = candidate
                    visibility = View.VISIBLE
                    setOnClickListener {
                        performKeyHaptic(this)
                        onCandidateSelected?.invoke(candidate)
                    }
                }
                candidateSlots.add(slot)
                candidateRow?.addView(slot)
            }
            candidateScroll?.scrollTo(0, 0)
        }
        pageIndicator?.visibility = if (visibleCandidates.size > 1) View.VISIBLE else View.GONE
        updateCandidatePosition()
    }

    private fun updateCandidatePosition() {
        if (displayedCandidates.size <= 1) return
        val scrollX = candidateScroll?.scrollX ?: 0
        val firstVisible = candidateSlots.indexOfFirst { it.right > scrollX }.coerceAtLeast(0)
        val pageSize = candidatesPerPage.coerceAtLeast(1)
        val current = firstVisible / pageSize + 1
        val total = (displayedCandidates.size + pageSize - 1) / pageSize
        pageIndicator?.apply {
            minWidth = (paint.measureText("$total/$total") + paddingLeft + paddingRight).toInt()
            text = "$current/$total"
        }
    }

    private fun clearCandidateStrip() {
        displayedCandidates = emptyList()
        candidateRow?.removeAllViews()
        candidateSlots.clear()
        candidateScroll?.scrollTo(0, 0)
        pageIndicator?.visibility = View.GONE
    }

    fun showNoMatch() {
        // Hide number row when showing no match message
        hideNumberRow()

        clearCandidateStrip()
        createCandidatePillSlot().let { slot ->
            slot.text = "無此字"
            slot.visibility = View.VISIBLE
            slot.setTextColor(colors.noMatchText)
            candidateSlots.add(slot)
            candidateRow?.addView(slot)
        }
    }

    fun clearCandidates() {
        currentRawKeys = ""
        clearCandidateStrip()

        // Close candidate grid if open
        if (isCandidateGridMode) {
            isCandidateGridMode = false
            buildKeyboard()
        }

        // Hide composition
        compositionText?.visibility = View.GONE

        // Hide English pill
        englishPill?.visibility = View.GONE

        // Hide mask toggle
        maskToggle?.visibility = View.GONE

        // Show number row in candidate bar when idle (no code/key being typed)
        showNumberRow()

        if (isSymbolMode) {
            symbolCandidateBar?.visibility = View.GONE
            symbolUtilBar?.visibility = View.VISIBLE
        }

        pageIndicator?.visibility = View.GONE
    }

    /**
     * Create the number row overlay with 10 number keys (1-0) evenly distributed.
     */
    private fun createNumberRowOverlay(barHeight: Int): LinearLayout {
        val numbers = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')

        return LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, barHeight)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1))
            visibility = View.GONE  // Hidden by default

            numbers.forEach { digit ->
                val slot = TextView(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1))
                    }
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                    textSize = 20f
                    setTextColor(colors.candidateText)
                    background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
                    elevation = dpToPx(1).toFloat()
                    text = digit.toString()
                    setupNumberKey(this, digit.digitToInt())
                }
                numberSlots.add(slot)
                addView(slot)
            }
        }
    }

    /**
     * Display number keys (1-0) in the candidate bar when no candidates are shown.
     * This allows quick number input without switching to symbol keyboard.
     */
    fun showNumberRow() {
        isNumberRowVisible = true
        candidateContainer?.visibility = View.GONE
        numberRow?.visibility = View.VISIBLE
    }

    /**
     * Hide the number row and show the candidate bar.
     */
    fun hideNumberRow() {
        isNumberRowVisible = false
        numberRow?.visibility = View.GONE
        candidateContainer?.visibility = View.VISIBLE
    }

    // ==================== KEYBOARD ROWS ====================

    private fun createKeyRow(keys: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            val keyViews = keys.map(::createLetterKey)

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                    // The staggered edge is otherwise a dead touch zone.
                    setOnClickListener { keyViews.first().performClick() }
                })
            }

            keyViews.forEach { addView(it) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                    setOnClickListener { keyViews.last().performClick() }
                })
            }
        }
    }

    private fun createLetterKey(char: Char): LinearLayout {
        val radical = KeyMapping.getRadical(char) ?: ""

        return LinearLayout(context).apply {
            letterKeyViews[char] = this
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            if (showKeyRadicals) {
                addView(TextView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                    gravity = Gravity.CENTER or Gravity.BOTTOM
                    text = radical
                    textSize = 20f
                    setTextColor(colors.keyTextPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                })
                addView(TextView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.65f)
                    gravity = Gravity.CENTER or Gravity.TOP
                    text = char.uppercaseChar().toString()
                    textSize = 12f
                    setTextColor(colors.keyTextSecondary)
                    includeFontPadding = false
                })
            } else {
                addView(TextView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    text = char.uppercaseChar().toString()
                    textSize = 24f
                    setTextColor(colors.keyTextPrimary)
                    includeFontPadding = false
                })
            }

            setOnClickListener {
                performKeyHaptic(this)
                val letter = if (isShiftOn || isCapsLock) char.uppercaseChar() else char
                onKeyPress?.invoke(KeyEvent.Letter(letter))
                // Turn off shift after typing (but not caps lock)
                if (isShiftOn && !isCapsLock) {
                    isShiftOn = false
                    updateShiftState()
                }
            }
        }
    }

    private fun createSpecialRow3(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            shiftKey = createShiftKey {
                val currentTime = System.currentTimeMillis()

                if (isCapsLock) {
                    // If caps lock is on, single tap turns it off
                    isCapsLock = false
                    isShiftOn = false
                } else if (isShiftOn && (currentTime - lastShiftTapTime) < DOUBLE_TAP_INTERVAL) {
                    // Double tap detected - enable caps lock
                    isCapsLock = true
                    isShiftOn = true
                } else {
                    // Single tap - toggle shift
                    isShiftOn = !isShiftOn
                }

                lastShiftTapTime = currentTime
                updateShiftState()
            }
            addView(shiftKey)

            row3.forEach { char -> addView(createLetterKey(char)) }

            addView(createSpecialKey("⌫", 1.5f) {
                onKeyPress?.invoke(KeyEvent.Backspace)
            }.apply {
                setupKeyRepeat(this) {
                    onKeyPress?.invoke(KeyEvent.Backspace)
                }
            })
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            addView(createSpecialKey("⌄", 0.55f) {
                onKeyPress?.invoke(KeyEvent.HideKeyboard)
            })

            modeToggleKey = createSpecialKey("123", 1.45f) {
                // Commit any pending composition as English before switching to number keyboard
                if (currentRawKeys.isNotEmpty()) {
                    onEnglishSelected?.invoke(currentRawKeys)
                }
                isSymbolMode = true
                symbolPage = 0
                onModeChange?.invoke(true)
                buildKeyboard()
            }
            modeToggleKey?.setOnLongClickListener {
                onKeyPress?.invoke(KeyEvent.OpenSettings)
                true
            }
            addView(modeToggleKey)

            addView(createSpecialKeyWithLongPress("，", ',', 1f))

            addView(createSpaceKey())

            addView(createSpecialKeyWithLongPress("。", '.', 1f))

            addView(createEnterKey())
        }
    }

    private fun createSpaceKey(): TextView {
        return TextView(context).apply {
            spaceKeyView = this
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f)
            gravity = Gravity.CENTER
            text = "space"
            textSize = 14f
            setTextColor(colors.keyTextSecondary)
            background = createKeyBackground(colors.spaceKeyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener {
                performKeyHaptic(this)
                onKeyPress?.invoke(KeyEvent.Space)
            }
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            gravity = Gravity.CENTER
            text = label
            textSize = 18f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener {
                performKeyHaptic(this)
                onClick()
            }
        }
    }

    /**
     * Create a special key with long-press variant.
     * Tap outputs defaultLabel[0], long-press outputs longPressChar.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createSpecialKeyWithLongPress(defaultLabel: String, longPressChar: Char, weight: Float): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            gravity = Gravity.CENTER
            text = defaultLabel
            textSize = 18f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        longPressTriggered = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        val runnable = Runnable {
                            longPressTriggered = true
                            // Long-press: output alternate character
                            onKeyPress?.invoke(KeyEvent.Symbol(longPressChar, forceLiteral = true))
                            performKeyHaptic(v, longPress = true)
                        }
                        longPressRunnable = runnable
                        longPressHandler.postDelayed(runnable, LONG_PRESS_DELAY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        if (!longPressTriggered) {
                            // Normal tap: output default character
                            performKeyHaptic(v)
                            onKeyPress?.invoke(KeyEvent.Symbol(defaultLabel[0]))
                        }
                        longPressRunnable = null
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * Create the shift key with bolder styling.
     */
    private fun createShiftKey(onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.8f)
            gravity = Gravity.CENTER
            text = "⇧"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener {
                performKeyHaptic(this)
                onClick()
            }
        }
    }

    private fun createKeyBackground(normalColor: Int, pressedColor: Int): InsetDrawable {
        val cornerRadiusPx = dpToPx(12).toFloat()  // More rounded for modern look
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(normalColor)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        // The drawable is inset, never the View: the entire square key cell is
        // clickable, including every rounded corner and the narrow visual gap.
        return InsetDrawable(states, dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1))
    }

    // ==================== SYMBOL/EMOJI KEYBOARD ====================

    private fun createSymbolRow(chars: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            chars.forEach { char -> addView(createSymbolKey(char)) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createSymbolKey(char: Char): TextView {
        // Check if this character has a half-width equivalent (full-width default)
        val halfWidthChar = FULL_TO_HALF_WIDTH[char]
        // Check if this character has a full-width equivalent (half-width default)
        val fullWidthChar = HALF_TO_FULL_WIDTH[char]

        return if (halfWidthChar != null) {
            // Full-width default, long-press for half-width (e.g. CJK punctuation page)
            createSymbolKeyWithLongPress(char, halfWidthChar)
        } else if (fullWidthChar != null) {
            // Half-width default, long-press for full-width (e.g. Gboard-style pages)
            createSymbolKeyWithLongPress(char, fullWidthChar)
        } else {
            // Regular symbol key
            TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = char.toString()
                textSize = 22f
                setTextColor(colors.keyTextPrimary)
                background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
                elevation = dpToPx(2).toFloat()

                if (char.isDigit()) {
                    setupNumberKey(this, char.digitToInt())
                } else {
                    setOnClickListener {
                        performKeyHaptic(this)
                        onKeyPress?.invoke(KeyEvent.Symbol(char))
                    }
                }
            }
        }
    }

    private fun createEnterKey(): TextView = createSpecialKey("↵", 1.2f) {
        onKeyPress?.invoke(KeyEvent.Enter)
    }.apply { styleEnterGlyph(this) }

    private fun styleEnterGlyph(key: TextView) = key.apply {
        textSize = 36f
        setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        // The ↵ glyph has little ink above its baseline; compensate visually
        // without moving the key itself or changing its touch target.
        setPadding(0, 0, 0, dpToPx(6))
    }

    /** Tap types the number; long-press expands its user-configured phrase. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupNumberKey(view: View, digit: Int) {
        view.setOnTouchListener { touched, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touched.isPressed = true
                    longPressTriggered = false
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        longPressTriggered = true
                        onKeyPress?.invoke(KeyEvent.ShortcutPhrase(digit))
                        performKeyHaptic(touched, longPress = true)
                    }
                    longPressRunnable = runnable
                    longPressHandler.postDelayed(runnable, LONG_PRESS_DELAY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    touched.isPressed = false
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    if (!longPressTriggered) {
                        performKeyHaptic(touched)
                        onKeyPress?.invoke(KeyEvent.Number(digit))
                    }
                    longPressRunnable = null
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    touched.isPressed = false
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    true
                }
                // Keep ownership of the gesture while the pointer is held. Android
                // commonly emits ACTION_MOVE even when the finger has barely moved;
                // returning false here cancels an otherwise valid long-press.
                MotionEvent.ACTION_MOVE -> true
                else -> true
            }
        }
    }

    /**
     * Create a symbol key with long-press variant.
     * Tap outputs defaultChar, long-press outputs longPressChar.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createSymbolKeyWithLongPress(defaultChar: Char, longPressChar: Char): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER
            text = defaultChar.toString()
            textSize = 22f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        longPressTriggered = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        val runnable = Runnable {
                            longPressTriggered = true
                            // Long-press: output alternate character
                            onKeyPress?.invoke(KeyEvent.Symbol(longPressChar, forceLiteral = true))
                            performKeyHaptic(v, longPress = true)
                        }
                        longPressRunnable = runnable
                        longPressHandler.postDelayed(runnable, LONG_PRESS_DELAY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        if (!longPressTriggered) {
                            // Normal tap: output default character
                            performKeyHaptic(v)
                            onKeyPress?.invoke(KeyEvent.Symbol(defaultChar))
                        }
                        longPressRunnable = null
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createSymbolSpecialRow3(chars: List<Char>): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Page button: cycles through pages 1-5
            val totalPages = 5
            val pageLabel = "${symbolPage + 1}/$totalPages"
            addView(createSpecialKey(pageLabel, 1.5f) {
                symbolPage = (symbolPage + 1) % totalPages
                buildKeyboard()
                this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
            })

            chars.forEach { char -> addView(createSymbolKey(char)) }

            addView(createSpecialKey("⌫", 1.5f) {
                onKeyPress?.invoke(KeyEvent.Backspace)
            }.apply {
                setupKeyRepeat(this) {
                    onKeyPress?.invoke(KeyEvent.Backspace)
                }
            })
        }
    }

    private fun createSymbolBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyHeightDp))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            addView(createSpecialKey("⌄", 0.55f) {
                onKeyPress?.invoke(KeyEvent.HideKeyboard)
            })

            addView(createSpecialKey("ABC", 1.45f) {
                isSymbolMode = false
                symbolPage = 0
                onModeChange?.invoke(false)
                buildKeyboard()
                // Request candidate refresh after layout completes
                this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
            })

            // Emoji, clipboard, and 簡⇄繁 conversion buttons live
            // in the symbol-mode util bar (above the digit row) — see
            // createSymbolUtilBar — so the bottom row is only mode-toggle and
            // punctuation/whitespace.

            addView(createSpecialKeyWithLongPress(",", '，', 1f))

            addView(createSpaceKey())

            addView(createSpecialKeyWithLongPress(".", '。', 1f))

            addView(createEnterKey())
        }
    }

    private fun updateShiftState() {
        shiftKey?.apply {
            when {
                isCapsLock -> {
                    // Caps lock: filled arrow with underline, accent color
                    text = "⇧̲"  // Shift with combining underline
                    setTextColor(colors.compositionText)
                    background = createKeyBackground(colors.compositionText, colors.specialKeyBackgroundPressed)
                    setTextColor(colors.keyBackground)  // Inverted color for visibility
                }
                isShiftOn -> {
                    // Shift on: accent color
                    text = "⇧"
                    setTextColor(colors.compositionText)
                    background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
                }
                else -> {
                    // Shift off: normal
                    text = "⇧"
                    setTextColor(colors.keyTextPrimary)
                    background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
                }
            }
        }
    }

    // ==================== PUBLIC LISTENERS ====================

    fun setOnKeyPressListener(listener: (KeyEvent) -> Unit) {
        onKeyPress = listener
    }

    fun setOnModeChangeListener(listener: (Boolean) -> Unit) {
        onModeChange = listener
    }

    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    fun setOnEnglishSelectedListener(listener: (String) -> Unit) {
        onEnglishSelected = listener
    }

    fun setOnPageIndicatorClickedListener(listener: () -> Unit) {
        onPageIndicatorClicked = listener
    }

    fun setOnCandidateRefreshRequestedListener(listener: () -> Unit) {
        onCandidateRefreshRequested = listener
    }

    fun setOnMaskToggleListener(listener: () -> Unit) {
        onMaskToggled = listener
    }

    /**
     * Show or hide the password-mask toggle button.
     * [isMasked] true = currently showing stars (tap to reveal).
     * The button background is highlighted when the user has revealed their input.
     */
    fun setMaskToggle(show: Boolean, isMasked: Boolean) {
        maskToggle?.let { toggle ->
            if (show) {
                toggle.text = "👁"
                if (isMasked) {
                    toggle.background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
                    toggle.setTextColor(colors.keyTextPrimary)
                } else {
                    toggle.background = createPillBackground(colors.compositionText, colors.candidatePillBackgroundPressed)
                    toggle.setTextColor(colors.keyBackground)
                }
                toggle.visibility = View.VISIBLE
            } else {
                toggle.visibility = View.GONE
            }
        }
    }

    /**
     * Clear candidate slots without touching the English pill or mask toggle.
     * Used in password mode when there are no Chinese candidates to display.
     */
    fun clearCandidateSlotsOnly() {
        hideNumberRow()
        clearCandidateStrip()
    }

    /**
     * Show the full-screen candidate grid view with all candidates.
     * @param allCandidates The full list of candidates.
     * @param initialPage The page to display initially (0-based, in terms of grid pages).
     */
    fun showCandidateGrid(allCandidates: List<String>, initialPage: Int = 0) {
        isCandidateGridMode = true
        buildKeyboard()
        candidateGridView?.setCandidates(allCandidates, initialPage)
    }

    /**
     * Close the candidate grid and return to normal keyboard view.
     */
    fun closeCandidateGrid() {
        if (isCandidateGridMode) {
            isCandidateGridMode = false
            buildKeyboard()
        }
    }

    fun setLetterMode() {
        if (isSymbolMode || isNumberPadMode || calculatorMode || isCandidateGridMode) {
            isSymbolMode = false
            isNumberPadMode = false
            calculatorMode = false
            symbolPage = 0
            isCandidateGridMode = false
            buildKeyboard()
        }
    }

    /**
     * Switch to the ordinary five-page symbol keyboard (page 0 has a digit row).
     * Numeric fields instead use [setNumberPadMode].
     */
    fun setSymbolMode() {
        if (!isSymbolMode || symbolPage != 0 || isCandidateGridMode) {
            isSymbolMode = true
            isNumberPadMode = false
            calculatorMode = false
            symbolPage = 0
            isCandidateGridMode = false
            buildKeyboard()
        }
    }

    /**
     * Clear shift / caps-lock so the keyboard returns to lowercase.
     * Called by the service on input-view start so caps state from a
     * previous field doesn't leak into a fresh one.
     */
    fun resetShiftState() {
        if (!isShiftOn && !isCapsLock) return
        isShiftOn = false
        isCapsLock = false
        lastShiftTapTime = 0L
        updateShiftState()
    }

    /**
     * Set up touch-based key repeat for a key (used for backspace).
     * When held down, the action fires immediately on press, then repeats after an initial delay.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyRepeat(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    performKeyHaptic(v)
                    action()
                    backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
                    val runnable = object : Runnable {
                        override fun run() {
                            action()
                            backspaceHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL)
                        }
                    }
                    backspaceRepeatRunnable = runnable
                    backspaceHandler.postDelayed(runnable, BACKSPACE_INITIAL_DELAY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
                    backspaceRepeatRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    /** Watch the entire keyboard so a glide can cross child key views. Ordinary taps
     * still go through their existing listeners; only a confirmed swipe cancels them. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isSymbolMode || isNumberPadMode || calculatorMode || isCandidateGridMode || isSensitiveField ||
            (!gestureDeleteEnabled && !swipeTypingEnabled)) {
            return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTouch = startSwipeTouch(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val touch = swipeTouch ?: return super.dispatchTouchEvent(event)
                for (index in 0 until event.historySize) {
                    touch.points.add(SwipeTypingDecoder.Point(event.getHistoricalX(index), event.getHistoricalY(index)))
                }
                touch.points.add(SwipeTypingDecoder.Point(event.x, event.y))
                if (!touch.active && shouldActivateSwipe(touch)) {
                    touch.active = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                if (touch.active) return true
            }
            MotionEvent.ACTION_UP -> {
                val touch = swipeTouch
                swipeTouch = null
                if (touch?.active == true) {
                    touch.points.add(SwipeTypingDecoder.Point(event.x, event.y))
                    if (touch.deleting) {
                        performKeyHaptic(this)
                        onKeyPress?.invoke(KeyEvent.SwipeDelete)
                    } else {
                        val words = if (ThemeManager.getMethodEnglish(context))
                            swipeWords else emptySet()
                        val result = SwipeTypingDecoder.decode(touch.points, touch.centers,
                            touch.keyWidth, words)
                        if (result != null) {
                            onKeyPress?.invoke(KeyEvent.SwipeCode(result.code, result.words))
                        } else {
                            touch.initialLetter?.let { onKeyPress?.invoke(KeyEvent.Letter(it)) }
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                swipeTouch = null
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun startSwipeTouch(event: MotionEvent): SwipeTouch? {
        val rootLocation = IntArray(2)
        getLocationOnScreen(rootLocation)
        fun center(view: View): SwipeTypingDecoder.Point {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return SwipeTypingDecoder.Point(
                location[0] - rootLocation[0] + view.width / 2f,
                location[1] - rootLocation[1] + view.height / 2f
            )
        }
        val centers = letterKeyViews.mapValues { (_, view) -> center(view) }
        val keyWidth = letterKeyViews.values.firstOrNull()?.width?.toFloat() ?: return null
        if (keyWidth <= 0f) return null
        val position = SwipeTypingDecoder.Point(event.x, event.y)
        val fromSpace = spaceKeyView?.let { view ->
            val middle = center(view)
            kotlin.math.abs(position.x - middle.x) <= view.width / 2f &&
                kotlin.math.abs(position.y - middle.y) <= view.height / 2f
        } == true
        val letter = centers.minByOrNull { (_, point) ->
            kotlin.math.hypot(position.x - point.x, position.y - point.y)
        }?.takeIf { (_, point) ->
            kotlin.math.abs(position.x - point.x) <= keyWidth * 0.7f &&
                kotlin.math.abs(position.y - point.y) <= keyHeightDp.let(::dpToPx) * 0.5f
        }?.key
        if ((!fromSpace || !gestureDeleteEnabled) && letter == null) return null
        return SwipeTouch(fromSpace, letter, centers, keyWidth, mutableListOf(position))
    }

    private fun shouldActivateSwipe(touch: SwipeTouch): Boolean {
        val start = touch.points.first()
        val end = touch.points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val threshold = maxOf(dpToPx(34).toFloat(), touch.keyWidth * 0.7f)
        val left = dx < -threshold && kotlin.math.abs(dy) < touch.keyWidth * 0.7f
        if (gestureDeleteEnabled && left &&
            (touch.fromSpace || !swipeTypingEnabled)) {
            touch.deleting = true
            return true
        }
        return !touch.fromSpace && touch.initialLetter != null &&
            swipeTypingEnabled &&
            kotlin.math.hypot(dx, dy) > threshold
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        swipeTouch = null
        backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        convertDirectionPopup?.dismiss()
        convertDirectionPopup = null
    }

    fun setNumberPadMode(spec: NumericPadSpec) {
        numericPadSpec = spec
        isNumberPadMode = true
        isSymbolMode = false
        calculatorMode = false
        isCandidateGridMode = false
        returnToNumberPadFromClipboard = false
        retainedCalculatorResult = null
        calculator.clear()
        buildKeyboard()
    }

    fun setSensitiveField(sensitive: Boolean) {
        isSensitiveField = sensitive
        if (sensitive) retainedCalculatorResult = null
    }

    fun isNumberPadMode(): Boolean = isNumberPadMode

    private fun performKeyHaptic(view: View, longPress: Boolean = false) {
        if (!ThemeManager.getHapticFeedbackEnabled(context)) return
        view.performHapticFeedback(
            if (longPress) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    sealed class KeyEvent {
        data class Letter(val char: Char) : KeyEvent()
        data class SwipeCode(val code: String, val englishWords: List<String>) : KeyEvent()
        object SwipeDelete : KeyEvent()
        data class Number(val digit: Int) : KeyEvent()
        data class ShortcutPhrase(val digit: Int) : KeyEvent()
        data class Symbol(val char: Char, val forceLiteral: Boolean = false) : KeyEvent()
        data class Emoji(val emoji: String) : KeyEvent()
        data class ClipboardPaste(val text: String) : KeyEvent()
        data class CalculatorInsert(val text: String) : KeyEvent()
        object Space : KeyEvent()
        object Backspace : KeyEvent()
        object Enter : KeyEvent()
        object HideKeyboard : KeyEvent()
        object OpenSettings : KeyEvent()
        // Tap = AUTO (detect from content). Long-press popup lets the user
        // force a specific direction regardless of what the text looks like.
        data class ConvertChinese(val direction: ConvertDirection = ConvertDirection.AUTO) : KeyEvent()

        enum class ConvertDirection { AUTO, TO_SIMPLIFIED, TO_TRADITIONAL }
    }

    companion object {
        private const val BACKSPACE_INITIAL_DELAY = 400L  // ms before first repeat
        private const val BACKSPACE_REPEAT_INTERVAL = 50L  // ms between subsequent repeats
        private const val LONG_PRESS_DELAY = 300L  // ms before long-press triggers

        // Half-width to full-width punctuation mapping
        // Used in symbol keyboard: tap half-width, long-press full-width
        private val HALF_TO_FULL_WIDTH = mapOf(
            '(' to '（',
            ')' to '）',
            '!' to '！',
            '?' to '？',
            ':' to '：',
            ';' to '；',
            ',' to '，',
            '.' to '。',
            '%' to '％',
            '“' to '「',
            '”' to '」',
            '‘' to '『',
            '’' to '』',
        )

        // Full-width to half-width punctuation mapping
        // Long-press on full-width outputs half-width equivalent
        private val FULL_TO_HALF_WIDTH = mapOf(
            '，' to ',',   // Ideographic comma -> ASCII comma
            '。' to '.',   // Ideographic period -> ASCII period
            '！' to '!',   // Fullwidth exclamation -> ASCII exclamation
            '？' to '?',   // Fullwidth question -> ASCII question
            '：' to ':',   // Fullwidth colon -> ASCII colon
            '；' to ';',   // Fullwidth semicolon -> ASCII semicolon
            '「' to '"',   // Left corner bracket -> ASCII double quote
            '」' to '"',   // Right corner bracket -> ASCII double quote
            '『' to '\'',  // Left white corner bracket -> ASCII single quote
            '』' to '\'',  // Right white corner bracket -> ASCII single quote
            '【' to '[',   // Left black lenticular bracket -> ASCII left bracket
            '】' to ']',   // Right black lenticular bracket -> ASCII right bracket
            '（' to '(',   // Fullwidth left parenthesis -> ASCII left parenthesis
            '）' to ')',   // Fullwidth right parenthesis -> ASCII right parenthesis
            '《' to '<',   // Left double angle bracket -> ASCII less-than
            '》' to '>',   // Right double angle bracket -> ASCII greater-than
            '〈' to '<',   // Left angle bracket -> ASCII less-than
            '〉' to '>',   // Right angle bracket -> ASCII greater-than
            '＜' to '<',   // Fullwidth less-than -> ASCII less-than
            '＞' to '>',   // Fullwidth greater-than -> ASCII greater-than
            '％' to '%',
        )
    }
}
