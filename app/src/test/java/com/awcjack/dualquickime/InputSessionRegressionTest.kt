package com.awcjack.dualquickime

import android.text.Selection
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
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
        ThemeManager.setEnglishSpellCheck(service, true)
        ThemeManager.setCantoneseTypoRecovery(service, true)
        ThemeManager.setCangjieTypoRecovery(service, true)
        ThemeManager.setMethodEnglish(service, true)
        ThemeManager.setMethodCantonese(service, true)
        ThemeManager.setMethodCangjie(service, true)
        ThemeManager.setMethodQuick(service, true)
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

    private fun currentCandidates(): List<String> =
        ReflectionHelpers.getField<CompositionState>(service, "composition").candidates

    private fun typeLetters(text: String) {
        for (letter in text) key(KeyboardView.KeyEvent.Letter(letter))
    }

    private fun ordinaryTextField(inputType: Int = InputType.TYPE_CLASS_TEXT) {
        ReflectionHelpers.setField(service, "mInputEditorInfo", EditorInfo().apply { this.inputType = inputType })
    }

    @Test fun reportedTyposAppearInOrdinaryTextWithoutChangingTheLiteralBuffer() {
        ordinaryTextField()
        typeLetters("sanf")
        assertEquals("sanf", connection.text)
        assertTrue(currentCandidates().take(6).containsAll(listOf("sang", "生")))
        call("clearComposition")
        connection.reset("")
        typeLetters("stah")
        assertEquals("stah", connection.text)
        val choices = currentCandidates()
        assertTrue(choices.take(6).containsAll(listOf("stab", "stag", "stay")))
        for ((index, candidate) in choices.withIndex()) {
            if (candidate.any { it.code in 0x4e00..0x9fff } && candidate.codePointCount(0, candidate.length) > 1) {
                assertTrue("stay before phrase $candidate", choices.indexOf("stay") < index)
            }
        }
        key(KeyboardView.KeyEvent.Space)
        assertEquals("stah ", connection.text)
        connection.reset("")
        typeLetters("stah")
        HkInputMethodService::class.java.getDeclaredMethod("commitCandidate", String::class.java)
            .apply { isAccessible = true }.invoke(service, "stay")
        assertEquals("stay ", connection.text)
    }

    @Test fun shortTyposRespectEachLanguageToggleAndRefreshAfterBackspace() {
        ordinaryTextField()
        ThemeManager.setMethodCangjie(service, false)
        ThemeManager.setMethodQuick(service, false)
        ThemeManager.setMethodEnglish(service, false)
        typeLetters("sanf")
        assertTrue("生" in currentCandidates())
        assertFalse("sang" in currentCandidates())
        call("clearComposition"); connection.reset("")
        ThemeManager.setMethodEnglish(service, true)
        ThemeManager.setMethodCantonese(service, false)
        typeLetters("stas")
        key(KeyboardView.KeyEvent.Backspace)
        key(KeyboardView.KeyEvent.Letter('h'))
        assertEquals("stah", connection.text)
        assertTrue("stay" in currentCandidates())
    }

    @Test fun longTyposAndRetainedTailReachTheServiceCandidateList() {
        ordinaryTextField()
        for ((typed, target) in listOf("compiter" to "computer", "infornation" to "information",
                "polytetrafluoroethyleme" to "polytetrafluoroethylene")) {
            call("clearComposition"); connection.reset("")
            typeLetters(typed)
            assertTrue(typed, target in currentCandidates())
            assertEquals(typed, connection.text)
        }
        call("clearComposition"); connection.reset("")
        typeLetters("nfo")
        val state = ReflectionHelpers.getField<CompositionState>(service, "composition")
        assertTrue(state.candidates.size > 6)
        val later = state.copy(pageSize = 6).withDisplayedCount(6).nextPage()
        assertEquals(state.candidates.drop(6).take(6), later.currentPageCandidates)
    }

    @Test fun ordinaryTextRecoveryHonorsKeyboardOptionsWhileUriAndSensitiveContextsSuppressIt() {
        for (type in listOf(InputType.TYPE_CLASS_TEXT,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)) {
            call("clearComposition"); connection.reset("")
            ordinaryTextField(type)
            typeLetters("stah")
            assertEquals((type and InputType.TYPE_MASK_VARIATION) != InputType.TYPE_TEXT_VARIATION_URI,
                "stay" in currentCandidates())
        }
        ordinaryTextField()
        val allowed = HkInputMethodService::class.java.getDeclaredMethod("canSuggestTypos")
            .apply { isAccessible = true }
        for (field in listOf("isPasswordField", "isEmailField", "isUsernameField", "pendingSwipeChoice")) {
            ReflectionHelpers.setField(service, field, true)
            assertFalse(field, allowed.invoke(service) as Boolean)
            ReflectionHelpers.setField(service, field, false)
        }
        ReflectionHelpers.setField(service, "swipeEnglishWords", listOf("stay"))
        assertFalse(allowed.invoke(service) as Boolean)
        ReflectionHelpers.setField(service, "swipeEnglishWords", emptyList<String>())
        assertTrue(allowed.invoke(service) as Boolean)
    }

    @Test fun googleKeepNoSuggestionsFieldStillOffersExplicitTypoChoices() {
        // Actual EditorInfo observed on the connected phone while the report reproduced.
        ordinaryTextField(0xac001)
        typeLetters("sanf")
        assertTrue(currentCandidates().take(6).containsAll(listOf("sang", "生")))
        assertEquals("sanf", connection.text)
        call("clearComposition")
        connection.reset("")
        typeLetters("stah")
        assertTrue("stay" in currentCandidates().take(6))
        assertEquals("stah", connection.text)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("stah ", connection.text)
    }

    @Test fun nearbyTyposAreSuggestedWithoutReplacingComposingText() {
        typeLetters("oftrm")
        assertTrue("often" in currentCandidates())
        assertEquals("oftrm", connection.text)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("oftrm ", connection.text)
    }

    @Test fun nfoRecoversNgoAheadOfItsExactPhraseShorthand() {
        typeLetters("nfo")
        val candidates = currentCandidates()
        assertTrue("我" in candidates)
        assertEquals("nfo", connection.text)
        val phrase = candidates.indexOf("年貨")
        if (phrase >= 0) assertTrue(candidates.indexOf("我") < phrase)
    }

    @Test fun EnglishAndChineseRecoveryTogglesAreIndependent() {
        ThemeManager.setMethodEnglish(service, false)
        ThemeManager.setMethodCangjie(service, false)
        ThemeManager.setMethodQuick(service, false)
        typeLetters("nfo")
        assertTrue("我" in currentCandidates())
        call("clearComposition")
        connection.reset("")
        ThemeManager.setCantoneseTypoRecovery(service, false)
        typeLetters("nfo")
        assertFalse("我" in currentCandidates())
    }

    @Test fun spellcheckOffAndSensitiveFieldsDoNotAddEnglishFixes() {
        ThemeManager.setEnglishSpellCheck(service, false)
        typeLetters("sohnds")
        assertFalse("sounds" in currentCandidates())
        ThemeManager.setEnglishSpellCheck(service, true)
        for (field in listOf("isPasswordField", "isEmailField", "isUsernameField")) {
            call("clearComposition")
            connection.reset("")
            ReflectionHelpers.setField(service, field, true)
            typeLetters("sohnds")
            assertFalse(field, "sounds" in currentCandidates())
            ReflectionHelpers.setField(service, field, false)
        }
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

    @Test fun usernameFieldsAreRecognizedFromEditorHintsAndLabels() {
        val classifier = HkInputMethodService::class.java
            .getDeclaredMethod("isUsernameInputField", EditorInfo::class.java)
            .apply { isAccessible = true }
        fun isUsername(info: EditorInfo) = classifier.invoke(service, info) as Boolean

        assertTrue(isUsername(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hintText = "Username"
        }))
        assertTrue(isUsername(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            label = "User name"
        }))
        assertTrue(isUsername(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hintText = "登入名稱"
        }))
        assertFalse(isUsername(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hintText = "Your name"
        }))
        assertFalse(isUsername(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hintText = "Username"
        }))
    }

    @Test fun punctuationAutoSpacingIsDisabledForPasswordEmailAndUsernameFields() {
        val insertSpace = HkInputMethodService::class.java
            .getDeclaredMethod("insertSpaceAfterHalfPunctuation", String::class.java)
            .apply { isAccessible = true }
        val startPendingSpace = HkInputMethodService::class.java
            .getDeclaredMethod("startPendingPunctuationSpace")
            .apply { isAccessible = true }
        val fieldFlags = listOf("isPasswordField", "isEmailField", "isUsernameField")

        for (field in fieldFlags) {
            for (punctuation in listOf('.', '!', ':')) {
                fieldFlags.forEach { ReflectionHelpers.setField(service, it, it == field) }

                connection.reset("hello$punctuation")
                insertSpace.invoke(service, "Next")
                assertEquals("$field: direct punctuation spacing", "hello$punctuation", connection.text)

                connection.reset("hello$punctuation")
                startPendingSpace.invoke(service)
                assertEquals("$field: provisional punctuation spacing", "hello$punctuation", connection.text)
                assertFalse(ReflectionHelpers.getField<Boolean>(service, "pendingPunctuationSpace"))
            }
        }

        fieldFlags.forEach { ReflectionHelpers.setField(service, it, false) }
        connection.reset("hello.")
        insertSpace.invoke(service, "Next")
        assertEquals("hello. ", connection.text)
    }

    @Test fun cursorDragUsesTheDocumentOffsetForLongText() {
        connection.reset("a".repeat(12000) + "👍b")
        key(KeyboardView.KeyEvent.StartCursorDrag)
        key(KeyboardView.KeyEvent.MoveCursor(-1))
        assertEquals(12002, connection.cursor)
        key(KeyboardView.KeyEvent.MoveCursor(-1))
        assertEquals(12000, connection.cursor)
        key(KeyboardView.KeyEvent.MoveCursor(1))
        assertEquals(12002, connection.cursor)
        key(KeyboardView.KeyEvent.EndCursorDrag)
    }

    @Test fun cursorDragUsesNativeCaretKeysWhenEditorDoesNotProvideOffsets() {
        connection.reset("abc")
        connection.exposesExcerpt = false
        key(KeyboardView.KeyEvent.StartCursorDrag)
        key(KeyboardView.KeyEvent.MoveCursor(-2))
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT), connection.caretKeys)
        assertEquals(3, connection.cursor)
        key(KeyboardView.KeyEvent.EndCursorDrag)
    }

    @Test fun cursorDragStopsWhenTheFocusedEditorChanges() {
        connection.reset("original")
        val focusedElsewhere = ExcerptConnection(View(service)).apply { reset("other") }
        key(KeyboardView.KeyEvent.StartCursorDrag)
        ReflectionHelpers.setField(service, "mStartedInputConnection", focusedElsewhere)

        key(KeyboardView.KeyEvent.MoveCursor(-1))

        assertTrue(connection.caretKeys.isEmpty())
        assertTrue(focusedElsewhere.caretKeys.isEmpty())
        assertEquals("other".length, focusedElsewhere.cursor)
        key(KeyboardView.KeyEvent.EndCursorDrag)
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

    @Test fun latinSeparatorIsVisibleWhileTypingAndRemovedForChineseSelection() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        typeLetters("hello")
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello ", connection.text)
        typeLetters("ngo")
        assertEquals("hello ngo", connection.text)
        HkInputMethodService::class.java.getDeclaredMethod("commitCandidate", String::class.java)
            .apply { isAccessible = true }.invoke(service, "我")
        assertEquals("hello我", connection.text)
    }

    @Test fun explicitSeparatorSurvivesOpeningQuotesAndBrackets() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        for (mark in listOf('"', '(', '[', '{')) {
            connection.reset("")
            typeLetters("say")
            key(KeyboardView.KeyEvent.Space)
            key(KeyboardView.KeyEvent.Symbol(mark))
            assertEquals("say $mark", connection.text)
            typeLetters("hello")
            val closer = when (mark) { '"' -> '"'; '(' -> ')'; '[' -> ']'; else -> '}' }
            key(KeyboardView.KeyEvent.Symbol(closer))
            assertEquals("say ${mark}hello$closer ", connection.text)
            typeLetters("world")
            assertEquals("say ${mark}hello$closer world", connection.text)
            call("finishEnglishComposition")
        }
    }

    @Test fun backspaceCancelsVisibleSeparatorAndSecondSpaceConfirmsIt() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        typeLetters("hello")
        key(KeyboardView.KeyEvent.Space)
        key(KeyboardView.KeyEvent.Backspace)
        assertEquals("hello", connection.text)
        typeLetters("world")
        key(KeyboardView.KeyEvent.Space)
        key(KeyboardView.KeyEvent.Space)
        assertEquals("helloworld ", connection.text)
    }

    @Test fun openingQuoteAfterSentencePunctuationKeepsTheSeparator() {
        typeLetters("hello")
        key(KeyboardView.KeyEvent.Symbol('.'))
        key(KeyboardView.KeyEvent.Symbol('"'))
        assertEquals("hello. \"", connection.text)
    }

    @Test fun chineseSwipeSelectionRemovesProvisionalLatinSeparator() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        typeLetters("hello")
        key(KeyboardView.KeyEvent.Space)
        key(KeyboardView.KeyEvent.SwipeCode("ngo", emptyList(), emptyList()))
        HkInputMethodService::class.java.getDeclaredMethod("commitCandidate", String::class.java)
            .apply { isAccessible = true }.invoke(service, "我")
        assertEquals("hello我", connection.text)
    }

    @Test fun deferredSpaceReturnsForContinuedTypingAndBeforeTheFirstDigit() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        // A delayed callback for our preceding composing edit must not cancel the held space.
        service.onUpdateSelection(0, 0, 4, 4, -1, -1)
        typeLetters("world")
        key(KeyboardView.KeyEvent.Space)
        assertEquals("hello world ", connection.text)
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
        assertEquals("x hello ", connection.text)
    }

    @Test fun deferredSpaceIsValidatedBeforeTypingEvenWithoutACaretCallback() {
        ThemeManager.setIgnoreSpaceAfterLatin(service, true)
        composing("hello")
        key(KeyboardView.KeyEvent.Space)
        connection.moveTo(0)
        key(KeyboardView.KeyEvent.Letter('x'))
        key(KeyboardView.KeyEvent.Space)
        assertEquals("x hello ", connection.text)
    }

    @Test fun deletingCommittedPunctuationSpaceKeepsTheNextWordAttached() {
        connection.reset("test")
        key(KeyboardView.KeyEvent.Symbol('.'))
        key(KeyboardView.KeyEvent.Space)
        assertEquals("test. ", connection.text)

        key(KeyboardView.KeyEvent.Backspace)
        key(KeyboardView.KeyEvent.Letter('c'))
        key(KeyboardView.KeyEvent.Letter('o'))
        key(KeyboardView.KeyEvent.Letter('m'))

        assertEquals("test.com", connection.text)
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
