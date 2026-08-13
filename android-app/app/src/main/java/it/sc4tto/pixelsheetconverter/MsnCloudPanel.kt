package it.sc4tto.pixelsheetconverter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.LinearLayout

class MsnCloudPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 19, 29, 34) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 191, 240, 250)
        style = Paint.Style.STROKE
        strokeWidth = density
    }

    init { setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun cloudPath(): Path {
        val w = width.toFloat(); val h = height.toFloat(); val lobe = 15f * density
        return Path().apply {
            moveTo(0f, lobe)
            cubicTo(w * .04f, lobe * .15f, w * .10f, lobe * .15f, w * .15f, lobe)
            cubicTo(w * .20f, lobe * -.20f, w * .30f, lobe * -.20f, w * .35f, lobe)
            cubicTo(w * .43f, lobe * -.35f, w * .56f, lobe * -.35f, w * .63f, lobe)
            cubicTo(w * .70f, lobe * -.15f, w * .80f, lobe * -.15f, w * .85f, lobe)
            cubicTo(w * .91f, lobe * .10f, w * .97f, lobe * .25f, w, lobe)
            lineTo(w, h - 16f * density)
            quadTo(w, h, w - 16f * density, h)
            lineTo(16f * density, h)
            quadTo(0f, h, 0f, h - 16f * density)
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val path = cloudPath()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(cloudPath())
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
