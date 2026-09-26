package com.awcjack.dualquickime

import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Exercise the service against an editable InputConnection, including actual composing spans. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class InputSessionRegressionTest {
    private lateinit var service: HkInputMethodService
    private lateinit var connection: ExcerptConnection

    private class ExcerptConnection(view: View) : BaseInputConnection(view, true) {
        var exposesExcerpt = true
        val caretKeys = mutableListOf<Int>()

        override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) caretKeys += event.keyCode
            return true
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            if (!exposesExcerpt) return null
            val buffer = editable!!
            val start = Selection.getSelectionStart(buffer)
            val end = Selection.getSelectionEnd(buffer)
            // Simulate editors that return only a small excerpt, even for a long document.
            val offset = (minOf(start, end) - 16).coerceAtLeast(0)
            val limit = (maxOf(start, end) + 16).coerceAtMost(buffer.length)
            return ExtractedText().apply {
                text = buffer.subSequence(offset, limit).toString()
                startOffset = offset
                selectionStart = start - offset
                selectionEnd = end - offset
                partialStartOffset = -1
                partialEndOffset = -1
            }
        }

        fun reset(text: String) {
            editable!!.clear()
            editable!!.append(text)
            Selection.setSelection(editable!!, text.length)
        }

        fun moveTo(index: Int) { Selection.setSelection(editable!!, index) }
        val cursor: Int get() = Selection.getSelectionEnd(editable!!)
        val text: String get() = editable.toString()
    }

    @Before fun setup() {
        service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        ThemeManager.setRecentCandidatesEnabled(service, false)
        ThemeManager.setIgnoreSpaceAfterLatin(service, false)
        ThemeManager.setRestoreSpaceBetweenLatin(service, true)
        ThemeManager.setSpaceAfterEnglishCandidate(service, true)
        connection = ExcerptConnection(View(service))
        connection.reset("")
        ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
    }

    @After fun tearDown() { service.onDestroy() }

    private fun key(event: KeyboardView.KeyEvent) {
        val method = HkInputMethodService::class.java.getDeclaredMethod("handleKeyEvent", KeyboardView.KeyEvent::class.java)
        method.isAccessible = true
        method.invoke(service, event)
    }

    private fun call(name: String) {
        HkInputMethodService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
    }

    private fun composing(raw: String, swipe: Boolean = false) {
        connection.setComposingText(raw, 1)
        ReflectionHelpers.setField(service, "composition", CompositionState(rawKeys = raw, candidates = listOf(raw)))
        ReflectionHelpers.setField(service, "pendingSwipeChoice", swipe)
    }

    private fun selectDomain(domain: String) {
        HkInputMethodService::class.java.getDeclaredMethod("handleEmailSuggestionSelected", String::class.java)
            .apply { isAccessible = true }.invoke(service, domain)
    }

    @Test fun settingsOpensAndSlidersKeepTheirRealRangesOnAndroid7() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val container = activity.get().findViewById<android.widget.LinearLayout>(R.id.keyboardAppearanceContainer)
            val slider = (0 until container.childCount).map(container::getChildAt)
                .filterIsInstance<android.widget.SeekBar>().first()
            slider.progress = slider.max
            val labels = (0 until container.childCount).map(container::getChildAt)
                .filterIsInstance<android.widget.TextView>().map { it.text.toString() }
            assertTrue(labels.contains("${ThemeManager.KEY_HEIGHT_MAX} dp"))
            slider.progress = 0
            assertTrue((0 until container.childCount).map(container::getChildAt)
                .filterIsInstance<android.widget.TextView>().any { it.text.toString() == "${ThemeManager.KEY_HEIGHT_MIN} dp" })
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun cursorDragUsesTheDocumentOffsetForLongText() {
        connection.reset("a".repeat(12000) + "👍b")
        key(KeyboardView.KeyEvent.MoveCursor(-1))
        assertEquals(12002, connection.cursor)
        key(KeyboardView.KeyEvent.MoveCursor(-1))
        assertEquals(12000, connection.cursor)
        key(KeyboardView.KeyEvent.MoveCursor(1))
        assertEquals(12002, connection.cursor)
    }

    @Test fun cursorDragUsesNativeCaretKeysWhenEditorDoesNotProvideOffsets() {
        connection.reset("abc")
        connection.exposesExcerpt = false
        key(KeyboardView.KeyEvent.MoveCursor(-2))
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT), connection.caretKeys)
        assertEquals(3, connection.cursor)
    }

    @Test fun spaceConfirmsEnglishSwipeWithOnlyOneSeparator() {
        composing("hello", swipe = true)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello ", connection.text)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello  ", connection.text)
    }

    @Test fun spaceStillSeparatesSwipeWhenCandidateAutoSpaceIsOff() {
        ThemeManager.setSpaceAfterEnglishCandidate(service, false)
        composing("hello", swipe = true)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello ", connection.text)
    }

    @Test fun deferredSpaceReturnsForContinuedTypingAndBeforeTheFirstDigit() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        // A delayed callback for our preceding composing edit must not cancel the held space.
        service.onUpdateSelection(0, 0, 4, 4, -1, -1)
        composing("world")
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello world", connection.text)
        key(KeyboardView.KeyEvent.Number(2))
        key(KeyboardView.KeyEvent.Number(0))
        assertEquals("hello world 20", connection.text)
    }

    @Test fun movingTheCaretCancelsDeferredSpaceBeforeTypingElsewhere() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        connection.moveTo(0)
        service.onUpdateSelection(5, 5, 0, 0, -1, -1)
        composing("x")
        key(KeyboardView.KeyEvent.Space)
        assertEquals("xhello", connection.text)
    }

    @Test fun deferredSpaceIsValidatedBeforeTypingEvenWithoutACaretCallback() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        connection.moveTo(0)
        key(KeyboardView.KeyEvent.Letter('x'))
        key(KeyboardView.KeyEvent.Space)
        assertEquals("xhello", connection.text)
    }

    @Test fun movingAwayDuringCompositionDoesNotRestoreSpaceOrMoveCaretBack() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        composing("world")
        connection.moveTo(0)
        service.onUpdateSelection(10, 10, 0, 0, 5, 10)
        assertEquals("helloworld", connection.text)
        assertEquals(0, connection.cursor)
    }

    @Test fun emailSuggestionChecksItsPrefixEvenBeforeSelectionCallbackArrives() {
        connection.reset("abc@")
        call("enterEmailSuggestionsMode")
        connection.moveTo(0)
        selectDomain("gmail.com")
        assertEquals("abc@", connection.text)
        assertEquals(0, connection.cursor)
    }

    @Test fun validEmailSuggestionsSurviveTypingAndOwnSelectionUpdates() {
        connection.reset("abc@")
        call("enterEmailSuggestionsMode")
        key(KeyboardView.KeyEvent.Letter('g'))
        service.onUpdateSelection(4, 4, 5, 5, -1, -1)
        selectDomain("gmail.com")
        assertEquals("abc@gmail.com", connection.text)
    }

    @Test fun movingToAnotherIdenticalEmailPrefixDoesNotReuseSuggestions() {
        connection.reset("abc@ abc@")
        call("enterEmailSuggestionsMode")
        connection.moveTo(4)
        selectDomain("gmail.com")
        assertEquals("abc@ abc@", connection.text)
    }

    @Test fun movingCaretClearsEmailModeSoTypingStartsANewComposition() {
        connection.reset("abc@")
        call("enterEmailSuggestionsMode")
        connection.moveTo(0)
        service.onUpdateSelection(4, 4, 0, 0, -1, -1)
        assertFalse(ReflectionHelpers.getField<Boolean>(service, "isEmailSuggestionsMode"))
        key(KeyboardView.KeyEvent.Letter('x'))
        key(KeyboardView.KeyEvent.Space)
        assertEquals("x abc@", connection.text)
    }
}
