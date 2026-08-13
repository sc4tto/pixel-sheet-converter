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
        color = Color.argb(210, 210, 246, 255); style = Paint.Style.STROKE; strokeWidth = density
    }
    init { setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun panelPath(): Path {
        val w = width.toFloat(); val h = height.toFloat(); val wave = 7f * density
        return Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h - wave)
            cubicTo(w * .80f, h - wave * .25f, w * .66f, h - wave * 1.25f, w * .48f, h - wave * .65f)
            cubicTo(w * .30f, h - wave * .05f, w * .16f, h - wave * 1.10f, 0f, h - wave * .55f)
            close()
        }
    }
    override fun onDraw(canvas: Canvas) {
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0xD8C9F4FA.toInt(), 0xCD64B5D0.toInt(), 0xC92B7698.toInt()), null, Shader.TileMode.CLAMP)
        val path = panelPath(); canvas.drawPath(path, fill); canvas.drawPath(path, edge)
        super.onDraw(canvas)
    }
    override fun dispatchDraw(canvas: Canvas) {
        canvas.save(); canvas.clipPath(panelPath()); super.dispatchDraw(canvas); canvas.restore()
    }
}
