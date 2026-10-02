package com.awcjack.dualquickime

import android.view.View
import android.view.MotionEvent
import android.os.Looper
import android.widget.TextView
import android.widget.GridLayout
import android.view.inputmethod.EditorInfo
import android.text.InputType
import com.awcjack.dualquickime.ui.KeyboardView
import com.awcjack.dualquickime.ui.EmojiKeyboardView
import com.awcjack.dualquickime.data.*
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
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

/** Audit observations: assertions describe the current defect, not desired behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-mdpi")
class AuditProbeTest {
    @Test fun detachedSpaceHoldStillChangesPreference() {
        val context = RuntimeEnvironment.getApplication()
        ThemeManager.setIgnoreSpaceAfterLatin(context, false)
        val view = KeyboardView(context)
        val space = ReflectionHelpers.getField<View>(view, "spaceKeyView")
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
        space.dispatchTouchEvent(event)
        event.recycle()
        KeyboardView::class.java.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }.invoke(view)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        println("AUDIT detached-space changedPreference=" + ThemeManager.getIgnoreSpaceAfterLatin(context))
        assertTrue(ThemeManager.getIgnoreSpaceAfterLatin(context))
        ThemeManager.setIgnoreSpaceAfterLatin(context, false)
    }

    @Test fun associatedRefreshClearsVisibleSuggestions() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView() as KeyboardView
            ReflectionHelpers.setField(service, "isAssociatedPhrasesMode", true)
            ReflectionHelpers.setField(service, "associatedPhrases", listOf("樓", "雨"))
            ReflectionHelpers.setField(service, "learnedAssociatedCandidates", setOf("樓"))
            view.setCandidates(listOf("樓", "雨"), setOf("樓"))
            val before = ReflectionHelpers.getField<List<TextView>>(view, "candidateSlots").size
            val refresh = ReflectionHelpers.getField<() -> Unit>(view, "onCandidateRefreshRequested")
            refresh()
            val after = ReflectionHelpers.getField<List<TextView>>(view, "candidateSlots").size
            println("AUDIT associated-refresh before=$before after=$after modeStillActive=" +
                ReflectionHelpers.getField<Boolean>(service, "isAssociatedPhrasesMode"))
            assertEquals(2, before)
            assertEquals(0, after)
        } finally { service.onDestroy() }
    }

    @Test fun emojiLastColumnExceedsNarrowViewport() {
        val view = EmojiKeyboardView(RuntimeEnvironment.getApplication())
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val grid = ReflectionHelpers.getField<GridLayout>(view, "emojiGrid")
        val right = grid.getChildAt(7).right
        println("AUDIT emoji viewport=${view.width} grid=${grid.width} lastColumnRight=$right childCount=${grid.childCount}")
        assertTrue(right > view.width)
    }

    @Test fun firstInputViewReplacesAlreadyLoadedSimplexTable() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val before = ReflectionHelpers.getField<Any>(service, "simplexTable")
            service.onCreateInputView()
            service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
            val after = ReflectionHelpers.getField<Any>(service, "simplexTable")
            println("AUDIT first-input reloadedSimplex=${before !== after}")
            assertNotSame(before, after)
        } finally { service.onDestroy() }
    }

    @Test fun bundledDataAndActualRenderedCandidateCounts() {
        val assets = RuntimeEnvironment.getApplication().assets
        val stats = assets.list("mck")!!.filter { it.startsWith("mix_map_ext_") }.map { name ->
            val text = assets.open("mck/$name").use { input -> ZipInputStream(input).use { zip ->
                zip.nextEntry; ObjectInputStream(zip).use { it.readObject() as String }
            } }
            val first = name.removePrefix("mix_map_ext_").first()
            val maximum = text.lineSequence().filter { '\t' in it }.map { row ->
                (first + row.substringBefore('\t')) to decodeMckCandidates(row.substringAfter('\t'), false).size
            }.maxBy { it.second }
            Triple(name, text.length, maximum)
        }
        println("AUDIT mix shards=${stats.size} totalUtf16Units=${stats.sumOf { it.second }} largest=${stats.maxBy { it.second }} maxCandidates=${stats.maxBy { it.third.second }}")
        val autocomplete = assets.open("english-autocomplete.txt").use { EnglishAutocomplete.parse(it) }
        val words = autocomplete.allWords()
        println("AUDIT englishWords=${words.size} busiestTwoLetterPrefix=" +
            words.filter { it.length >= 2 }.groupingBy { it.take(2) }.eachCount().maxBy { it.value })
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView() as KeyboardView
            val update = HkInputMethodService::class.java.getDeclaredMethod("updateComposition", String::class.java).apply { isAccessible = true }
            for (code in listOf("a", "s", "y", "cc", "yy", "sanf", "stah")) {
                update.invoke(service, code)
                val state = ReflectionHelpers.getField<CompositionState>(service, "composition")
                println("AUDIT code=$code candidates=${state.candidates.size} allocatedCandidateViews=" +
                    ReflectionHelpers.getField<List<TextView>>(view, "candidateSlots").size)
            }
        } finally { service.onDestroy() }
    }
}
