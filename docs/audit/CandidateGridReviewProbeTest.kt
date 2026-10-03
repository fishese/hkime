package com.awcjack.dualquickime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.CandidateGridView
import com.awcjack.dualquickime.ui.KeyboardView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Baseline review probes for v0.3.60. Methods prefixed observed reproduce defects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-mdpi")
class CandidateGridReviewProbeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun cleanup() { ThemeManager.setCandidatesPerPage(context, 35) }

    private fun layout(view: View, width: Int = 360) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun cells(grid: CandidateGridView) = texts(grid.getChildAt(0))

    @Test fun pageBoundariesAndSelectionsRemainCorrect() {
        for (size in listOf(20, 25, 30, 35)) {
            ThemeManager.setCandidatesPerPage(context, size)
            val grid = CandidateGridView(context)
            var selected = ""
            grid.setOnCandidateSelectedListener { selected = it }
            for (count in listOf(0, 1, size - 1, size, size + 1, size * 2, size * 2 + 1)) {
                val all = (0 until count).map { "候選$it" }
                grid.setCandidates(all)
                val pages = maxOf(1, (count + size - 1) / size)
                for (page in 0 until pages) {
                    assertEquals(all.drop(page * size).take(size), cells(grid).map { it.text.toString() })
                    cells(grid).lastOrNull()?.let { it.performClick(); assertEquals(it.text.toString(), selected) }
                    if (pages > 1) texts(grid).first { it.text == "▶" }.performClick()
                }
                // Advancing at the final page must not wrap or lose its final candidate.
                assertEquals(all.drop((pages - 1) * size), cells(grid).map { it.text.toString() })
                for (page in pages - 2 downTo 0) {
                    texts(grid).first { it.text == "◀" }.performClick()
                    assertEquals(all.drop(page * size).take(size), cells(grid).map { it.text.toString() })
                }
            }
        }
    }

    @Test fun listShrinkAndLearnedMetadataRefreshRemainCorrect() {
        ThemeManager.setCandidatesPerPage(context, 20)
        val grid = CandidateGridView(context)
        val all = (0 until 45).map { "候選$it" }
        grid.setCandidates(all, 2)
        assertEquals(all.drop(40), cells(grid).map { it.text.toString() })
        grid.updateCandidatesKeepingPage(all.take(21), setOf(all[20]))
        assertEquals(listOf(all[20]), cells(grid).map { it.text.toString() })
        assertEquals(ThemeManager.getColors(context).learnedCandidateText, cells(grid).single().currentTextColor)
        grid.updateCandidatesKeepingPage(all.take(1), emptySet())
        assertEquals(all.take(1), cells(grid).map { it.text.toString() })
        grid.setCandidates(emptyList())
        assertTrue(cells(grid).isEmpty())
    }

    @Test fun observedEnlargedPhraseHasNarrowSingleLineCellWithoutOverflowHandling() {
        ThemeManager.setCandidatesPerPage(context, 20)
        val grid = CandidateGridView(context)
        grid.setCandidates(listOf("落樓話我", "characteristically"))
        layout(grid)
        for (cell in cells(grid)) {
            val available = cell.width - cell.compoundPaddingLeft - cell.compoundPaddingRight
            // Verify geometry only: this Windows Robolectric runtime lacks native font measurement.
            println("text=${cell.text} available=$available font=${cell.textSize}")
            assertTrue(available in 54..58)
            assertEquals(1, cell.maxLines)
            assertNull(cell.ellipsize)
            assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, cell.autoSizeTextType)
        }
        assertEquals(18.75f, cells(grid)[0].textSize, 0.01f)
    }

    @Test fun observedReopeningBuildsTheGridTwice() {
        val keyboard = KeyboardView(context)
        val all = (0 until 100).map { "候選$it" }
        keyboard.setCandidates(all)
        keyboard.showCandidateGrid(all)
        val grid = ReflectionHelpers.getField<CandidateGridView>(keyboard, "candidateGridView")
        keyboard.closeCandidateGrid()
        var areasAdded = 0
        grid.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { areasAdded++ }
            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        keyboard.showCandidateGrid(all)
        // Each complete build adds the grid area and navigation row.
        assertEquals(4, areasAdded)
    }

    @Test fun observedSupplementaryCharacterUsesTwoCharacterFontTier() {
        ThemeManager.setCandidatesPerPage(context, 20)
        val grid = CandidateGridView(context)
        grid.setCandidates(listOf("字", "𠮷"))
        assertEquals(25f, cells(grid)[0].textSize, 0.01f)
        assertEquals(22.5f, cells(grid)[1].textSize, 0.01f)
    }

    @Test fun leavingGridRestoresLearnedExpansionAndRegularChoices() {
        val all = (0 until 30).map { "候選$it" }
        val learned = all.take(8).toSet()
        for (expanded in listOf(false, true)) {
            val keyboard = KeyboardView(context)
            val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
            activity.get().setContentView(keyboard)
            keyboard.setCandidates(all, learned)
            layout(keyboard)
            val list = ReflectionHelpers.getField<androidx.recyclerview.widget.RecyclerView>(keyboard, "candidateList")
            if (expanded) {
                val toggle = list.adapter!!.createViewHolder(list, list.adapter!!.getItemViewType(5))
                @Suppress("UNCHECKED_CAST")
                (list.adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>)
                    .bindViewHolder(toggle, 5)
                toggle.itemView.performClick()
            }
            keyboard.setOnCandidateRefreshRequestedListener { keyboard.setCandidates(all, learned) }
            keyboard.showCandidateGrid(all)
            val grid = ReflectionHelpers.getField<CandidateGridView>(keyboard, "candidateGridView")
            texts(grid).first { it.text == "ABC" }.performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            layout(keyboard)
            val restored = ReflectionHelpers.getField<androidx.recyclerview.widget.RecyclerView>(keyboard, "candidateList")
            assertEquals(if (expanded) 31 else 28, restored.adapter!!.itemCount)
            assertEquals(if (expanded) all else all.take(5) + all.drop(8),
                ReflectionHelpers.getField<List<String>>(keyboard, "displayedCandidates"))
            activity.pause().stop().destroy()
        }
    }
}
