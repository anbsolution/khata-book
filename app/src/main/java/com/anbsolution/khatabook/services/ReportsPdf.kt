package com.anbsolution.khatabook.services

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ReportsPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 40f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - 48f

    fun write(output: OutputStream, data: ReportExportData) {
        val document = PdfDocument()
        val titlePaint = textPaint(18f, Color.rgb(23, 78, 166), bold = true)
        val headingPaint = textPaint(10f, Color.rgb(31, 41, 55), bold = true)
        val bodyPaint = textPaint(9f, Color.rgb(55, 65, 81))
        val mutedPaint = textPaint(8f, Color.rgb(102, 112, 133))
        val amountPaint = textPaint(9f, Color.rgb(31, 41, 55)).apply {
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
        }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var rowTop = 0f
        var currentSection = ""

        fun drawOutstandingHeader(target: Canvas, baseline: Float) {
            target.drawText("Customer", MARGIN, baseline, headingPaint)
            target.drawText("Outstanding", PAGE_WIDTH - MARGIN, baseline, amountPaint)
            target.drawLine(MARGIN, baseline + 7f, PAGE_WIDTH - MARGIN, baseline + 7f, linePaint)
        }

        fun drawTransactionHeader(target: Canvas, baseline: Float) {
            target.drawText("Date", MARGIN, baseline, headingPaint)
            target.drawText("Customer", 112f, baseline, headingPaint)
            target.drawText("Type", 245f, baseline, headingPaint)
            target.drawText("Details", 300f, baseline, headingPaint)
            target.drawText("Amount", PAGE_WIDTH - MARGIN, baseline, amountPaint)
            target.drawLine(MARGIN, baseline + 7f, PAGE_WIDTH - MARGIN, baseline + 7f, linePaint)
        }

        fun drawSectionHeader(target: Canvas, section: String, baseline: Float) {
            target.drawText(section, MARGIN, baseline, headingPaint)
        }

        fun startPage(section: String) {
            pageNumber++
            currentSection = section
            val currentPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            page = currentPage
            val target = currentPage.canvas
            canvas = target
            target.drawText(
                if (pageNumber == 1) "Business Report" else "Business Report · Continued",
                MARGIN,
                42f,
                titlePaint
            )
            target.drawText(
                fitText(data.businessName, bodyPaint, PAGE_WIDTH - MARGIN * 2),
                MARGIN,
                61f,
                bodyPaint
            )
            target.drawText(
                "Period: ${data.periodLabel}   Generated: ${formatDate(data.generatedAt)}",
                MARGIN,
                77f,
                mutedPaint
            )

            if (pageNumber == 1) {
                target.drawRoundRect(
                    RectF(MARGIN, 91f, PAGE_WIDTH - MARGIN, 166f),
                    9f,
                    9f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(242, 244, 247)
                    }
                )
                val labels = arrayOf("Udhar", "Jama", "Period net")
                val values = arrayOf(
                    formatRupees(data.totalUdharPaise),
                    formatRupees(data.totalJamaPaise),
                    formatRupees(data.totalUdharPaise - data.totalJamaPaise)
                )
                val columnX = floatArrayOf(MARGIN + 14f, MARGIN + 184f, MARGIN + 354f)
                for (index in labels.indices) {
                    target.drawText(labels[index], columnX[index], 113f, mutedPaint)
                    target.drawText(values[index], columnX[index], 137f, headingPaint)
                }
                target.drawText(
                    "${data.transactions.size} period entries",
                    MARGIN + 14f,
                    156f,
                    mutedPaint
                )
                rowTop = 183f
            } else {
                rowTop = 92f
            }

            drawSectionHeader(target, section, rowTop)
            rowTop += 17f
            if (section == "Current outstanding balances") {
                drawOutstandingHeader(target, rowTop)
            } else {
                drawTransactionHeader(target, rowTop)
            }
            rowTop += 13f
        }

        fun finishPage() {
            val currentPage = page ?: return
            canvas?.drawText(
                "Page $pageNumber",
                PAGE_WIDTH - MARGIN,
                PAGE_HEIGHT - 23f,
                amountPaint
            )
            document.finishPage(currentPage)
            page = null
            canvas = null
        }

        fun continuePage() {
            finishPage()
            startPage(currentSection)
        }

        try {
            startPage("Current outstanding balances")
            if (data.outstandingCustomers.isEmpty()) {
                canvas?.drawText("No outstanding balances.", MARGIN, rowTop + 12f, bodyPaint)
                rowTop += ROW_HEIGHT
            } else {
                data.outstandingCustomers.forEach { customer ->
                    if (rowTop + ROW_HEIGHT > CONTENT_BOTTOM) continuePage()
                    val target = canvas ?: error("PDF page is not available.")
                    target.drawText(
                        fitText(customer.customerName, bodyPaint, 380f),
                        MARGIN,
                        rowTop + 14f,
                        bodyPaint
                    )
                    target.drawText(
                        formatRupees(customer.balancePaise),
                        PAGE_WIDTH - MARGIN,
                        rowTop + 14f,
                        amountPaint
                    )
                    target.drawLine(
                        MARGIN,
                        rowTop + ROW_HEIGHT,
                        PAGE_WIDTH - MARGIN,
                        rowTop + ROW_HEIGHT,
                        linePaint
                    )
                    rowTop += ROW_HEIGHT
                }
            }

            val transactionSectionHeight = 42f
            if (rowTop + transactionSectionHeight > CONTENT_BOTTOM) {
                finishPage()
                startPage("Period transactions")
            } else {
                currentSection = "Period transactions"
                val target = canvas ?: error("PDF page is not available.")
                drawSectionHeader(target, currentSection, rowTop + 4f)
                rowTop += 21f
                drawTransactionHeader(target, rowTop)
                rowTop += 13f
            }

            if (data.transactions.isEmpty()) {
                canvas?.drawText("No transactions in this period.", MARGIN, rowTop + 12f, bodyPaint)
            } else {
                data.transactions.forEach { transaction ->
                    if (rowTop + ROW_HEIGHT > CONTENT_BOTTOM) continuePage()
                    val target = canvas ?: error("PDF page is not available.")
                    val primaryY = rowTop + 14f
                    target.drawText(formatDate(transaction.createdAt), MARGIN, primaryY, bodyPaint)
                    target.drawText(
                        fitText(transaction.customerName, bodyPaint, 128f),
                        112f,
                        primaryY,
                        bodyPaint
                    )
                    target.drawText(
                        if (transaction.type == "UDHAR") "Udhar" else "Jama",
                        245f,
                        primaryY,
                        bodyPaint
                    )
                    target.drawText(
                        fitText(
                            transaction.note.replace(Regex("\\s+"), " ").trim().ifBlank { "—" },
                            mutedPaint,
                            145f
                        ),
                        300f,
                        rowTop + 30f,
                        mutedPaint
                    )
                    target.drawText(
                        formatRupees(transaction.amountPaise),
                        PAGE_WIDTH - MARGIN,
                        primaryY,
                        amountPaint
                    )
                    target.drawLine(
                        MARGIN,
                        rowTop + ROW_HEIGHT,
                        PAGE_WIDTH - MARGIN,
                        rowTop + ROW_HEIGHT,
                        linePaint
                    )
                    rowTop += ROW_HEIGHT
                }
            }
            finishPage()
            document.writeTo(output)
        } finally {
            finishPage()
            document.close()
        }
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        val count = paint.breakText(text, true, maxWidth, null)
        return if (count >= text.length) {
            text
        } else {
            text.take((count - 1).coerceAtLeast(0)).trimEnd() + "…"
        }
    }

    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

    private fun formatRupees(amountPaise: Long): String {
        val sign = if (amountPaise < 0L) "-" else ""
        val rupees = kotlin.math.abs(amountPaise / 100)
        val paise = kotlin.math.abs(amountPaise % 100)
        return "$sign₹$rupees.${paise.toString().padStart(2, '0')}"
    }
}
