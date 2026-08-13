package it.sc4tto.pixelsheetconverter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.LinearLayout

class MsnTopPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 75, 205, 232); style = Paint.Style.STROKE; strokeWidth = 1.35f * density
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = .85f * density
    }
    init { setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun panelPath(): Path {
        val w = width.toFloat(); val h = height.toFloat(); val wave = 10f * density
        return Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h - wave)
            cubicTo(w * .82f, h - wave * .15f, w * .67f, h - wave * 1.15f, w * .49f, h - wave * .60f)
            cubicTo(w * .31f, h - wave * .02f, w * .15f, h - wave * 1.05f, 0f, h - wave * .48f)
            close()
        }
    }
    private fun secondWave(): Path {
        val w = width.toFloat(); val h = height.toFloat(); val wave = 10f * density
        return Path().apply {
            moveTo(0f, h - wave * .72f)
            cubicTo(w * .18f, h - wave * 1.18f, w * .34f, h - wave * .08f, w * .53f, h - wave * .78f)
            cubicTo(w * .70f, h - wave * 1.38f, w * .86f, h - wave * .27f, w, h - wave * .66f)
        }
    }
    override fun onDraw(canvas: Canvas) {
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0xD8C9F4FA.toInt(), 0xCD64B5D0.toInt(), 0xC92B7698.toInt()), null, Shader.TileMode.CLAMP)
        val path = panelPath()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
        canvas.drawPath(secondWave(), highlight)
        super.onDraw(canvas)
    }
    override fun dispatchDraw(canvas: Canvas) {
        canvas.save(); canvas.clipPath(panelPath()); super.dispatchDraw(canvas); canvas.restore()
    }
}
