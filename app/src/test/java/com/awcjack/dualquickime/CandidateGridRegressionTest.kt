package com.awcjack.dualquickime

import android.graphics.Paint
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowPaint
import org.robolectric.util.ReflectionHelpers

/** Deterministic proportional font metrics: the Windows runtime has no native font DLL. */
@Implements(Paint::class)
open class GridFontPaintShadow : ShadowPaint() {
    @RealObject private lateinit var targetPaint: Paint
    @Implementation override fun measureText(text: String): Float =
        text.codePoints().toArray().sumOf { if (it > 127) 1.0 else 0.6 }.toFloat() * targetPaint.textSize
    @Implementation fun getFontMetrics(): Paint.FontMetrics = Paint.FontMetrics().apply {
        top = -targetPaint.textSize
        ascent = top
        bottom = targetPaint.textSize * 0.25f
        descent = bottom
    }
}

@Implements(android.text.TextPaint::class)
class GridFontTextPaintShadow : GridFontPaintShadow()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-mdpi",
    shadows = [GridFontPaintShadow::class, GridFontTextPaintShadow::class])
@Suppress("DEPRECATION") // Fixed SDK-28 fixtures exercise known, linear font scales.
class CandidateGridRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun cleanup() {
        ThemeManager.setCandidatesPerPage(context, 35)
        ThemeManager.setThemeMode(context, ThemeManager.THEME_AUTO)
        context.resources.displayMetrics.scaledDensity = 1f
    }
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

    @Test fun completeTextFitsAllPageSizesWidthsAndFontScales() {
        val candidates = listOf("字", "𠮷𠮷", "落樓話我", "落樓話我知", "characteristically")
        for (scale in listOf(1f, 1.5f, 2f)) {
            context.resources.displayMetrics.scaledDensity = scale
            for (size in listOf(20, 25, 30, 35)) {
                ThemeManager.setCandidatesPerPage(context, size)
                val grid = CandidateGridView(context)
                grid.setCandidates(candidates)
                for (width in listOf(320, 360, 411, 720, 320)) {
                    layout(grid, width)
                    assertEquals(candidates, cells(grid).map { it.text.toString() })
                    for (cell in cells(grid)) {
                        assertTrue(cell.paint.measureText(cell.text.toString()) <=
                            cell.width - cell.compoundPaddingLeft - cell.compoundPaddingRight + 0.02f)
                        val metrics = cell.paint.fontMetrics
                        assertTrue(metrics.bottom - metrics.top <=
                            cell.height - cell.compoundPaddingTop - cell.compoundPaddingBottom + 0.02f)
                        assertTrue(cell.textSize > 0f)
                    }
                }
            }
        }
    }

    @Test fun resizingRestoresPreferredSizeAndSelectionKeepsFullText() {
        ThemeManager.setCandidatesPerPage(context, 20)
        val grid = CandidateGridView(context)
        val phrase = "落樓話我知"
        grid.setCandidates(listOf(phrase))
        var selected = ""
        grid.setOnCandidateSelectedListener { selected = it }
        layout(grid, 320)
        val cell = cells(grid).single()
        assertTrue(cell.textSize < 15f)
        cell.performClick()
        assertEquals(phrase, selected)
        layout(grid, 720)
        assertEquals(15f, cell.textSize, 0.01f)
        layout(grid, 320)
        assertTrue(cell.textSize < 15f)
    }

    @Test fun supplementaryCharactersUseTheSameStartingTierAndEnlargement() {
        for (size in listOf(25, 20)) {
            ThemeManager.setCandidatesPerPage(context, size)
            val grid = CandidateGridView(context)
            grid.setCandidates(listOf("字", "𠮷", "落樓", "𠮷𠮷"))
            val actual = cells(grid).map { it.textSize }
            assertEquals(actual[0], actual[1], 0.01f)
            assertEquals(actual[2], actual[3], 0.01f)
            assertEquals(if (size == 20) 25f else 20f, actual[0], 0.01f)
        }
    }

    @Test fun openingChangedDataBuildsOnceAndUnchangedReopenReusesCells() {
        val keyboard = KeyboardView(context)
        val all = (0 until 80).map { "候選$it" }
        keyboard.setCandidates(all)
        keyboard.showCandidateGrid(all)
        val grid = ReflectionHelpers.getField<CandidateGridView>(keyboard, "candidateGridView")
        var additions = 0
        grid.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { additions++ }
            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        val firstCell = cells(grid).first()
        keyboard.closeCandidateGrid()
        keyboard.showCandidateGrid(all)
        assertEquals(0, additions)
        assertSame(firstCell, cells(grid).first())
        keyboard.closeCandidateGrid()
        keyboard.showCandidateGrid(listOf("樓", "雨"))
        assertEquals(2, additions)
        assertEquals(listOf("樓", "雨"), cells(grid).map { it.text.toString() })
    }

    @Test fun noOpRefreshPreservesCellsAndThemeAndLearnedChangesUpdateThem() {
        ThemeManager.setThemeMode(context, ThemeManager.THEME_LIGHT)
        val grid = CandidateGridView(context)
        val all = listOf("樓", "雨")
        grid.setCandidates(all)
        val first = cells(grid).first()
        grid.refreshTheme()
        grid.updateCandidatesKeepingPage(all, emptySet())
        assertSame(first, cells(grid).first())
        grid.updateCandidatesKeepingPage(all, setOf("樓"))
        assertEquals(ThemeManager.getColors(context).learnedCandidateText, cells(grid).first().currentTextColor)
        val beforeThemeChange = cells(grid).first()
        ThemeManager.setThemeMode(context, ThemeManager.THEME_DARK)
        grid.refreshTheme()
        assertNotSame(beforeThemeChange, cells(grid).first())
        assertEquals(ThemeManager.getColors(context).learnedCandidateText, cells(grid).first().currentTextColor)
    }

    @Test fun densityChangesAndShrinkingListsClampToAnExistingPage() {
        ThemeManager.setCandidatesPerPage(context, 20)
        val grid = CandidateGridView(context)
        val all = (0 until 80).map { "候選$it" }
        grid.setCandidates(all, 3)
        ThemeManager.setCandidatesPerPage(context, 35)
        grid.refreshTheme()
        assertEquals(all.drop(70), cells(grid).map { it.text.toString() })
        grid.updateCandidatesKeepingPage(all.take(36), emptySet())
        assertEquals(all.drop(35).take(1), cells(grid).map { it.text.toString() })
        grid.updateCandidatesKeepingPage(emptyList(), emptySet())
        assertTrue(cells(grid).isEmpty())
    }
}
