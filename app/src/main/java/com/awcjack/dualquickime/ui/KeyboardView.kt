package com.awcjack.dualquickime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.convert.ChineseConverter
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
    private var onCandidateSwipe: ((Boolean) -> Unit)? = null
    private var onCandidateRefreshRequested: (() -> Unit)? = null
    private var currentRawKeys: String = ""
    private var isShiftOn = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_INTERVAL = 300L  // milliseconds
    private var isSymbolMode = false
    private var symbolPage = 0  // 0 = numbers, 1 = symbols, 2 = emoji
    private var shiftKey: TextView? = null
    private var modeToggleKey: TextView? = null

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
    private var showComposition = true
    private var candidatesPerPage = 6
    private var candidatePillPaddingDp = 8  // Horizontal padding inside each candidate pill (dp)
    private var keyHeightDp = ThemeManager.KEY_HEIGHT_DEFAULT
    private var candidateTextSizeSp = ThemeManager.CANDIDATE_TEXT_DEFAULT
    private var showKeyRadicals = true

    // Candidate bar components (embedded, Gboard-style) - now with fixed slots
    private var candidateContainer: LinearLayout? = null
    private var compositionText: TextView? = null
    private var candidateRow: LinearLayout? = null
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
    private val symRow2Page1 = listOf('@', '#', '$', '&', '-', '+', '(', ')', '/')
    private val symRow3Page1 = listOf('、', '“', '”', ':', ';', '!', '?')

    // Symbol rows (page 2) - Gboard "=\<" layout
    private val symRow1Page2 = listOf('~', '`', '|', '•', '√', 'π', '÷', '×', '*', '§')
    private val symRow2Page2 = listOf('£', '¢', '€', '¥', '^', '°', '=', '{', '}', '\\')
    private val symRow3Page2 = listOf('％', '‘', '’', '™', '℅', '[', ']')

    // Symbol rows (page 3) - Chinese brackets and CJK punctuation
    private val symRow1Page3 = listOf('「', '」', '『', '』', '【', '】', '（', '）', '〈', '〉')
    private val symRow2Page3 = listOf('《', '》', '，', '。', '：', '；', '！', '？', '…')
    private val symRow3Page3 = listOf('—', '–', '_', '‖', '¦', '※', '·')

    // Symbol rows (page 4) - Currency and units
    private val symRow1Page4 = listOf('$', '¥', '€', '£', '¢', '₩', '₹', '฿', '₱', '₽')
    private val symRow2Page4 = listOf('%', '‰', '°', '℃', '℉', '±', '∞', '≈', '≠')
    private val symRow3Page4 = listOf('≤', '≥', '∑', '∏', '†', '‡', '"', '\'', '©', '®')

    // Symbol rows (page 5) - Arrows, shapes, and cards
    private val symRow1Page5 = listOf('←', '→', '↑', '↓', '↔', '↕', '⇐', '⇒', '⇑', '⇓')
    private val symRow2Page5 = listOf('▲', '▼', '◀', '▶', '◆', '◇', '□', '■', '△', '∆')
    private val symRow3Page5 = listOf('♠', '♣', '♥', '♦', '★', '☆', '♪')

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
        setBackgroundColor(colors.keyboardBackground)
        setPadding(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(8))
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
            Log.e("DualQuickIME", "KeyboardView $reason failed; recovering to letter mode", t)
            isSymbolMode = false
            symbolPage = 0
            isCandidateGridMode = false
            try {
                loadTheme()
                buildKeyboard()
            } catch (t2: Throwable) {
                Log.e("DualQuickIME", "KeyboardView recovery also failed", t2)
            }
        }
    }

    private fun buildKeyboard() {
        removeAllViews()

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
                        isSymbolMode = false
                        symbolPage = 0
                        onModeChange?.invoke(false)
                        buildKeyboard()
                        // Request candidate refresh after layout completes
                        this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
                    }
                }
            }
            clipboardKeyboardView?.refreshTheme()
            clipboardKeyboardView?.refreshContent()
            addView(clipboardKeyboardView)
            return
        }

        // Top bar: candidate bar in letter mode (composition + candidates), or
        // a util-button bar in symbol mode (since composition isn't possible
        // there, the area is otherwise unused).
        if (isSymbolMode) {
            addView(createSymbolUtilBar())
            when (symbolPage) {
                0 -> {
                    addView(createSymbolRow(numRow1))
                    addView(createSymbolRow(symRow2Page1, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page1))
                }
                1 -> {
                    addView(createSymbolRow(symRow1Page2))
                    addView(createSymbolRow(symRow2Page2, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page2))
                }
                2 -> {
                    addView(createSymbolRow(symRow1Page3))
                    addView(createSymbolRow(symRow2Page3, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page3))
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
     * Top bar shown in symbol/number mode. The candidate bar is unused there
     * (no Cangjie composition can happen on a digit/symbol keyboard), so we
     * reuse the space for utility buttons that would otherwise crowd the
     * bottom row: emoji, clipboard, Chinese conversion, voice input.
     *
     * Candidate-bar field references are nulled out so any stray ?.-guarded
     * call (e.g. clearCandidates) doesn't touch detached views from the
     * previous letter-mode build.
     */
    private fun createSymbolUtilBar(): LinearLayout {
        candidateContainer = null
        compositionText = null
        candidateRow = null
        pageIndicator = null
        englishPill = null
        maskToggle = null
        numberRow = null
        candidateSlots.clear()
        numberSlots.clear()

        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minimumHeight = dpToPx(46)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

            addView(createUtilBarButton("😀") {
                symbolPage = 99
                buildKeyboard()
            })
            addView(createUtilBarButton("📋") {
                symbolPage = 100
                buildKeyboard()
            })
            if (ChineseConverter.isAvailable() && ThemeManager.getChineseConvertEnabled(context)) {
                addView(createUtilBarButton(
                    label = "簡⇄繁",
                    onLongClick = { anchor -> showConvertDirectionPopup(anchor) }
                ) {
                    onKeyPress?.invoke(KeyEvent.ConvertChinese(KeyEvent.ConvertDirection.AUTO))
                })
            }
            if (BuildConfig.VOICE_INPUT_ENABLED && ThemeManager.getVoiceInputEnabled(context)) {
                addView(createUtilBarButton("🎤") {
                    onKeyPress?.invoke(KeyEvent.VoiceInput)
                })
            }

            // Trailing spacer keeps buttons left-aligned. Without it the row
            // would distribute children evenly and the buttons would balloon.
            addView(View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    private fun createUtilBarButton(
        label: String,
        onLongClick: ((View) -> Unit)? = null,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
                setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 16f
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

    private fun createCandidateBar(): FrameLayout {
        candidateSlots.clear()
        numberSlots.clear()
        englishPill = null
        maskToggle = null

        // Use FrameLayout as wrapper to allow overlaying number row on candidate row
        val wrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        candidateContainer = CandidateSwipeContainer(context).apply {
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minimumHeight = dpToPx(maxOf(32, candidateTextSizeSp + 6))
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

        // Candidate row with flexible width distribution
        candidateRow = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Create fixed number of candidate slots
        for (i in 0 until candidatesPerPage) {
            val slot = createCandidatePillSlot()
            candidateSlots.add(slot)
            candidateRow?.addView(slot)
        }
        candidateContainer?.addView(candidateRow)

        // Page indicator (clickable to view all candidates)
        pageIndicator = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setTextColor(colors.pageIndicatorText)
            textSize = 11f
            visibility = View.GONE
            background = createPillBackground(colors.candidateBarBackground, colors.candidatePillBackgroundPressed)

            setOnClickListener {
                onPageIndicatorClicked?.invoke()
            }
        }
        candidateContainer?.addView(pageIndicator)

        wrapper.addView(candidateContainer)

        // Number row overlay (10 number keys with equal width)
        numberRow = createNumberRowOverlay()
        wrapper.addView(numberRow)

        // Show number row by default when keyboard starts
        if (isNumberRowVisible) {
            showNumberRow()
        }

        return wrapper
    }

    /** Intercept horizontal drags without stealing ordinary candidate taps. */
    private inner class CandidateSwipeContainer(context: Context) : LinearLayout(context) {
        private var startX = 0f
        private var startY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (kotlin.math.abs(dx) > dpToPx(22) && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        dragging = true
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    if (dragging || kotlin.math.abs(dx) > dpToPx(22)) {
                        onCandidateSwipe?.invoke(dx < 0)
                    }
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }

    private fun createCandidatePillSlot(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(1), 0, dpToPx(1), 0)
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dpToPx(candidatePillPaddingDp), dpToPx(1), dpToPx(candidatePillPaddingDp), dpToPx(1))
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
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
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

    /**
     * Set candidates to display in the candidate bar.
     * Measures text width and only displays candidates that fit; remaining
     * candidates roll forward to the next page via the returned displayed
     * count, and the page indicator is updated with a corrected total based
     * on what actually fit (the original totalPages argument is only an
     * upper-bound estimate — overflow makes the real count larger).
     *
     * @param candidates List of candidates to display on this page
     * @param currentPage Current page number (1-based for display)
     * @param totalCandidates Total candidates across all pages (>= candidates.size + startOffset)
     * @param startOffset Index in the full candidate list where [candidates] begins
     * @return Number of candidates actually displayed (may be less than candidates.size if they don't fit)
     */
    fun setCandidates(
        candidates: List<String>,
        currentPage: Int,
        totalCandidates: Int,
        startOffset: Int
    ): Int {
        // Hide number row when showing candidates
        hideNumberRow()

        // Pre-set page indicator text with a worst-case-width estimate so the
        // width reservation below is enough even if the real total grows after
        // overflow. We pick the larger of the naive estimate and currentPage+1
        // so we always reserve at least 2 digits for the total.
        val naiveTotalEstimate = if (candidates.isEmpty()) currentPage
            else (totalCandidates + candidates.size - 1) / maxOf(candidates.size, 1)
        val measurementTotal = maxOf(naiveTotalEstimate, currentPage + 1)
        val showPageIndicator = totalCandidates > candidates.size || currentPage > 1
        pageIndicator?.let { pi ->
            if (showPageIndicator) {
                pi.text = "$currentPage/$measurementTotal"
                pi.visibility = View.VISIBLE
            } else {
                pi.visibility = View.GONE
            }
        }

        val availableWidth = computeAvailableCandidateWidth(showPageIndicator)

        var usedWidth = 0
        var displayedCount = 0
        val horizontalMargin = dpToPx(1) * 2   // slot left + right margin

        candidateSlots.forEachIndexed { index, slot ->
            if (index < candidates.size) {
                val candidate = candidates[index]
                slot.text = candidate
                slot.setTextColor(colors.candidateText)
                slot.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val textWidth = slot.measuredWidth + horizontalMargin

                // Fit check: always show at least one candidate, even if it's wider than the row,
                // so the user can still page forward. Otherwise require the pill to fit fully;
                // remaining candidates roll over to the next page via displayedCount.
                if (displayedCount == 0 || usedWidth + textWidth <= availableWidth) {
                    slot.visibility = View.VISIBLE
                    slot.setOnClickListener {
                        onCandidateSelected?.invoke(candidate)
                    }
                    usedWidth += textWidth
                    displayedCount++
                } else {
                    slot.text = ""
                    slot.visibility = View.INVISIBLE
                    slot.setOnClickListener(null)
                }
            } else {
                slot.text = ""
                slot.visibility = View.INVISIBLE
                slot.setOnClickListener(null)
            }
        }

        // Now we know how many actually fit on this page — recompute the total
        // page count using displayedCount as the per-page estimate for the
        // remaining candidates. Without this, a page that overflowed would
        // still show the original "1/2" even though the spillover plus future
        // pages mean the real total is larger.
        if (showPageIndicator) {
            val remaining = (totalCandidates - startOffset - displayedCount).coerceAtLeast(0)
            val perPage = displayedCount.coerceAtLeast(1)
            val remainingPages = (remaining + perPage - 1) / perPage
            val actualTotal = (currentPage + remainingPages).coerceAtLeast(currentPage)
            pageIndicator?.text = "$currentPage/$actualTotal"
        }

        return displayedCount
    }

    /**
     * Available width inside the candidate row. Computed from the candidate container minus
     * the measured widths of its other visible children (composition text, english pill,
     * page indicator) because candidateRow.width is stale immediately after setComposition()
     * toggles sibling visibility — layout hasn't run yet, so the row still reports its old
     * size and we'd overestimate space and let pills overflow past the right edge.
     */
    private fun computeAvailableCandidateWidth(pageIndicatorVisible: Boolean): Int {
        val container = candidateContainer
        val containerWidth = when {
            container != null && container.width > 0 -> container.width
            this.width > 0 -> this.width - paddingLeft - paddingRight
            else -> context.resources.displayMetrics.widthPixels
        }
        var used = (container?.paddingLeft ?: 0) + (container?.paddingRight ?: 0)

        fun addSibling(v: View?) {
            val view = v ?: return
            if (view.visibility != View.VISIBLE) return
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val lp = view.layoutParams as? MarginLayoutParams
            used += view.measuredWidth + (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
        }

        addSibling(maskToggle)
        addSibling(compositionText)
        addSibling(englishPill)
        if (pageIndicatorVisible) addSibling(pageIndicator)

        // Small buffer to absorb rounding between measure and layout.
        used += dpToPx(2)

        return (containerWidth - used).coerceAtLeast(dpToPx(80))
    }

    fun showNoMatch() {
        // Hide number row when showing no match message
        hideNumberRow()

        // Clear all candidate slots
        candidateSlots.forEach { slot ->
            slot.text = ""
            slot.visibility = View.INVISIBLE
            slot.setOnClickListener(null)
        }

        // Show "無此字" in first slot
        candidateSlots.firstOrNull()?.let { slot ->
            slot.text = "無此字"
            slot.visibility = View.VISIBLE
            slot.setTextColor(colors.noMatchText)
            slot.setOnClickListener(null)
        }

        pageIndicator?.visibility = View.GONE
    }

    fun clearCandidates() {
        currentRawKeys = ""

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

        pageIndicator?.visibility = View.GONE
    }

    /**
     * Create the number row overlay with 10 number keys (1-0) evenly distributed.
     */
    private fun createNumberRowOverlay(): LinearLayout {
        val numbers = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')

        return LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minimumHeight = dpToPx(maxOf(46, candidateTextSizeSp + 24))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            visibility = View.GONE  // Hidden by default

            numbers.forEach { digit ->
                val slot = TextView(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                    }
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
                    textSize = 18f
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

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            keys.forEach { char -> addView(createLetterKey(char)) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createLetterKey(char: Char): LinearLayout {
        val radical = KeyMapping.getRadical(char) ?: ""

        return LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
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

            modeToggleKey = createSpecialKey("123", 1.2f) {
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

            // Voice input button (only in full version)
            if (BuildConfig.VOICE_INPUT_ENABLED && ThemeManager.getVoiceInputEnabled(context)) {
                addView(createSpecialKey("🎤", 1f) {
                    onKeyPress?.invoke(KeyEvent.VoiceInput)
                })
            }

            addView(createSpaceKey())

            addView(createSpecialKeyWithLongPress("。", '.', 1f))

            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun createSpaceKey(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = "space"
            textSize = 14f
            setTextColor(colors.keyTextSecondary)
            background = createKeyBackground(colors.spaceKeyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener { onKeyPress?.invoke(KeyEvent.Space) }
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 18f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener { onClick() }
        }
    }

    /**
     * Create a special key with long-press variant.
     * Tap outputs defaultLabel[0], long-press outputs longPressChar.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createSpecialKeyWithLongPress(defaultLabel: String, longPressChar: Char, weight: Float): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
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
                            onKeyPress?.invoke(KeyEvent.Symbol(longPressChar))
                            if (ThemeManager.getHapticFeedbackEnabled(context)) {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            }
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
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.8f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = "⇧"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener { onClick() }
        }
    }

    private fun createKeyBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
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
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
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
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
                }
                gravity = Gravity.CENTER
                text = char.toString()
                textSize = 22f
                setTextColor(colors.keyTextPrimary)
                background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
                elevation = dpToPx(2).toFloat()

                if (char.isDigit()) {
                    setupNumberKey(this, char.digitToInt())
                } else {
                    setOnClickListener { onKeyPress?.invoke(KeyEvent.Symbol(char)) }
                }
            }
        }
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
                        if (ThemeManager.getHapticFeedbackEnabled(context)) {
                            touched.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    longPressRunnable = runnable
                    longPressHandler.postDelayed(runnable, LONG_PRESS_DELAY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    touched.isPressed = false
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    if (!longPressTriggered) onKeyPress?.invoke(KeyEvent.Number(digit))
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
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
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
                            onKeyPress?.invoke(KeyEvent.Symbol(longPressChar))
                            // Provide haptic feedback if enabled
                            if (ThemeManager.getHapticFeedbackEnabled(context)) {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            }
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

            addView(createSpecialKey("ABC", 1.2f) {
                isSymbolMode = false
                symbolPage = 0
                onModeChange?.invoke(false)
                buildKeyboard()
                // Request candidate refresh after layout completes
                this@KeyboardView.post { onCandidateRefreshRequested?.invoke() }
            })

            // Emoji, clipboard, 簡⇄繁 conversion, and voice input buttons live
            // in the symbol-mode util bar (above the digit row) — see
            // createSymbolUtilBar — so the bottom row is only mode-toggle and
            // punctuation/whitespace.

            addView(createSpecialKeyWithLongPress(",", '，', 1f))

            addView(createSpaceKey())

            addView(createSpecialKeyWithLongPress(".", '。', 1f))

            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
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

    fun setOnCandidateSwipeListener(listener: (forward: Boolean) -> Unit) {
        onCandidateSwipe = listener
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
        candidateSlots.forEach { slot ->
            slot.text = ""
            slot.visibility = View.INVISIBLE
            slot.setOnClickListener(null)
        }
        pageIndicator?.visibility = View.GONE
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
        if (isSymbolMode || isCandidateGridMode) {
            isSymbolMode = false
            symbolPage = 0
            isCandidateGridMode = false
            buildKeyboard()
        }
    }

    /**
     * Switch to symbol/number mode (page 0 — digit row + common symbols).
     * Used by the service when the focused field is numeric so the user
     * lands on a number-friendly layout without an extra tap.
     */
    fun setSymbolMode() {
        if (!isSymbolMode || symbolPage != 0 || isCandidateGridMode) {
            isSymbolMode = true
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        convertDirectionPopup?.dismiss()
        convertDirectionPopup = null
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
        data class Number(val digit: Int) : KeyEvent()
        data class ShortcutPhrase(val digit: Int) : KeyEvent()
        data class Symbol(val char: Char) : KeyEvent()
        data class Emoji(val emoji: String) : KeyEvent()
        data class ClipboardPaste(val text: String) : KeyEvent()
        object Space : KeyEvent()
        object Backspace : KeyEvent()
        object Enter : KeyEvent()
        object VoiceInput : KeyEvent()
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
            '％' to '%',
        )
    }
}
