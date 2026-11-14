package com.anbsolution.khatabook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

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

    @Test
    fun reportPeriodsUseLocalCalendarBoundaries() {
        val now = GregorianCalendar(2026, Calendar.MAY, 16, 14, 30).apply {
            set(Calendar.MILLISECOND, 456)
        }.timeInMillis
        val monthStart = GregorianCalendar(2026, Calendar.MAY, 1).timeInMillis
        val weekStart = GregorianCalendar(2026, Calendar.MAY, 10).timeInMillis

        assertEquals(
            monthStart,
            LedgerCalculator.periodStartMillis(LedgerPeriod.THIS_MONTH, now)
        )
        assertEquals(
            weekStart,
            LedgerCalculator.periodStartMillis(LedgerPeriod.LAST_7_DAYS, now)
        )
        assertEquals(
            Long.MIN_VALUE,
            LedgerCalculator.periodStartMillis(LedgerPeriod.ALL_TIME, now)
        )
    }

    @Test
    fun periodFilterIncludesStartAndExcludesFutureEntries() {
        val now = GregorianCalendar(2026, Calendar.MAY, 16, 14, 30).timeInMillis
        val todayStart = LedgerCalculator.periodStartMillis(LedgerPeriod.TODAY, now)

        assertTrue(LedgerCalculator.isWithinPeriod(todayStart, todayStart, now))
        assertFalse(LedgerCalculator.isWithinPeriod(todayStart - 1, todayStart, now))
        assertFalse(LedgerCalculator.isWithinPeriod(now + 1, todayStart, now))
    }

    @Test
    fun transactionSearchMatchesNotesTypesAndDisplayedAmounts() {
        assertTrue(LedgerCalculator.matchesTransactionSearch("school", "School fees", "UDHAR", 125_050L))
        assertTrue(LedgerCalculator.matchesTransactionSearch("jama", "Received", "JAMA", 125_050L))
        assertTrue(LedgerCalculator.matchesTransactionSearch("1250.5", "Received", "JAMA", 125_050L))
        assertFalse(LedgerCalculator.matchesTransactionSearch("rent", "School fees", "UDHAR", 125_050L))
        assertFalse(LedgerCalculator.matchesTransactionSearch("100", "", "JAMA", 100L))
    }

    @Test
    fun blankTransactionSearchMatchesEveryEntry() {
        assertTrue(LedgerCalculator.matchesTransactionSearch("  ", "", "UDHAR", 100L))
    }
}
