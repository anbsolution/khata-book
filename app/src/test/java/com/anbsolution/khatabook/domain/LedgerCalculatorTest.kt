package com.anbsolution.khatabook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerCalculatorTest {
    @Test
    fun calculatesBalanceInPaise() {
        assertEquals(7500L, LedgerCalculator.balancePaise(10000L, 2500L))
    }

    @Test
    fun rejectsZeroAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            LedgerCalculator.validateAmount(0L)
        }
    }
}
