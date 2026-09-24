package com.awcjack.dualquickime.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Local, bounded decimal calculator. Equals never writes to the editor by itself. */
class CalculatorEngine {
    var display: String = "0"
        private set
    private var left: BigDecimal? = null
    private var operation: Char? = null
    private var startNewNumber = true
    private var hasResult = false

    val result: String? get() = display.takeIf { hasResult && it != "Error" }
    val summary: String get() = if (left != null && operation != null)
        "${format(left!!)} $operation ${if (startNewNumber) "" else display}".trimEnd()
    else display

    fun clear() {
        display = "0"
        left = null
        operation = null
        startNewNumber = true
        hasResult = false
    }

    fun digit(value: Char) {
        if (value !in '0'..'9') return
        if (display == "Error") clear()
        if (startNewNumber) {
            display = value.toString()
            startNewNumber = false
        } else if (display.filter(Char::isDigit).length < 16) {
            display = when (display) {
                "0" -> value.toString()
                "-0" -> "-$value"
                else -> display + value
            }
        }
        hasResult = false
    }

    fun decimal() {
        if (display == "Error") clear()
        if (startNewNumber) {
            display = "0."
            startNewNumber = false
        } else if ('.' !in display) display += "."
        hasResult = false
    }

    fun toggleSign() {
        if (display == "Error") return
        val wasResult = hasResult
        display = if (startNewNumber && !hasResult) "-0"
            else if (display.startsWith('-')) display.drop(1) else "-$display"
        startNewNumber = wasResult
        hasResult = wasResult
    }

    fun backspace() {
        if (display == "Error") { clear(); return }
        if (startNewNumber && !hasResult) return
        startNewNumber = false
        display = display.dropLast(1).takeIf { it.isNotEmpty() && it != "-" } ?: "0"
        hasResult = false
    }

    fun operator(value: Char) {
        if (value !in listOf('+', '-', '×', '÷') || display == "Error") return
        if (left != null && operation != null && !startNewNumber) {
            evaluate()
            if (display == "Error") return
        }
        left = display.toBigDecimalOrNull()
        operation = value
        startNewNumber = true
        hasResult = false
    }

    fun equals() {
        if (left == null || operation == null || startNewNumber || display == "Error") return
        evaluate()
        left = null
        operation = null
        startNewNumber = true
        hasResult = display != "Error"
    }

    private fun evaluate() {
        val a = left ?: return
        val b = display.toBigDecimalOrNull() ?: return
        val value = when (operation) {
            '+' -> a.add(b)
            '-' -> a.subtract(b)
            '×' -> a.multiply(b)
            '÷' -> if (b.signum() == 0) null else a.divide(b, 16, RoundingMode.HALF_UP)
            else -> null
        }
        display = value?.let(::format)?.takeIf { it.length <= 24 } ?: "Error"
    }

    private fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
