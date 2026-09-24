package com.awcjack.dualquickime.data

import android.text.InputType

/** Keys exposed by a numeric editor. Unaccepted punctuation is not put on the main pad. */
data class NumericPadSpec(
    val kind: Kind,
    val password: Boolean = false,
) {
    enum class Kind { INTEGER, SIGNED, DECIMAL, SIGNED_DECIMAL, PHONE, DATE, TIME, DATETIME }
    enum class Action { DIGIT, SYMBOL, BACKSPACE, ENTER, HIDE, LETTERS }
    data class Key(val label: String, val action: Action, val weight: Float = 1f)

    val rows: List<List<Key>> get() {
        fun digit(value: String, weight: Float = 1f) = Key(value, Action.DIGIT, weight)
        fun symbol(value: String) = Key(value, Action.SYMBOL)
        val hide = Key("⌄", Action.HIDE)
        val letters = Key("ABC", Action.LETTERS)
        val backspace = Key("⌫", Action.BACKSPACE)
        val enter = Key("↵", Action.ENTER)
        val topAction = if (kind == Kind.PHONE) symbol("+") else hide
        val middleAction = if (kind == Kind.PHONE) hide else letters
        val bottom = when (kind) {
            Kind.INTEGER -> listOf(digit("0", 3f), enter)
            Kind.SIGNED -> listOf(symbol("-"), digit("0", 2f), enter)
            Kind.DECIMAL -> listOf(digit("0", 2f), symbol("."), enter)
            Kind.SIGNED_DECIMAL -> listOf(symbol("-"), digit("0"), symbol("."), enter)
            Kind.PHONE -> listOf(symbol("*"), digit("0"), symbol("#"), enter)
            Kind.DATE -> listOf(symbol("/"), digit("0"), symbol("-"), enter)
            Kind.TIME -> listOf(digit("0", 2f), symbol(":"), enter)
            Kind.DATETIME -> listOf(symbol("/"), digit("0"), symbol(":"), enter)
        }
        return listOf(
            listOf(digit("1"), digit("2"), digit("3"), topAction),
            listOf(digit("4"), digit("5"), digit("6"), middleAction),
            listOf(digit("7"), digit("8"), digit("9"), backspace),
            bottom,
        )
    }

    companion object {
        fun fromInputType(inputType: Int): NumericPadSpec? {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> {
                    val signed = (inputType and InputType.TYPE_NUMBER_FLAG_SIGNED) != 0
                    val decimal = (inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0
                    val kind = when {
                        signed && decimal -> Kind.SIGNED_DECIMAL
                        signed -> Kind.SIGNED
                        decimal -> Kind.DECIMAL
                        else -> Kind.INTEGER
                    }
                    NumericPadSpec(kind, variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                }
                InputType.TYPE_CLASS_PHONE -> NumericPadSpec(Kind.PHONE)
                InputType.TYPE_CLASS_DATETIME -> NumericPadSpec(when (variation) {
                    InputType.TYPE_DATETIME_VARIATION_DATE -> Kind.DATE
                    InputType.TYPE_DATETIME_VARIATION_TIME -> Kind.TIME
                    else -> Kind.DATETIME
                })
                else -> null
            }
        }
    }
}
