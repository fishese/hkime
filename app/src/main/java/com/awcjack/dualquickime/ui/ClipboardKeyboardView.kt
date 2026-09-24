package com.awcjack.dualquickime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.data.ClipboardHistoryItem
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Clipboard history keyboard view with pinned section and history tabs.
 * Design mirrors EmojiKeyboardView for consistency.
 */
class ClipboardKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onClipboardItemSelected: ((String) -> Unit)? = null
    private var onBackspacePressed: (() -> Unit)? = null
    private var onAbcPressed: (() -> Unit)? = null
    private var returnKeyLabel = "ABC"

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null

    private lateinit var colors: KeyboardColors
    private var currentTab = 0  // 0 = All, 1 = Pinned
    private val categoryTabs = mutableListOf<TextView>()
    private var clipboardScrollView: ScrollView? = null
    private var clipboardContainer: LinearLayout? = null
    private var emptyStateView: TextView? = null

    // Tab icons
    private val tabIcons = listOf("📋", "📌")  // All, Pinned

    init {
        orientation = VERTICAL
        loadTheme()
        buildView()
    }

    private fun loadTheme() {
        colors = ThemeManager.getColors(context)
        setBackgroundColor(colors.keyboardBackground)
    }

    fun refreshTheme() {
        loadTheme()
        buildView()
    }

    /**
     * Refresh clipboard content (call when returning to clipboard view).
     */
    fun refreshContent() {
        populateClipboard(currentTab)
    }

    private fun buildView() {
        removeAllViews()

        // Clipboard list area
        addView(createClipboardArea())

        // Category tabs at bottom
        addView(createCategoryBar())

        // Bottom row with ABC and backspace
        addView(createBottomRow())
    }

    private fun createClipboardArea(): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(180))
            setBackgroundColor(colors.keyboardBackground)

            clipboardScrollView = ScrollView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                isVerticalScrollBarEnabled = false
                isFillViewport = true
            }

            clipboardContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            }

            emptyStateView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(colors.clipboardEmptyText)
                visibility = View.GONE
            }

            clipboardScrollView?.addView(clipboardContainer)
            addView(clipboardScrollView)
            addView(emptyStateView)

            // Populate with initial tab
            populateClipboard(currentTab)
        }
    }

    private fun populateClipboard(tabIndex: Int) {
        clipboardContainer?.removeAllViews()
        clipboardScrollView?.scrollTo(0, 0)

        val items = when (tabIndex) {
            0 -> ClipboardHistoryManager.getHistory(context)
            1 -> ClipboardHistoryManager.getPinnedItems(context)
            else -> ClipboardHistoryManager.getHistory(context)
        }

        if (items.isEmpty()) {
            showEmptyState(tabIndex)
        } else {
            hideEmptyState()
            items.forEach { item ->
                clipboardContainer?.addView(createClipboardItem(item))
            }
        }
    }

    private fun showEmptyState(tabIndex: Int) {
        emptyStateView?.text = if (tabIndex == 1) {
            context.getString(R.string.clipboard_pinned_empty)
        } else {
            context.getString(R.string.clipboard_empty)
        }
        emptyStateView?.visibility = View.VISIBLE
        clipboardScrollView?.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateView?.visibility = View.GONE
        clipboardScrollView?.visibility = View.VISIBLE
    }

    private fun createClipboardItem(item: ClipboardHistoryItem): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(56)).apply {
                setMargins(0, dpToPx(2), 0, dpToPx(2))
            }
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8))
            background = createItemBackground(colors.clipboardItemBackground, colors.clipboardItemBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            // Text preview (clickable area)
            val textView = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                text = item.text
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textSize = 14f
                setTextColor(colors.clipboardItemText)
            }

            // Pin button
            val pinButton = TextView(context).apply {
                layoutParams = LayoutParams(dpToPx(36), dpToPx(36))
                gravity = Gravity.CENTER
                text = "📌"
                textSize = 16f
                alpha = if (item.isPinned) 1.0f else 0.4f
                background = createIconBackground()

                setOnClickListener {
                    ClipboardHistoryManager.togglePin(context, item.id)
                    populateClipboard(currentTab)
                }
            }

            // Delete button
            val deleteButton = TextView(context).apply {
                layoutParams = LayoutParams(dpToPx(36), dpToPx(36))
                gravity = Gravity.CENTER
                text = "✕"
                textSize = 16f
                setTextColor(colors.clipboardDeleteIcon)
                background = createIconBackground()

                setOnClickListener {
                    ClipboardHistoryManager.removeItem(context, item.id)
                    populateClipboard(currentTab)
                }
            }

            addView(textView)
            addView(pinButton)
            addView(deleteButton)

            // Click on text area to paste
            setOnClickListener {
                onClipboardItemSelected?.invoke(item.text)
            }
        }
    }

    private fun createCategoryBar(): HorizontalScrollView {
        return HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(colors.candidateBarBackground)

            val tabContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
            }

            categoryTabs.clear()
            tabIcons.forEachIndexed { index, icon ->
                val tab = createCategoryTab(icon, index)
                categoryTabs.add(tab)
                tabContainer.addView(tab)
            }

            updateCategorySelection()
            addView(tabContainer)
        }
    }

    private fun createCategoryTab(icon: String, index: Int): TextView {
        val label = when (index) {
            0 -> "$icon ${context.getString(R.string.clipboard_tab_all)}"
            1 -> "$icon ${context.getString(R.string.clipboard_tab_pinned)}"
            else -> icon
        }
        return TextView(context).apply {
            layoutParams = LayoutParams(0, dpToPx(36), 1f).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD

            setOnClickListener {
                if (currentTab != index) {
                    currentTab = index
                    updateCategorySelection()
                    populateClipboard(index)
                }
            }
        }
    }

    private fun updateCategorySelection() {
        categoryTabs.forEachIndexed { index, tab ->
            if (index == currentTab) {
                tab.background = createCategorySelectedBackground()
                tab.setTextColor(colors.emojiCategorySelectedText)
            } else {
                tab.background = createCategoryBackground()
                tab.setTextColor(colors.emojiCategoryText)
            }
        }
    }

    private fun createCategoryBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(this@ClipboardKeyboardView.colors.emojiCategoryBackground)
        }
    }

    private fun createCategorySelectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(this@ClipboardKeyboardView.colors.emojiCategorySelectedBackground)
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(50))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(6))

            // Return to the keyboard that opened clipboard history.
            addView(createSpecialKey(returnKeyLabel, 1.2f) {
                onAbcPressed?.invoke()
            })

            // Spacer
            addView(View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f)
            })

            // Backspace
            addView(createSpecialKey("⌫", 1.2f) {
                onBackspacePressed?.invoke()
            }.apply {
                setupKeyRepeat(this) {
                    onBackspacePressed?.invoke()
                }
            })
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

    private fun createItemBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
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

    private fun createIconBackground(): StateListDrawable {
        val pressedColor = colors.clipboardItemBackgroundPressed
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.TRANSPARENT)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun setOnClipboardItemSelectedListener(listener: (String) -> Unit) {
        onClipboardItemSelected = listener
    }

    fun setOnBackspacePressedListener(listener: () -> Unit) {
        onBackspacePressed = listener
    }

    fun setOnAbcPressedListener(listener: () -> Unit) {
        onAbcPressed = listener
    }

    fun setReturnKeyLabel(label: String) {
        returnKeyLabel = label
    }

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
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val BACKSPACE_INITIAL_DELAY = 400L
        private const val BACKSPACE_REPEAT_INTERVAL = 50L
    }
}
