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
    private var equation: String? = null

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
        equation = null
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
        equation = null
    }

    fun decimal() {
        if (display == "Error") clear()
        if (startNewNumber) {
            display = "0."
            startNewNumber = false
        } else if ('.' !in display) display += "."
        hasResult = false
        equation = null
    }

    fun toggleSign() {
        if (display == "Error") return
        val wasResult = hasResult
        display = if (startNewNumber && !hasResult) "-0"
            else if (display.startsWith('-')) display.drop(1) else "-$display"
        startNewNumber = wasResult
        hasResult = wasResult
        equation = null
    }

    fun backspace() {
        if (display == "Error") { clear(); return }
        if (startNewNumber && !hasResult) return
        startNewNumber = false
        display = display.dropLast(1).takeIf { it.isNotEmpty() && it != "-" } ?: "0"
        hasResult = false
        equation = null
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
        equation = null
    }

    fun equals() {
        if (display == "Error") return
        if (left == null || operation == null) {
            if (!startNewNumber) {
                display = display.toBigDecimalOrNull()?.let(::format) ?: "Error"
                hasResult = display != "Error"
                startNewNumber = true
                equation = null
            }
            return
        }
        if (startNewNumber) return
        val storedLeft = format(left!!)
        val storedOperation = operation!!
        val storedRight = display.toBigDecimalOrNull()?.let(::format) ?: display
        evaluate()
        left = null
        operation = null
        startNewNumber = true
        hasResult = display != "Error"
        equation = if (hasResult) "$storedLeft ${plainOperator(storedOperation)} $storedRight = $display" else null
    }

    /** Pin/Insert/Eq first completes the current calculation, just like tapping equals. */
    fun settledResult(): String? {
        equals()
        return result
    }

    /** The completed formula, such as "100 * 1.1 = 110". Null when there is no operator. */
    fun settledEquation(): String? {
        equals()
        return equation
    }

    private fun plainOperator(value: Char) = when (value) {
        '×' -> '*'
        '÷' -> '/'
        else -> value
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
