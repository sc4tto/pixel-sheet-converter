package it.sc4tto.pixelsheetconverter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class MsnWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 85, 184, 215)
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val edge = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.48f)
            cubicTo(w * 0.82f, h * 0.08f, w * 0.66f, h * 0.92f, w * 0.48f, h * 0.58f)
            cubicTo(w * 0.31f, h * 0.25f, w * 0.16f, h * 0.92f, 0f, h * 0.52f)
            close()
        }
        canvas.drawPath(edge, wavePaint)

        val highlight = Path().apply {
            moveTo(0f, h * 0.40f)
            cubicTo(w * 0.16f, h * 0.80f, w * 0.31f, h * 0.13f, w * 0.48f, h * 0.46f)
            cubicTo(w * 0.66f, h * 0.80f, w * 0.82f, 0f, w, h * 0.36f)
        }
        canvas.drawPath(highlight, highlightPaint)
    }
}
