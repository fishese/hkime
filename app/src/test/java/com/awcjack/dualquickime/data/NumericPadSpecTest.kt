package com.awcjack.dualquickime.data

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericPadSpecTest {
    @Test fun numberFlagsControlVisiblePunctuation() {
        fun symbols(type: Int) = NumericPadSpec.fromInputType(type)!!.rows.flatten()
            .filter { it.action == NumericPadSpec.Action.SYMBOL }.map { it.label }
        assertEquals(emptyList<String>(), symbols(InputType.TYPE_CLASS_NUMBER))
        assertEquals(listOf("-"), symbols(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED))
        assertEquals(listOf("."), symbols(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertEquals(listOf("-", "."), symbols(InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL))
    }

    @Test fun phoneAndDatetimeHaveAppropriateSeparators() {
        val phone = NumericPadSpec.fromInputType(InputType.TYPE_CLASS_PHONE)!!
        assertEquals(listOf("+", "*", "#"), phone.rows.flatten()
            .filter { it.action == NumericPadSpec.Action.SYMBOL }.map { it.label })
        assertTrue(NumericPadSpec.fromInputType(InputType.TYPE_CLASS_DATETIME or
            InputType.TYPE_DATETIME_VARIATION_TIME)!!.rows.flatten().any { it.label == ":" })
        assertFalse(NumericPadSpec.fromInputType(InputType.TYPE_CLASS_DATETIME or
            InputType.TYPE_DATETIME_VARIATION_TIME)!!.rows.flatten().any { it.label == "/" })
    }

    @Test fun passwordAndTextFieldDetection() {
        assertTrue(NumericPadSpec.fromInputType(InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_VARIATION_PASSWORD)!!.password)
        assertNull(NumericPadSpec.fromInputType(InputType.TYPE_CLASS_TEXT))
    }
}
