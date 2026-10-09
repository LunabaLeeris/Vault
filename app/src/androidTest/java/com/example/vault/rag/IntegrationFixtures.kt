package com.example.vault.rag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.util.UUID

object IntegrationFixtures {
    fun pdf(context: Context): File {
        val file = File(context.cacheDir, "rag-test-${UUID.randomUUID()}.pdf")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
        val pdf = PdfDocument()
        try {
            for (number in 1..3) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, number).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("VAULT INTEGRATION PAGE $number", 40f, 80f, paint)
                page.canvas.drawText(when (number) {
                    1 -> "This page introduces the sample agreement."
                    2 -> "Water and electricity are separate utilities."
                    else -> "Rent is due on the fifth day of each month."
                }, 40f, 160f, paint)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        return file
    }

    fun image(context: Context): File {
        val file = File(context.cacheDir, "rag-test-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawText("Laptop warranty lasts 24 months.", 40f, 160f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 42f })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        return file
    }
}
