package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEngineTest {
    @Test fun decimalMathIsExactForCommonTypedValues() {
        val calc = CalculatorEngine()
        calc.digit('0'); calc.decimal(); calc.digit('1'); calc.operator('+')
        calc.digit('0'); calc.decimal(); calc.digit('2'); calc.equals()
        assertEquals("0.3", calc.result)
        calc.equals()
        assertEquals("0.3", calc.result)
    }

    @Test fun negativeNumbersAndOperatorReplacement() {
        val calc = CalculatorEngine()
        calc.digit('5'); calc.operator('+'); calc.operator('-')
        calc.digit('2'); calc.toggleSign(); calc.equals()
        assertEquals("7", calc.result)
        calc.toggleSign()
        assertEquals("-7", calc.result)
    }

    @Test fun divideByZeroNeverProducesAnInsertableResult() {
        val calc = CalculatorEngine()
        calc.digit('2'); calc.operator('÷'); calc.digit('0'); calc.equals()
        assertEquals("Error", calc.display)
        assertNull(calc.result)
    }

    @Test fun inputAndOutputAreBounded() {
        val calc = CalculatorEngine()
        repeat(30) { calc.digit('9') }
        assertEquals(16, calc.display.length)
        calc.operator('×'); repeat(16) { calc.digit('9') }; calc.equals()
        assertEquals("Error", calc.display)
    }

    @Test fun resultIsNotAvailableUntilEquals() {
        val calc = CalculatorEngine()
        calc.digit('4'); calc.operator('+'); calc.digit('3')
        assertNull(calc.result)
        calc.equals()
        assertEquals("7", calc.result)
        calc.backspace()
        assertNull(calc.result)
    }

    @Test fun equationInsertsTheCompletedFormula() {
        val calc = CalculatorEngine()
        calc.digit('1'); calc.digit('0'); calc.digit('0')
        calc.operator('×'); calc.digit('1'); calc.decimal(); calc.digit('1')
        assertEquals("100 * 1.1 = 110", calc.settledEquation())
        assertEquals("110", calc.settledResult())
        val single = CalculatorEngine()
        single.digit('5')
        assertNull(single.settledEquation())
    }

    @Test fun keepAndInsertCanSettleAnExpressionOrSingleNumber() {
        val expression = CalculatorEngine()
        expression.digit('4'); expression.operator('+'); expression.digit('3')
        assertEquals("7", expression.settledResult())
        val single = CalculatorEngine()
        single.digit('5')
        assertEquals("5", single.settledResult())
        assertEquals("5", single.settledResult())
    }
}
