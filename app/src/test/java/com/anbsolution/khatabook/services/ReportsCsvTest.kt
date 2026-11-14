package com.anbsolution.khatabook.services

import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsCsvTest {
    @Test
    fun exportsTotalsOutstandingAndPeriodTransactions() {
        val csv = ReportsCsv.render(
            ReportExportData(
                businessName = "Corner Shop",
                periodLabel = "This month",
                generatedAt = 0L,
                totalUdharPaise = 1250L,
                totalJamaPaise = 500L,
                outstandingCustomers = listOf(
                    ReportOutstandingCustomer("Asha", 750L)
                ),
                transactions = listOf(
                    ReportTransaction(
                        createdAt = 0L,
                        customerName = "Asha",
                        type = "UDHAR",
                        amountPaise = 1250L,
                        note = "Stock purchase"
                    )
                )
            )
        )

        assertTrue(csv.contains("\"Total Udhar\",\"12.50\""))
        assertTrue(csv.contains("\"Period net\",\"7.50\""))
        assertTrue(csv.contains("\"Asha\",\"7.50\""))
        assertTrue(csv.contains("\"Asha\",\"Udhar\",\"12.50\",\"Stock purchase\""))
    }

    @Test
    fun keepsSmallNegativeNetAmountsSigned() {
        val csv = ReportsCsv.render(
            ReportExportData(
                businessName = "Corner Shop",
                periodLabel = "Today",
                generatedAt = 0L,
                totalUdharPaise = 0L,
                totalJamaPaise = 50L,
                outstandingCustomers = emptyList(),
                transactions = emptyList()
            )
        )

        assertTrue(csv.contains("\"Period net\",\"-0.50\""))
        assertTrue(csv.contains("\"Net status\",\"Jama\""))
    }

    @Test
    fun escapesCsvContentAndGuardsSpreadsheetFormulas() {
        val csv = ReportsCsv.render(
            ReportExportData(
                businessName = "=2+2",
                periodLabel = "All time",
                generatedAt = 0L,
                totalUdharPaise = 0L,
                totalJamaPaise = 0L,
                outstandingCustomers = emptyList(),
                transactions = listOf(
                    ReportTransaction(
                        createdAt = 0L,
                        customerName = "Asha, \"A\"",
                        type = "JAMA",
                        amountPaise = 100L,
                        note = "line one\nline two"
                    )
                )
            )
        )

        assertTrue(csv.contains("\"Business\",\"'=2+2\""))
        assertTrue(csv.contains("\"Asha, \"\"A\"\"\""))
        assertTrue(csv.contains("\"line one\nline two\""))
    }
}
