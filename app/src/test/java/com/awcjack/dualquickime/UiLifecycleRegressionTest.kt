package com.awcjack.dualquickime

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import com.awcjack.dualquickime.ui.EmojiKeyboardView
import com.awcjack.dualquickime.ui.KeyboardView
import com.awcjack.dualquickime.theme.ThemeManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.text.InputType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-mdpi")
class UiLifecycleRegressionTest {
    @Test fun remainingSymbolPagesKeepNumbersBelowTheSharedToolsAndCandidateBar() {
        val context = RuntimeEnvironment.getApplication()
        try {
            for (expanded in listOf(false, true)) {
                ThemeManager.setExpandedNumberRow(context, expanded)
                val view = KeyboardView(context)
                layout(view)
                val letterHeight = view.height
                view.setSymbolMode()
                view.setCandidates(listOf("①", "一"))

                for (page in 2..5) {
                    // Switch with candidates still showing, as on a real symbol page.
                    val pageButton = (view.getChildAt(3) as LinearLayout).getChildAt(0) as TextView
                    assertEquals("${page - 1}/5", pageButton.text.toString())
                    pageButton.performClick()
                    layout(view)
                    val tools = ReflectionHelpers.getField<View>(view, "symbolUtilBar")
                    val candidates = ReflectionHelpers.getField<View>(view, "symbolCandidateBar")
                    val numberRow = ReflectionHelpers.getField<LinearLayout>(view, "numberRow")
                    val strip = ReflectionHelpers.getField<View>(view, "candidateContainer")
                    assertEquals(letterHeight, view.height)
                    assertEquals(View.VISIBLE, tools.visibility)
                    assertEquals(if (expanded) View.VISIBLE else View.GONE, candidates.visibility)
                    assertEquals(View.GONE, strip.visibility)
                    if (expanded) {
                        assertEquals(View.VISIBLE, numberRow.visibility)
                        assertTrue(numberRow.top >= tools.bottom)
                        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                            (0 until numberRow.childCount).map { (numberRow.getChildAt(it) as TextView).text.toString() })
                    }

                    view.setCandidates(listOf("②", "二"))
                    layout(view)
                    assertEquals(letterHeight, view.height)
                    assertEquals(View.GONE, tools.visibility)
                    assertEquals(View.VISIBLE, strip.visibility)
                    assertEquals(if (expanded) View.VISIBLE else View.GONE, numberRow.visibility)
                    if (expanded) assertTrue(numberRow.top >= strip.bottom)

                    view.clearCandidates()
                    layout(view)
                    assertEquals(letterHeight, view.height)
                    assertEquals(View.VISIBLE, tools.visibility)
                    assertEquals(View.GONE, strip.visibility)
                    if (expanded) assertEquals(View.VISIBLE, numberRow.visibility)
                    view.setCandidates(listOf("②", "二"))
                }
                // Wrap around to page 1 and verify its tools occupy the extra row.
                ((view.getChildAt(3) as LinearLayout).getChildAt(0) as TextView).performClick()
                view.clearCandidates()
                layout(view)
                assertEquals(letterHeight, view.height)
                if (expanded) {
                    val tools = ReflectionHelpers.getField<View>(view, "symbolUtilBar")
                    val strip = ReflectionHelpers.getField<View>(view, "candidateContainer")
                    assertTrue(tools.top >= strip.bottom)
                    assertEquals(View.GONE, ReflectionHelpers.getField<View>(view, "numberRow").visibility)
                }
            }
        } finally {
            ThemeManager.setExpandedNumberRow(context, false)
        }
    }

    @Test fun firstSymbolPageKeepsExpandedHeightAndToolsWhileCandidatesChange() {
        val context = RuntimeEnvironment.getApplication()
        try {
            for (expanded in listOf(false, true)) {
                ThemeManager.setExpandedNumberRow(context, expanded)
                val view = KeyboardView(context)
                view.clearCandidates()
                layout(view)
                val letterHeight = view.height

                view.setSymbolMode()
                view.clearCandidates()
                layout(view)
                val tools = ReflectionHelpers.getField<View>(view, "symbolUtilBar")
                val candidates = ReflectionHelpers.getField<View>(view, "symbolCandidateBar")
                assertEquals(letterHeight, view.height)
                assertEquals(View.VISIBLE, tools.visibility)
                assertEquals(if (expanded) View.VISIBLE else View.GONE, candidates.visibility)

                view.setCandidates(listOf("①", "一"))
                layout(view)
                assertEquals(letterHeight, view.height)
                assertEquals(View.VISIBLE, candidates.visibility)
                assertEquals(if (expanded) View.VISIBLE else View.GONE, tools.visibility)
                assertEquals(View.GONE, ReflectionHelpers.getField<View>(view, "numberRow").visibility)
                if (expanded) assertTrue(tools.top >= candidates.bottom)

                view.clearCandidates()
                layout(view)
                assertEquals(letterHeight, view.height)
                assertEquals(View.VISIBLE, tools.visibility)
                assertEquals(if (expanded) View.VISIBLE else View.GONE, candidates.visibility)
                if (expanded) {
                    assertEquals(View.GONE, ReflectionHelpers.getField<View>(view, "numberRow").visibility)
                    assertEquals(View.VISIBLE, ReflectionHelpers.getField<View>(view, "candidateContainer").visibility)
                }
            }
        } finally {
            ThemeManager.setExpandedNumberRow(context, false)
        }
    }

    @Test fun numberRowSettingRebuildsTheExistingKeyboardOnRefresh() {
        val context = RuntimeEnvironment.getApplication()
        ThemeManager.setExpandedNumberRow(context, false)
        val view = KeyboardView(context)
        view.setCandidates(listOf("字"))
        layout(view)
        val compactHeight = view.height
        try {
            ThemeManager.setExpandedNumberRow(context, true)
            view.refreshTheme()
            view.setCandidates(listOf("字"))
            layout(view)
            assertTrue(view.height > compactHeight)
            assertEquals(View.VISIBLE, ReflectionHelpers.getField<View>(view, "numberRow").visibility)
            ThemeManager.setExpandedNumberRow(context, false)
            view.refreshTheme()
            view.setCandidates(listOf("字"))
            layout(view)
            assertEquals(compactHeight, view.height)
            assertEquals(View.GONE, ReflectionHelpers.getField<View>(view, "numberRow").visibility)
        } finally {
            ThemeManager.setExpandedNumberRow(context, false)
        }
    }

    @Test fun expandedNumberRowStaysBesideCandidatesAndOffersNumberAlternatives() {
        val context = RuntimeEnvironment.getApplication()
        ThemeManager.setExpandedNumberRow(context, true)
        try {
            val view = KeyboardView(context)
            var pressed: KeyboardView.KeyEvent? = null
            view.setOnKeyPressListener { pressed = it }
            view.setCandidates(listOf("一", "壹"))
            layout(view)

            val candidateContainer = ReflectionHelpers.getField<View>(view, "candidateContainer")
            val numberRow = ReflectionHelpers.getField<LinearLayout>(view, "numberRow")
            assertEquals(View.VISIBLE, candidateContainer.visibility)
            assertEquals(View.VISIBLE, numberRow.visibility)
            assertTrue(numberRow.top >= candidateContainer.bottom)
            assertEquals(listOf("一", "壹"), ReflectionHelpers.getField<List<String>>(view, "displayedCandidates"))

            val numberKey = numberRow.getChildAt(0)
            val now = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
            val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, 10f, 10f, 0)
            try {
                numberKey.dispatchTouchEvent(down)
                numberKey.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
            assertEquals(KeyboardView.KeyEvent.Number(1, offerSymbolCandidates = true), pressed)
        } finally {
            ThemeManager.setExpandedNumberRow(context, false)
        }
    }

    @Test fun stripCounterUsesFirstCandidatePositionAndCapsTotalWithSmallPlus() {
        val view = KeyboardView(RuntimeEnvironment.getApplication())
        view.setCandidates((0 until 1425).map { "候選$it" })
        layout(view)
        val list = ReflectionHelpers.getField<RecyclerView>(view, "candidateList")
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(10, 0)
        layout(view)
        KeyboardView::class.java.getDeclaredMethod("updateCandidatePosition").apply { isAccessible = true }.invoke(view)
        val indicator = ReflectionHelpers.getField<TextView>(view, "pageIndicator")
        assertEquals("11/99+", indicator.text.toString())
        val spans = (indicator.text as android.text.Spanned).getSpans(0, indicator.text.length,
            android.text.style.RelativeSizeSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(0.7f, spans.single().sizeChange, 0.001f)
    }
    @Test fun collapsedLearnedCandidatesKeepFullListPositionsInCounter() {
        val view = KeyboardView(RuntimeEnvironment.getApplication())
        val all = (0 until 27).map { "候選$it" }
        view.setCandidates(all, all.take(7).toSet())
        layout(view)
        val list = ReflectionHelpers.getField<RecyclerView>(view, "candidateList")
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(6, 0)
        layout(view)
        KeyboardView::class.java.getDeclaredMethod("updateCandidatePosition").apply { isAccessible = true }.invoke(view)
        assertEquals("8/27", ReflectionHelpers.getField<TextView>(view, "pageIndicator").text.toString())
    }
    @Test fun fullGridDensityControlsActualPageContents() {
        val context = RuntimeEnvironment.getApplication()
        val all = (0 until 80).map { "候選$it" }
        fun labels(view: View): List<String> = when (view) {
            is TextView -> listOf(view.text.toString()).filter { it.startsWith("候選") }
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
            else -> emptyList()
        }
        try {
            for (count in listOf(20, 25, 30, 35)) {
                ThemeManager.setCandidatesPerPage(context, count)
                val grid = com.awcjack.dualquickime.ui.CandidateGridView(context)
                grid.setCandidates(all)
                assertEquals(all.take(count), labels(grid))
                ReflectionHelpers.getField<TextView>(grid, "nextPageButton").performClick()
                assertEquals(all.drop(count).take(count), labels(grid))
            }
        } finally { ThemeManager.setCandidatesPerPage(context, 35) }
    }
    @Test fun enlargedGridIncreasesFontsWithoutChangingOverallHeight() {
        val context = RuntimeEnvironment.getApplication()
        val candidates = listOf("字", "落樓", "落樓話我", "落樓話我知")
        val expectedSizes = listOf(20f, 18f, 15f, 12f)
        val grid = com.awcjack.dualquickime.ui.CandidateGridView(context)
        var standardHeight = 0
        try {
            // Reuse the view to verify that switching back restores the normal layout.
            for (count in listOf(25, 20, 30, 35, 20, 25)) {
                ThemeManager.setCandidatesPerPage(context, count)
                grid.setCandidates(candidates)
                layout(grid)
                val container = grid.getChildAt(0) as android.view.ViewGroup
                assertEquals(if (count == 20) 4 else 5, container.childCount)
                val row = container.getChildAt(0) as android.view.ViewGroup
                assertEquals(if (count == 20) 5 else count / 5, row.childCount)
                for (index in candidates.indices) {
                    val cell = row.getChildAt(index) as TextView
                    // Longer phrases may shrink to fit; starting sizes remain the ceiling.
                    val startingSize = expectedSizes[index] * (if (count == 20) 1.25f else 1f)
                    assertTrue(cell.textSize > 0f)
                    assertTrue(cell.textSize <= startingSize + 0.01f)
                }
                if (standardHeight == 0) standardHeight = grid.measuredHeight
                assertEquals(standardHeight, grid.measuredHeight)
            }
        } finally { ThemeManager.setCandidatesPerPage(context, 35) }
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    @Test fun largeStripRecyclesViewsAndLastResultRemainsSelectable() {
        val view = KeyboardView(RuntimeEnvironment.getApplication())
        val all = (0 until 1425).map { "候選$it" }
        view.setCandidates(all)
        layout(view)
        val list = ReflectionHelpers.getField<RecyclerView>(view, "candidateList")
        assertEquals(1425, list.adapter!!.itemCount)
        assertTrue(list.childCount in 1..32)
        var selected = ""
        view.setOnCandidateSelectedListener { selected = it }
        list.scrollToPosition(all.lastIndex)
        layout(view)
        (list.layoutManager as LinearLayoutManager).findViewByPosition(all.lastIndex)!!.performClick()
        assertEquals(all.last(), selected)
        assertTrue(list.childCount <= 32)
    }
    @Test fun actualLargeDictionaryQueryUsesOnlyViewportViews() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView() as KeyboardView
            HkInputMethodService::class.java.getDeclaredMethod("updateComposition", String::class.java)
                .apply { isAccessible = true }.invoke(service, "cc")
            settleServiceWork(service)
            layout(view)
            val list = ReflectionHelpers.getField<RecyclerView>(view, "candidateList")
            assertEquals(1425, list.adapter!!.itemCount)
            assertTrue(list.childCount in 1..32)
            println("Candidate viewport children=${list.childCount}, total results=${list.adapter!!.itemCount}")
        } finally { service.onDestroy() }
    }
    @Test fun unchangedEditorEntryReusesLoadedTableAndKeyboardViews() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView() as KeyboardView
            settleServiceWork(service)
            val table = ReflectionHelpers.getField<Any>(service, "simplexTable")
            val key = ReflectionHelpers.getField<View>(view, "spaceKeyView")
            repeat(4) { service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false) }
            settleServiceWork(service)
            assertSame(table, ReflectionHelpers.getField<Any>(service, "simplexTable"))
            assertSame(key, ReflectionHelpers.getField<View>(view, "spaceKeyView"))
        } finally { service.onDestroy() }
    }
    @Test fun associatedRefreshRetainsBothKindsOfCandidates() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView() as KeyboardView
            ReflectionHelpers.setField(service, "isAssociatedPhrasesMode", true)
            ReflectionHelpers.setField(service, "associatedPhrases", listOf("樓", "雨"))
            ReflectionHelpers.setField(service, "learnedAssociatedCandidates", emptySet<String>())
            ReflectionHelpers.getField<() -> Unit>(view, "onCandidateRefreshRequested")()
            assertEquals(listOf("樓", "雨"), ReflectionHelpers.getField<List<String>>(view, "displayedCandidates"))
        } finally { service.onDestroy() }
    }

    @Test fun detachedSpaceHoldCannotChangePreference() {
        val context = RuntimeEnvironment.getApplication()
        ThemeManager.setIgnoreSpaceAfterLatin(context, false)
        val view = KeyboardView(context)
        val space = ReflectionHelpers.getField<View>(view, "spaceKeyView")
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
        space.dispatchTouchEvent(event)
        event.recycle()
        KeyboardView::class.java.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }.invoke(view)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(ThemeManager.getIgnoreSpaceAfterLatin(context))
    }

    @Test fun emojiColumnsFitAndAdaptWhenResized() {
        val view = EmojiKeyboardView(RuntimeEnvironment.getApplication())
        for (width in listOf(320, 360, 411, 720, 320)) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val grid = ReflectionHelpers.getField<GridLayout>(view, "emojiGrid")
            assertTrue((0 until grid.childCount).all { grid.getChildAt(it).right <= width })
            assertEquals(171, grid.childCount)
        }
    }
}
