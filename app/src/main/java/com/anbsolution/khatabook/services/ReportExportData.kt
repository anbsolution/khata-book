package com.anbsolution.khatabook.services

data class ReportExportData(
    val businessName: String,
    val periodLabel: String,
    val generatedAt: Long,
    val totalUdharPaise: Long,
    val totalJamaPaise: Long,
    val outstandingCustomers: List<ReportOutstandingCustomer>,
    val transactions: List<ReportTransaction>
)

data class ReportOutstandingCustomer(
    val customerName: String,
    val balancePaise: Long
)

data class ReportTransaction(
    val createdAt: Long,
    val customerName: String,
    val type: String,
    val amountPaise: Long,
    val note: String
)

object ReportsCsv {
    fun render(data: ReportExportData): String = buildString {
        row(csvCell("Business"), csvCell(data.businessName))
        row(csvCell("Period"), csvCell(data.periodLabel))
        row(
            csvCell("Generated"),
            csvCell(formatDateTime(data.generatedAt))
        )
        row(csvCell("Total Udhar"), csvCell(formatRupees(data.totalUdharPaise), false))
        row(csvCell("Total Jama"), csvCell(formatRupees(data.totalJamaPaise), false))
        val net = data.totalUdharPaise - data.totalJamaPaise
        row(csvCell("Period net"), csvCell(formatRupees(net), false))
        row(
            csvCell("Net status"),
            csvCell(
                when {
                    net > 0L -> "Udhar"
                    net < 0L -> "Jama"
                    else -> "Settled"
                }
            )
        )
        row(csvCell("Period entries"), csvCell(data.transactions.size.toString(), false))
        append("\r\n")
        row(csvCell("Current outstanding balances"))
        row(csvCell("Customer"), csvCell("Outstanding (INR)"))
        data.outstandingCustomers.forEach { customer ->
            row(
                csvCell(customer.customerName),
                csvCell(formatRupees(customer.balancePaise), false)
            )
        }
        append("\r\n")
        row(csvCell("Period transactions"))
        row(
            csvCell("Date"),
            csvCell("Customer"),
            csvCell("Type"),
            csvCell("Amount (INR)"),
            csvCell("Note")
        )
        data.transactions.forEach { transaction ->
            row(
                csvCell(formatDateTime(transaction.createdAt), false),
                csvCell(transaction.customerName),
                csvCell(if (transaction.type == "UDHAR") "Udhar" else "Jama"),
                csvCell(formatRupees(transaction.amountPaise), false),
                csvCell(transaction.note)
            )
        }
    }

    private fun StringBuilder.row(vararg cells: String) {
        append(cells.joinToString(","))
        append("\r\n")
    }

    private fun csvCell(value: String, guardFormula: Boolean = true): String {
        val formulaPrefix = value.trimStart().firstOrNull()
        val safeValue = if (
            guardFormula && formulaPrefix != null &&
            formulaPrefix in setOf('=', '+', '-', '@', '\t', '\r')
        ) {
            "'$value"
        } else {
            value
        }
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }

    private fun formatDateTime(timestamp: Long): String =
        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(timestamp))

    private fun formatRupees(amountPaise: Long): String {
        val sign = if (amountPaise < 0L) "-" else ""
        val rupees = kotlin.math.abs(amountPaise / 100)
        val paise = kotlin.math.abs(amountPaise % 100)
        return "$sign$rupees.${paise.toString().padStart(2, '0')}"
    }
}
