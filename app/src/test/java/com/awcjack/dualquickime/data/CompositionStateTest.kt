package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionStateTest {
    @Test fun swipingBackReturnsToExactDynamicOffset() {
        val first = CompositionState(candidates = (1..20).map(Int::toString), pageSize = 6)
            .withDisplayedCount(4)
        val second = first.nextPage().withDisplayedCount(3)
        val third = second.nextPage()
        assertEquals(7, third.displayOffset)
        assertEquals(4, third.previousPage().displayOffset)
        assertEquals(0, third.previousPage().previousPage().displayOffset)
    }
}
