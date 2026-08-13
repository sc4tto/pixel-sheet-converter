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
        val w = width.toFloat(); val h = height.toFloat()
        val lobe = 15f * density; val side = 4f * density; val radius = 18f * density
        return Path().apply {
            moveTo(side, lobe + radius)
            quadTo(side, lobe, side + radius, lobe)
            cubicTo(w * .04f, lobe * .15f, w * .10f, lobe * .15f, w * .15f, lobe)
            cubicTo(w * .20f, lobe * -.20f, w * .30f, lobe * -.20f, w * .35f, lobe)
            cubicTo(w * .43f, lobe * -.35f, w * .56f, lobe * -.35f, w * .63f, lobe)
            cubicTo(w * .70f, lobe * -.15f, w * .80f, lobe * -.15f, w * .85f, lobe)
            cubicTo(w * .91f, lobe * .10f, w * .96f, lobe * .15f, w - side - radius, lobe)
            quadTo(w - side, lobe, w - side, lobe + radius)
            cubicTo(w - side * .20f, h * .35f, w - side * 1.80f, h * .46f, w - side, h * .58f)
            cubicTo(w - side * .20f, h * .70f, w - side * 1.80f, h * .78f, w - side, h - radius - side)
            quadTo(w - side, h - side, w - radius - side, h - side)
            cubicTo(w * .82f, h - side * 1.85f, w * .70f, h - side * .15f, w * .57f, h - side)
            cubicTo(w * .44f, h - side * 1.90f, w * .30f, h - side * .10f, w * .18f, h - side)
            lineTo(radius + side, h - side)
            quadTo(side, h - side, side, h - radius - side)
            cubicTo(side * 1.75f, h * .78f, side * .20f, h * .68f, side, h * .57f)
            cubicTo(side * 1.75f, h * .46f, side * .20f, h * .35f, side, lobe + radius)
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
