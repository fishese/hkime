package com.awcjack.dualquickime

import android.text.InputType
import android.text.Selection
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.awcjack.dualquickime.data.LearnedPhraseManager
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import com.awcjack.dualquickime.ui.CandidateGridView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearnedPhraseIntegrationTest {
    private lateinit var service: HkInputMethodService
    private lateinit var connection: TestConnection
    private class TestConnection(view: View) : BaseInputConnection(view, true) {
        var acceptCommits = true
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
            if (acceptCommits) super.commitText(text, newCursorPosition) else false
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
            ExtractedText().apply {
                text = editable.toString(); startOffset = 0
                selectionStart = Selection.getSelectionStart(editable)
                selectionEnd = Selection.getSelectionEnd(editable)
            }
    }

    @Before fun setup() {
        service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        ThemeManager.setLearnedPhrasesEnabled(service, true)
        ThemeManager.setRecentCandidatesEnabled(service, false)
        LearnedPhraseManager.clearAll(service)
        connection = TestConnection(View(service))
        Selection.setSelection(connection.editable!!, 0)
        ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
        field(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
    }

    @After fun teardown() {
        LearnedPhraseManager.clearAll(service)
        ThemeManager.setLearnedPhrasesEnabled(service, false)
        service.onDestroy()
    }

    private fun field(info: EditorInfo) {
        ReflectionHelpers.setField(service, "mInputEditorInfo", info)
        call("resetLearnedPhraseContext")
    }
    private fun call(name: String) {
        HkInputMethodService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
        settleServiceWork(service)
    }
    private fun select(text: String, association: Boolean = false) {
        HkInputMethodService::class.java.getDeclaredMethod(
            if (association) "handleAssociatedPhraseSelected" else "commitCandidate", String::class.java)
            .apply { isAccessible = true }.invoke(service, text)
        settleServiceWork(service)
    }
    private fun key(event: KeyboardView.KeyEvent) {
        HkInputMethodService::class.java.getDeclaredMethod("handleKeyEvent", KeyboardView.KeyEvent::class.java)
            .apply { isAccessible = true }.invoke(service, event)
        settleServiceWork(service)
    }
    private fun suggestions(prefix: String) = LearnedPhraseManager.suggestions(service, prefix)

    @Test fun learnsSelectedChainAcrossTypedCodesThenDisplaysLearnedContinuation() {
        select("落")
        key(KeyboardView.KeyEvent.Letter('l'))
        key(KeyboardView.KeyEvent.Letter('a'))
        select("樓")
        select("話", true)
        select("我", true)
        select("知", true)
        assertEquals("落樓話我知", connection.editable.toString())
        assertEquals(listOf("樓"), suggestions("落"))
        assertEquals("話", suggestions("落樓").first())
        field(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        select("落")
        val displayed = ReflectionHelpers.getField<List<String>>(service, "associatedPhrases")
        assertEquals("樓", displayed.first())
        assertEquals(setOf("樓"), ReflectionHelpers.getField<Set<String>>(service, "learnedAssociatedCandidates"))
    }

    @Test fun punctuationCursorMovementDeletionAndNewFieldBreakLearningContext() {
        select("落")
        select("hello")
        select("樓")
        assertTrue(suggestions("落").isEmpty())
        select("落")
        Selection.setSelection(connection.editable!!, 0)
        call("invalidateEditorAnchors")
        Selection.setSelection(connection.editable!!, connection.editable!!.length)
        select("雨")
        assertTrue(suggestions("落").isEmpty())
        select("落")
        key(KeyboardView.KeyEvent.Backspace)
        select("雨")
        assertTrue(suggestions("落").isEmpty())
        select("落")
        field(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        select("樓")
        assertTrue(suggestions("落").isEmpty())
    }

    @Test fun pastedAppTextAndRejectedCommitsNeverTrain() {
        connection.commitText("落樓話", 1)
        select("我")
        assertTrue(suggestions("話").isEmpty())
        assertTrue(suggestions("落樓話").isEmpty())
        select("落")
        connection.acceptCommits = false
        select("樓")
        assertTrue(suggestions("落").isEmpty())
    }

    @Test fun disabledRetainsEntriesButDoesNotLearnOrDisplayThenClearPersists() {
        select("落樓")
        ThemeManager.setLearnedPhrasesEnabled(service, false)
        select("落雨")
        assertTrue(suggestions("落").isEmpty())
        assertTrue(ReflectionHelpers.getField<Set<String>>(service, "learnedAssociatedCandidates").isEmpty())
        ThemeManager.setLearnedPhrasesEnabled(service, true)
        LearnedPhraseManager.flush()
        LearnedPhraseManager.invalidateCache()
        assertEquals(listOf("樓"), suggestions("落"))
        LearnedPhraseManager.clearAll(service)
        LearnedPhraseManager.invalidateCache()
        assertTrue(suggestions("落").isEmpty())
    }

    @Test fun privateFieldsNeitherTrainNorDisplayExistingLearnedData() {
        select("落樓")
        val excluded = listOf(
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; hintText = "Username" },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER })
        for (info in excluded) {
            field(info)
            select("落")
            select("雨")
            assertTrue(ReflectionHelpers.getField<Set<String>>(service, "learnedAssociatedCandidates").isEmpty())
            assertEquals(listOf("樓"), suggestions("落"))
        }
    }

    @Test fun recentCandidateHistoryAlsoHonorsSensitiveFieldExclusions() {
        val recent = com.awcjack.dualquickime.data.RecentCandidateManager
        recent.clearAll(service)
        ThemeManager.setRecentCandidatesEnabled(service, true)
        for (info in listOf(
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; hintText = "Username" },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; hintText = "Verification code" },
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING })) {
            field(info)
            connection.setComposingText("lok", 1)
            ReflectionHelpers.setField(service, "composition",
                com.awcjack.dualquickime.data.CompositionState(rawKeys = "lok", candidates = listOf("落")))
            select("落")
            assertTrue(recent.getRecentForCode(service, "lok").isEmpty())
        }
        field(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        connection.setComposingText("lok", 1)
        ReflectionHelpers.setField(service, "composition",
            com.awcjack.dualquickime.data.CompositionState(rawKeys = "lok", candidates = listOf("落")))
        select("落")
        assertEquals(listOf("落"), recent.getRecentForCode(service, "lok"))
        recent.clearAll(service)
        ThemeManager.setRecentCandidatesEnabled(service, false)
    }

    private fun layout(view: KeyboardView) {
        view.measure(View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun slots(view: KeyboardView): List<TextView> {
        layout(view)
        return ReflectionHelpers.getField<List<TextView>>(view, "candidateSlots")
            .sortedBy { it.left }
    }
    private fun rowTexts(view: KeyboardView): List<String> {
        val adapter = ReflectionHelpers.getField<Any>(view, "candidateAdapter")
        return ReflectionHelpers.getField<List<Any>>(adapter, "items").map {
            ReflectionHelpers.getField<String>(it, "text")
        }
    }
    private fun texts(view: View): List<TextView> {
        if (view is KeyboardView) layout(view)
        return (if (view is TextView) listOf(view) else emptyList()) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
    }

    @Test fun learnedColorsAndAccessibleLabelSurviveStripAndGridAndMetadataChange() {
        val view = KeyboardView(service)
        view.setCandidates(listOf("樓", "雨"), setOf("樓"))
        val colors = ThemeManager.getColors(service)
        val learned = texts(view).first { it.text == "樓" }
        assertEquals(colors.learnedCandidateText, learned.currentTextColor)
        assertEquals(service.getString(R.string.learned_candidate_accessibility, "樓"), learned.contentDescription)
        view.showCandidateGrid(listOf("樓", "雨"))
        assertEquals(colors.learnedCandidateText, texts(view).first { it.text == "樓" }.currentTextColor)
        val grid = CandidateGridView(service)
        grid.setCandidates(listOf("樓", "雨"), learned = setOf("雨"))
        assertEquals(colors.learnedCandidateText, texts(grid).first { it.text == "雨" }.currentTextColor)
        val strip = KeyboardView(service)
        strip.setCandidates(listOf("樓"), setOf("樓"))
        strip.setCandidates(listOf("樓"))
        assertEquals(colors.candidateText, texts(strip).first { it.text == "樓" }.currentTextColor)
    }

    @Test fun fiveSuggestionStripExpandsInlineWithoutLosingLearnedColors() {
        val view = KeyboardView(service)
        val all = listOf("樓", "雨", "街", "車", "山", "海", "地", "雪")
        view.setCandidates(all, all.toSet())
        val slots = slots(view)
        assertEquals(all.take(5), slots.map { it.text.toString() })
        var arrow = texts(view).first { it.text == "›" }
        assertEquals("›", arrow.text.toString())
        assertEquals(View.VISIBLE, arrow.visibility)
        assertEquals(service.getString(R.string.show_more_learned_suggestions), arrow.contentDescription)
        view.measure(View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertTrue(slots.all { it.width > 0 })
        val learnedWidth = slots.first().width
        val learnedHeight = slots.first().height
        val learnedSize = slots.first().textSize
        view.setCandidates(listOf("樓"))
        view.measure(View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val normal = slots(view).first()
        assertEquals(learnedWidth, normal.width)
        assertEquals(learnedHeight, normal.height)
        assertEquals(learnedSize, normal.textSize, 0.0f)
        view.setCandidates(all, all.toSet())
        arrow = texts(view).first { it.text == "›" }
        var selected = ""
        view.setOnCandidateSelectedListener { selected = it }
        arrow.performClick()
        val sixth = texts(view).first { it.text == "海" }
        assertEquals(ThemeManager.getColors(service).learnedCandidateText, sixth.currentTextColor)
        sixth.performClick()
        assertEquals("海", selected)
        view.setCandidates(all)
        assertEquals(all, slots(view).map { it.text.toString() })
        assertFalse(ReflectionHelpers.getField<TextView>(view, "pageIndicator").text == "›")
    }

    @Test fun learnedLimitDoesNotHideBundledOrTypedSuggestionsOrStretchSinglePill() {
        val view = KeyboardView(service)
        val learned = listOf("樓", "雨", "街", "車", "山", "海")
        val bundled = listOf("人", "會", "用", "的", "話", "知", "我")
        view.setCandidates(learned + bundled, learned.toSet())
        assertEquals(learned.take(5) + bundled,
            slots(view).map { it.text.toString() })
        assertEquals(learned.take(5) + listOf("›") + bundled,
            rowTexts(view))
        texts(view).first { it.text == "›" }.performClick()
        assertEquals(learned + listOf("‹") + bundled,
            rowTexts(view))
        texts(view).first { it.text == "‹" }.performClick()
        assertEquals(learned.take(5) + listOf("›") + bundled,
            rowTexts(view))
        view.setCandidates(learned + bundled)
        assertEquals(learned + bundled,
            slots(view).map { it.text.toString() })
        view.setCandidates(listOf("樓"), setOf("樓"))
        val slot = slots(view).first()
        assertEquals(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, slot.layoutParams.width)
        assertEquals(View.GONE, ReflectionHelpers.getField<TextView>(view, "pageIndicator").visibility)
    }

    @Test fun fullCandidateGridIncludesHiddenLearnedChoicesInBothStripStates() {
        val learned = listOf("樓", "雨", "街", "車", "山", "海", "地", "雪")
        val bundled = listOf("人", "會", "用")
        val all = learned + bundled
        for (expanded in listOf(false, true)) {
            val view = KeyboardView(service)
            view.setCandidates(all, learned.toSet())
            if (expanded) texts(view).first { it.text == "›" }.performClick()
            view.setOnPageIndicatorClickedListener { view.showCandidateGrid(all) }
            ReflectionHelpers.getField<TextView>(view, "pageIndicator").performClick()
            val displayed = texts(view)
            assertTrue(all.all { candidate -> displayed.any { it.text == candidate } })
            assertTrue(learned.all { candidate -> displayed.first { it.text == candidate }.currentTextColor ==
                ThemeManager.getColors(service).learnedCandidateText })
        }
    }

    @Test fun changedHiddenRankingIsUsedWhenExpandingAnUnchangedPreview() {
        val view = KeyboardView(service)
        val first = listOf("樓", "雨", "街", "車", "山", "海", "地")
        val second = first.take(5) + first.drop(5).reversed()
        view.setCandidates(first, first.toSet())
        view.setCandidates(second, second.toSet())
        texts(view).first { it.text == "›" }.performClick()
        assertEquals(second,
            slots(view).map { it.text.toString() })
    }

    @Test fun settingsCanToggleAndClearOnlyLearnedEntries() {
        select("落樓")
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val toggle = activity.findViewById<SwitchCompat>(R.id.switchLearnedPhrases)
        assertTrue(toggle.isChecked)
        toggle.isChecked = false
        assertFalse(ThemeManager.getLearnedPhrasesEnabled(activity))
        assertFalse(ThemeManager.getLearnedPhrasesEnabled(service))
        toggle.isChecked = true
        activity.findViewById<View>(R.id.btnClearLearnedPhrases).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        LearnedPhraseManager.invalidateCache()
        assertTrue(suggestions("落").isEmpty())
    }
}
