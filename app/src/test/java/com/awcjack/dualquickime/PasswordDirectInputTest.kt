package com.awcjack.dualquickime

import android.text.InputType
import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.ui.KeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PasswordDirectInputTest {
    private class HiddenConnection(view: View) : BaseInputConnection(view, true) {
        var composingCalls = 0
        var finishCalls = 0
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composingCalls++
            return false
        }
        override fun finishComposingText(): Boolean { finishCalls++; return true }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
    }

    @Test fun passwordVariationsCommitDirectlyWithoutCandidatesOrComposing() {
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )) {
            val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
            try {
                val connection = HiddenConnection(View(service))
                Selection.setSelection(connection.editable!!, 0)
                ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
                val info = EditorInfo().apply { inputType = type }
                ReflectionHelpers.setField(service, "mInputEditorInfo", info)
                service.onStartInput(info, false)
                val keyboard = service.onCreateInputView() as KeyboardView
                fun key(event: KeyboardView.KeyEvent) {
                    HkInputMethodService::class.java.getDeclaredMethod("handleKeyEvent", KeyboardView.KeyEvent::class.java)
                        .apply { isAccessible = true }.invoke(service, event)
                }
                key(KeyboardView.KeyEvent.Letter('A'))
                key(KeyboardView.KeyEvent.Letter('k'))
                val eye = ReflectionHelpers.getField<android.widget.TextView>(keyboard, "maskToggle")
                assertEquals(View.VISIBLE, eye.visibility)
                eye.performClick()
                assertFalse(ReflectionHelpers.getField<Boolean>(service, "isPasswordMaskEnabled"))
                ReflectionHelpers.getField<(String) -> Unit>(keyboard, "onCandidateSelected")("大")
                assertEquals("Ak", connection.editable.toString())
                service.onUpdateSelection(0, 0, 2, 2, -1, -1)
                key(KeyboardView.KeyEvent.Number(3, true))
                key(KeyboardView.KeyEvent.Symbol('@'))
                key(KeyboardView.KeyEvent.Space)
                assertEquals("Ak3@ ", connection.editable.toString())
                key(KeyboardView.KeyEvent.Backspace)
                key(KeyboardView.KeyEvent.Letter('z'))
                assertEquals("Ak3@z", connection.editable.toString())
                val state = ReflectionHelpers.getField<CompositionState>(service, "composition")
                assertEquals("Ak3@z", state.rawKeys)
                assertTrue(state.candidates.isEmpty())
                assertEquals(0, connection.composingCalls)
                key(KeyboardView.KeyEvent.Enter)
                assertEquals(0, connection.finishCalls)
                assertEquals(0, connection.composingCalls)
            } finally { service.onDestroy() }
        }
    }
}
