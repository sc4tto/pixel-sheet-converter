package it.sc4tto.pixelsheetconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

object WatermarkComposer {
    private const val WIDTH_RATIO = 0.30f
    private const val MARGIN_RATIO = 0.03f

    fun addLogo(context: Context, source: Bitmap): Bitmap {
        val logo = requireNotNull(
            BitmapFactory.decodeResource(context.resources, R.drawable.pixel_sheet_logo),
        ) { "Risorsa logo non disponibile" }
        val targetWidth = (source.width * WIDTH_RATIO).roundToInt().coerceAtLeast(1)
        val targetHeight = (targetWidth.toFloat() * logo.height / logo.width).roundToInt().coerceAtLeast(1)
        val margin = (minOf(source.width, source.height) * MARGIN_RATIO).roundToInt().coerceAtLeast(1)
        val left = (source.width - targetWidth - margin).coerceAtLeast(0)
        val top = (source.height - targetHeight - margin).coerceAtLeast(0)

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawBitmap(
            logo,
            null,
            Rect(left, top, left + targetWidth, top + targetHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }
}
