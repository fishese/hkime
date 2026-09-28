package com.awcjack.dualquickime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsTabsTest {
    @Test fun latinSentenceCaseIsOnTheInputTab() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val row = activity.findViewById<View>(R.id.latinSentenceCaseRow)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        assertFalse(isShown(row))
        content.findText(activity.getString(R.string.settings_tab_input)).performClick()
        assertTrue(isShown(row))
        assertTrue(isShown(content.findText(activity.getString(R.string.settings_english_options))))
        assertTrue(isShown(content.findText(activity.getString(R.string.settings_latin_sentence_case))))
    }

    private fun isShown(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun ViewGroup.findText(text: String): TextView {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.text == text) return child
            if (child is ViewGroup) child.findTextOrNull(text)?.let { return it }
        }
        error("Missing text $text")
    }

    private fun ViewGroup.findTextOrNull(text: String): TextView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.text == text) return child
            if (child is ViewGroup) child.findTextOrNull(text)?.let { return it }
        }
        return null
    }
}
