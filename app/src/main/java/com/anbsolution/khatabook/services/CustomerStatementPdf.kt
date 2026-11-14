package com.anbsolution.khatabook.services

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object CustomerStatementPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
    private const val ROW_HEIGHT = 44f

    fun write(
        output: OutputStream,
        businessName: String,
        customer: CustomerEntity,
        transactions: List<LedgerTransactionEntity>,
        generatedAt: Long = System.currentTimeMillis()
    ) {
        val document = PdfDocument()
        val titlePaint = textPaint(21f, Color.rgb(23, 78, 166), bold = true)
        val headingPaint = textPaint(11f, Color.rgb(31, 41, 55), bold = true)
        val bodyPaint = textPaint(10f, Color.rgb(55, 65, 81))
        val mutedPaint = textPaint(9f, Color.rgb(102, 112, 133))
        val amountPaint = textPaint(10f, Color.rgb(31, 41, 55)).apply {
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
        }
        val totalUdhar = transactions.filter { it.type == "UDHAR" }.sumOf { it.amountPaise }
        val totalJama = transactions.filter { it.type == "JAMA" }.sumOf { it.amountPaise }
        val balance = totalUdhar - totalJama

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var rowTop = 0f

        fun drawTableHeader(target: Canvas, baseline: Float) {
            target.drawText("Date", MARGIN, baseline, headingPaint)
            target.drawText("Type", 137f, baseline, headingPaint)
            target.drawText("Details", 205f, baseline, headingPaint)
            target.drawText("Amount", PAGE_WIDTH - MARGIN, baseline, amountPaint)
            target.drawLine(MARGIN, baseline + 8f, PAGE_WIDTH - MARGIN, baseline + 8f, linePaint)
        }

        fun startPage(firstPage: Boolean) {
            pageNumber++
            val currentPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            page = currentPage
            val target = currentPage.canvas
            canvas = target

            if (firstPage) {
                target.drawText("Customer Statement", MARGIN, 56f, titlePaint)
                target.drawText(
                    fitText(businessName, headingPaint, CONTENT_WIDTH),
                    MARGIN,
                    80f,
                    headingPaint
                )
                target.drawText(
                    fitText("Customer: ${customer.name}", bodyPaint, CONTENT_WIDTH),
                    MARGIN,
                    101f,
                    bodyPaint
                )
                val phone = customer.phone.trim()
                if (phone.isNotEmpty()) {
                    target.drawText(
                        fitText("Phone: $phone", mutedPaint, CONTENT_WIDTH),
                        MARGIN,
                        118f,
                        mutedPaint
                    )
                }
                val dateY = if (phone.isEmpty()) 119f else 135f
                target.drawText(
                    "Generated: ${formatDate(generatedAt)}",
                    MARGIN,
                    dateY,
                    mutedPaint
                )

                val summaryTop = dateY + 15f
                target.drawRoundRect(
                    RectF(MARGIN, summaryTop, PAGE_WIDTH - MARGIN, summaryTop + 78f),
                    10f,
                    10f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(242, 244, 247)
                    }
                )
                val columnX = floatArrayOf(MARGIN + 14f, MARGIN + 184f, MARGIN + 354f)
                val labels = arrayOf("Total Udhar", "Total Jama", "Current balance")
                val values = arrayOf(
                    formatRupees(totalUdhar),
                    formatRupees(totalJama),
                    formatRupees(kotlin.math.abs(balance))
                )
                for (index in labels.indices) {
                    target.drawText(labels[index], columnX[index], summaryTop + 23f, mutedPaint)
                    target.drawText(values[index], columnX[index], summaryTop + 47f, headingPaint)
                }
                val balanceStatus = when {
                    balance > 0L -> "Outstanding to business"
                    balance < 0L -> "Advance with customer"
                    else -> "Account settled"
                }
                target.drawText(
                    balanceStatus,
                    columnX[2],
                    summaryTop + 65f,
                    mutedPaint
                )
                val tableBaseline = summaryTop + 100f
                drawTableHeader(target, tableBaseline)
                rowTop = tableBaseline + 15f
            } else {
                target.drawText("Customer Statement · Continued", MARGIN, 52f, headingPaint)
                target.drawText(
                    fitText("${customer.name} · ${businessName}", bodyPaint, CONTENT_WIDTH),
                    MARGIN,
                    74f,
                    bodyPaint
                )
                drawTableHeader(target, 105f)
                rowTop = 120f
            }
        }

        fun finishPage() {
            val currentPage = page ?: return
            canvas?.drawText(
                "Page $pageNumber",
                PAGE_WIDTH - MARGIN,
                PAGE_HEIGHT - 24f,
                amountPaint
            )
            document.finishPage(currentPage)
            page = null
            canvas = null
        }

        try {
            startPage(firstPage = true)
            if (transactions.isEmpty()) {
                canvas?.drawText("No transactions recorded.", MARGIN, rowTop + 17f, bodyPaint)
            } else {
                transactions.forEach { transaction ->
                    if (rowTop + ROW_HEIGHT > PAGE_HEIGHT - 45f) {
                        finishPage()
                        startPage(firstPage = false)
                    }
                    val target = canvas ?: error("PDF page is not available.")
                    val primaryY = rowTop + 15f
                    target.drawText(formatDate(transaction.createdAt), MARGIN, primaryY, bodyPaint)
                    target.drawText(
                        if (transaction.type == "UDHAR") "Udhar" else "Jama",
                        137f,
                        primaryY,
                        bodyPaint
                    )
                    target.drawText(
                        fitText(
                            transaction.note.replace(Regex("\\s+"), " ").trim().ifBlank { "—" },
                            mutedPaint,
                            267f
                        ),
                        205f,
                        rowTop + 33f,
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
        val rupees = amountPaise / 100
        val paise = kotlin.math.abs(amountPaise % 100)
        return "₹$rupees.${paise.toString().padStart(2, '0')}"
    }
}
