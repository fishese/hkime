package com.awcjack.dualquickime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.awcjack.dualquickime.data.EmojiData
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Full emoji keyboard with category tabs and scrolling grid.
 */
class EmojiKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onEmojiSelected: ((String) -> Unit)? = null
    private var onBackspacePressed: (() -> Unit)? = null
    private var onAbcPressed: (() -> Unit)? = null

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null

    // Long-press handling for skin tone popup
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var skinTonePopup: PopupWindow? = null

    private lateinit var colors: KeyboardColors
    private var currentCategory = 0
    private val categoryTabs = mutableListOf<TextView>()
    private var emojiGrid: GridLayout? = null
    private var emojiScrollView: ScrollView? = null

    // Category icons (emoji representations)
    private val categoryIcons = listOf("😀", "👋", "🐱", "🍔", "⚽", "🚗", "💡", "❤️", "🏳️")

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - dpToPx(8)).coerceAtLeast(0)
        val columns = (available / dpToPx(46).coerceAtLeast(1)).coerceAtLeast(1)
        emojiGrid?.let { grid ->
            if (grid.columnCount != columns) {
                // GridLayout resolves automatic specs during layout; discard the old
                // resolved column positions before shrinking the column count.
                for (index in 0 until grid.childCount) {
                    grid.getChildAt(index).layoutParams = GridLayout.LayoutParams().apply {
                        width = dpToPx(42); height = dpToPx(42)
                        setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                    }
                }
                grid.columnCount = columns
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun buildView() {
        removeAllViews()

        // Emoji grid area (takes most of the space)
        addView(createEmojiGridArea())

        // Category tabs at bottom
        addView(createCategoryBar())

        // Bottom row with ABC and backspace
        addView(createBottomRow())
    }

    private fun createEmojiGridArea(): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(180))
            setBackgroundColor(colors.keyboardBackground)

            emojiScrollView = ScrollView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                isVerticalScrollBarEnabled = false
                isFillViewport = true
            }

            emojiGrid = GridLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                columnCount = 8
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }

            emojiScrollView?.addView(emojiGrid)
            addView(emojiScrollView)

            // Populate with initial category
            populateEmojis(currentCategory)
        }
    }

    private fun populateEmojis(categoryIndex: Int, resetScroll: Boolean = true) {
        emojiGrid?.removeAllViews()
        if (resetScroll) {
            emojiScrollView?.scrollTo(0, 0)
        }

        val emojis = when (categoryIndex) {
            0 -> EmojiData.smileys
            1 -> EmojiData.people
            2 -> EmojiData.animals
            3 -> EmojiData.food
            4 -> EmojiData.activities
            5 -> EmojiData.travel
            6 -> EmojiData.objects
            7 -> EmojiData.symbols
            8 -> EmojiData.flags
            else -> EmojiData.smileys
        }

        // Get default skin tone preference
        val defaultSkinTone = ThemeManager.getDefaultSkinTone(context)

        val seenBases = mutableSetOf<String>()
        emojis.forEach { emoji ->
            val displayEmoji = if (EmojiData.supportsSkinTone(emoji)) {
                val baseKey = EmojiData.getBaseEmoji(emoji)
                if (!seenBases.add(baseKey)) return@forEach
                EmojiData.applySkiTone(emoji, defaultSkinTone)
            } else {
                emoji
            }
            emojiGrid?.addView(createEmojiKey(displayEmoji))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createEmojiKey(emoji: String): TextView {
        val cellSize = dpToPx(42)
        return TextView(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = emoji
            textSize = 24f
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)

            // Check if this emoji supports skin tones
            val supportsSkinTone = EmojiData.supportsSkinTone(emoji)

            if (supportsSkinTone) {
                // Long-press for skin tone popup
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true
                            // Start long-press timer
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            val runnable = Runnable {
                                showSkinTonePopup(v, emoji)
                            }
                            longPressRunnable = runnable
                            longPressHandler.postDelayed(runnable, LONG_PRESS_DELAY)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            v.isPressed = false
                            // Cancel long-press if still pending
                            longPressRunnable?.let {
                                longPressHandler.removeCallbacks(it)
                                // If long-press didn't fire, treat as click
                                if (skinTonePopup?.isShowing != true) {
                                    onEmojiSelected?.invoke(emoji)
                                }
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
            } else {
                // Simple click for emojis without skin tone support
                setOnClickListener {
                    onEmojiSelected?.invoke(emoji)
                }
            }
        }
    }

    private fun showSkinTonePopup(anchorView: View, emoji: String) {
        // Dismiss any existing popup
        skinTonePopup?.dismiss()

        val variants = EmojiData.getSkinToneVariants(emoji)
        if (variants.size <= 1) return

        // Create popup content
        val popupContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            elevation = dpToPx(4).toFloat()
        }

        // Add skin tone variants
        variants.forEachIndexed { index, variant ->
            val variantView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                gravity = Gravity.CENTER
                text = variant
                textSize = 24f
                background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)

                setOnClickListener {
                    // Save the selected skin tone as the new default
                    ThemeManager.setDefaultSkinTone(context, index)
                    // Emit the selected emoji
                    onEmojiSelected?.invoke(variant)
                    skinTonePopup?.dismiss()
                    // Refresh the grid to show emojis with new default skin tone (preserve scroll position)
                    populateEmojis(currentCategory, resetScroll = false)
                }
            }
            popupContent.addView(variantView)
        }

        // Create and show popup
        skinTonePopup = PopupWindow(
            popupContent,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dpToPx(8).toFloat()
            isOutsideTouchable = true
            setOnDismissListener {
                skinTonePopup = null
            }

            // Calculate popup position (centered above the anchor)
            val location = IntArray(2)
            anchorView.getLocationInWindow(location)
            val popupWidth = variants.size * dpToPx(40) + dpToPx(8)
            val xOffset = location[0] + (anchorView.width / 2) - (popupWidth / 2)

            showAtLocation(
                anchorView,
                Gravity.NO_GRAVITY,
                xOffset.coerceAtLeast(dpToPx(4)),
                location[1] - dpToPx(50)
            )
        }
    }

    private fun createCategoryBar(): HorizontalScrollView {
        return HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(colors.candidateBarBackground)

            val tabContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
            }

            categoryTabs.clear()
            categoryIcons.forEachIndexed { index, icon ->
                val tab = createCategoryTab(icon, index)
                categoryTabs.add(tab)
                tabContainer.addView(tab)
            }

            updateCategorySelection()
            addView(tabContainer)
        }
    }

    private fun createCategoryTab(icon: String, index: Int): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(dpToPx(44), dpToPx(36)).apply {
                setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = icon
            textSize = 20f

            setOnClickListener {
                if (currentCategory != index) {
                    currentCategory = index
                    updateCategorySelection()
                    populateEmojis(index)
                }
            }
        }
    }

    private fun updateCategorySelection() {
        categoryTabs.forEachIndexed { index, tab ->
            if (index == currentCategory) {
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
            setColor(this@EmojiKeyboardView.colors.emojiCategoryBackground)
        }
    }

    private fun createCategorySelectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(this@EmojiKeyboardView.colors.emojiCategorySelectedBackground)
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(50))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(6))

            // ABC button
            addView(createSpecialKey("ABC", 1.2f) {
                onAbcPressed?.invoke()
            })

            // Spacer
            addView(android.view.View(context).apply {
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

    fun setOnEmojiSelectedListener(listener: (String) -> Unit) {
        onEmojiSelected = listener
    }

    fun setOnBackspacePressedListener(listener: () -> Unit) {
        onBackspacePressed = listener
    }

    fun setOnAbcPressedListener(listener: () -> Unit) {
        onAbcPressed = listener
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
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        skinTonePopup?.dismiss()
        skinTonePopup = null
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
        private const val LONG_PRESS_DELAY = 500L
    }
}
