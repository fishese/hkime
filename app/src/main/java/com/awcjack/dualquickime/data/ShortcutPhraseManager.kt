package com.awcjack.dualquickime.data

import android.content.Context

/** Stores the ten user-defined phrases assigned to long-press 0-9. */
object ShortcutPhraseManager {
    private const val PREFS = "shortcut_phrases"

    fun get(context: Context, digit: Int): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(digit), "")
            .orEmpty()

    fun set(context: Context, digit: Int, phrase: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(digit), phrase)
            .apply()
    }

    private fun key(digit: Int): String {
        require(digit in 0..9)
        return "shortcut_$digit"
    }
}
