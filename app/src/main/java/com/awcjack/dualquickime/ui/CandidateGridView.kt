package com.awcjack.dualquickime.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Full-screen candidate grid view that covers the entire keyboard space.
 * Displays all candidates in a paginated grid layout.
 *
 * Used when the user clicks the page indicator in the candidate bar to
 * view all candidates at once, rather than cycling through pages with Space.
 */
class CandidateGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onCandidateSelected: ((String) -> Unit)? = null
    private var onBackPressed: (() -> Unit)? = null

    private lateinit var colors: KeyboardColors

    private var allCandidates: List<String> = emptyList()
    private var learnedCandidates = emptySet<String>()
    private var currentPage: Int = 0

    // Keep the same grid height while giving enlarged text taller cells.
    private var gridColumns = 7
    private var gridRows = 5
    private var candidateFontScale = 1f
    private val candidatesPerPage: Int get() = gridColumns * gridRows

    private var gridContainer: LinearLayout? = null
    private var pageIndicator: TextView? = null
    private var prevPageButton: TextView? = null
    private var nextPageButton: TextView? = null

    init {
        orientation = VERTICAL
        loadTheme()
    }

    private fun loadTheme(): Boolean {
        val nextColors = ThemeManager.getColors(context)
        val changedColors = !::colors.isInitialized || colors != nextColors
        val previousSize = candidatesPerPage
        colors = nextColors
        loadGridLayout()
        currentPage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
        setBackgroundColor(colors.keyboardBackground)
        setPadding(dpToPx(3), dpToPx(6), dpToPx(3), dpToPx(8))
        return changedColors || previousSize != candidatesPerPage
    }

    private fun loadGridLayout() {
        val pageSize = ThemeManager.getCandidatesPerPage(context)
        gridRows = if (pageSize == 20) 4 else 5
        gridColumns = pageSize / gridRows
        candidateFontScale = if (pageSize == 20) 1.25f else 1f
    }

    fun refreshTheme() {
        val changed = loadTheme()
        if (changed && childCount > 0) {
            buildView()
        }
    }

    /**
     * Set the full list of candidates and display them.
     * @param candidates All candidates to display.
     * @param initialPage The page to show initially (0-based).
     */
    fun updateCandidatesKeepingPage(candidates: List<String>, learned: Set<String>) {
        setCandidates(candidates, currentPage, learned)
    }

    fun setCandidates(candidates: List<String>, initialPage: Int = 0, learned: Set<String> = emptySet()) {
        val changedTheme = loadTheme()
        val changedCandidates = allCandidates != candidates || learnedCandidates != learned
        val previousPage = currentPage
        allCandidates = candidates
        if (learnedCandidates != learned) learnedCandidates = learned.toSet()
        currentPage = initialPage.coerceIn(0, maxOf(0, totalPages - 1))
        if (changedTheme || changedCandidates || previousPage != currentPage || childCount == 0) buildView()
    }

    private val totalPages: Int
        get() = if (allCandidates.isEmpty()) 0
        else (allCandidates.size + candidatesPerPage - 1) / candidatesPerPage

    private val currentPageCandidates: List<String>
        get() {
            if (allCandidates.isEmpty()) return emptyList()
            val start = currentPage * candidatesPerPage
            val end = minOf(start + candidatesPerPage, allCandidates.size)
            return if (start < allCandidates.size) allCandidates.subList(start, end) else emptyList()
        }

    private fun buildView() {
        removeAllViews()

        // Grid area: rows of candidates
        addView(createGridArea())

        // Bottom bar: back button, page navigation
        addView(createBottomRow())
    }

    private fun createGridArea(): LinearLayout {
        val container = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            orientation = VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
        }
        gridContainer = container

        val candidates = currentPageCandidates
        var candidateIndex = 0

        for (row in 0 until gridRows) {
            val rowLayout = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(if (gridRows == 4) 55 else 44))
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (col in 0 until gridColumns) {
                if (candidateIndex < candidates.size) {
                    val candidate = candidates[candidateIndex]
                    rowLayout.addView(createCandidateCell(candidate))
                    candidateIndex++
                } else {
                    // Empty placeholder to maintain grid alignment
                    rowLayout.addView(createEmptyCell())
                }
            }

            container.addView(rowLayout)
        }

        return container
    }

    private fun createCandidateCell(candidate: String): TextView {
        val charCount = candidate.codePointCount(0, candidate.length)
        val startingSize = candidateFontScale * when {
            charCount <= 1 -> 20f
            charCount <= 2 -> 18f
            charCount <= 4 -> 15f
            else -> 12f
        }
        return FittingCandidateTextView(context, startingSize).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = candidate
            setTextColor(if (candidate in learnedCandidates) colors.learnedCandidateText else colors.candidateText)
            contentDescription = if (candidate in learnedCandidates)
                context.getString(com.awcjack.dualquickime.R.string.learned_candidate_accessibility, candidate) else candidate
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            // Keep the full candidate on one line; fitting uses this cell's measured size.
            maxLines = 1
            setHorizontallyScrolling(true)
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))

            setOnClickListener {
                onCandidateSelected?.invoke(candidate)
            }
        }
    }

    /** Measures only visible cells, starting from their preferred size on every resize. */
    private class FittingCandidateTextView(context: Context, startingSizeSp: Float) : AppCompatTextView(context) {
        private val maximumSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, startingSizeSp, resources.displayMetrics)
        private val measuringPaint = TextPaint()
        private var previousWidth = -1
        private var previousHeight = -1

        init { setTextSize(TypedValue.COMPLEX_UNIT_PX, maximumSizePx) }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val availableWidth = measuredWidth - compoundPaddingLeft - compoundPaddingRight
            val availableHeight = measuredHeight - compoundPaddingTop - compoundPaddingBottom
            if (availableWidth <= 0 || availableHeight <= 0 ||
                availableWidth == previousWidth && availableHeight == previousHeight) return
            previousWidth = availableWidth
            previousHeight = availableHeight
            measuringPaint.set(paint)
            measuringPaint.textSize = maximumSizePx
            val requiredWidth = measuringPaint.measureText(text.toString()).coerceAtLeast(1f)
            val metrics = measuringPaint.fontMetrics
            val requiredHeight = (if (includeFontPadding) metrics.bottom - metrics.top
                else metrics.descent - metrics.ascent).coerceAtLeast(1f)
            val fittedSize = maximumSizePx * minOf(1f,
                availableWidth / requiredWidth, availableHeight / requiredHeight)
            if (kotlin.math.abs(textSize - fittedSize) > 0.01f) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedSize)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    private fun createEmptyCell(weight: Float = 1f): View {
        return View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(50))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))

            // Back button
            addView(createSpecialKey("ABC", 1.2f) {
                onBackPressed?.invoke()
            })

            // Spacer
            addView(View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            })

            // Page navigation area (only if multi-page)
            if (totalPages > 1) {
                // Previous page button
                prevPageButton = createSpecialKey("◀", 0.8f) {
                    if (currentPage > 0) {
                        currentPage--
                        buildView()
                    }
                }
                addView(prevPageButton)

                // Page indicator
                pageIndicator = TextView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(12), 0, dpToPx(12), 0)
                    text = "${currentPage + 1}/$totalPages"
                    textSize = 14f
                    setTextColor(colors.pageIndicatorText)
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(pageIndicator)

                // Next page button
                nextPageButton = createSpecialKey("▶", 0.8f) {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                        buildView()
                    }
                }
                addView(nextPageButton)
            } else {
                // No pagination needed - just show a centered indicator
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                })
            }
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 16f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener { onClick() }
        }
    }

    private fun createPillBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(normalColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createKeyBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
            setColor(normalColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ==================== PUBLIC LISTENERS ====================

    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    fun setOnBackPressedListener(listener: () -> Unit) {
        onBackPressed = listener
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
