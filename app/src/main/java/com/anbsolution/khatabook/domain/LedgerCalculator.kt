package com.anbsolution.khatabook.domain

object LedgerCalculator {
    fun balancePaise(creditPaise: Long, paymentPaise: Long): Long {
        require(creditPaise >= 0) { "Credit cannot be negative" }
        require(paymentPaise >= 0) { "Payment cannot be negative" }
        return creditPaise - paymentPaise
    }

    fun validateAmount(amountPaise: Long) {
        require(amountPaise > 0) { "Amount must be greater than zero" }
    }
}
