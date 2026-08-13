package it.sc4tto.pixelsheetconverter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CameraGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 0, 0, 0)
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        strokeWidth = resources.displayMetrics.density
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val verticals = floatArrayOf(width / 3f, width * 2f / 3f)
        val horizontals = floatArrayOf(height / 3f, height * 2f / 3f)
        verticals.forEach { x ->
            canvas.drawLine(x, 0f, x, height.toFloat(), shadowPaint)
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
        }
        horizontals.forEach { y ->
            canvas.drawLine(0f, y, width.toFloat(), y, shadowPaint)
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }
    }
}
