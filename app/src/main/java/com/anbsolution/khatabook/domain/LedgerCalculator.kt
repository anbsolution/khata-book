package com.anbsolution.khatabook.domain

import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

enum class LedgerPeriod {
    TODAY,
    LAST_7_DAYS,
    THIS_MONTH,
    ALL_TIME
}

object LedgerCalculator {
    fun balancePaise(creditPaise: Long, paymentPaise: Long): Long {
        require(creditPaise >= 0) { "Credit cannot be negative" }
        require(paymentPaise >= 0) { "Payment cannot be negative" }
        return creditPaise - paymentPaise
    }

    fun validateAmount(amountPaise: Long) {
        require(amountPaise > 0) { "Amount must be greater than zero" }
    }

    fun periodStartMillis(period: LedgerPeriod, now: Long): Long {
        if (period == LedgerPeriod.ALL_TIME) return Long.MIN_VALUE
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (period) {
                LedgerPeriod.TODAY -> Unit
                LedgerPeriod.LAST_7_DAYS -> add(Calendar.DAY_OF_YEAR, -6)
                LedgerPeriod.THIS_MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                LedgerPeriod.ALL_TIME -> Unit
            }
        }.timeInMillis
    }

    fun isWithinPeriod(timestamp: Long, startMillis: Long, endMillis: Long): Boolean =
        timestamp >= startMillis && timestamp <= endMillis

    fun matchesTransactionSearch(
        query: String,
        note: String,
        type: String,
        amountPaise: Long
    ): Boolean {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return true

        val rupeeAmount = BigDecimal.valueOf(amountPaise, 2)
            .stripTrailingZeros()
            .toPlainString()
        return note.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            type.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            rupeeAmount.contains(normalizedQuery)
    }
}
