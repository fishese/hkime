package com.awcjack.dualquickime

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
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
