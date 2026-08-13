package it.sc4tto.pixelsheetconverter

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

data class ConversionResult(
    val bitmap: Bitmap,
    val indices: IntArray,
    val width: Int,
    val height: Int,
    val counts: IntArray,
    val palette: IntArray
)

object ImageConverter {
    val metrics = listOf("RGB classico", "RGB lineare", "OKLab percettivo", "CIELAB Delta E 2000")
    val dithers = listOf("Nessuno", "Floyd-Steinberg", "Floyd-Steinberg serpentino", "Atkinson", "Bayer 4x4", "Sierra Lite", "Stucki", "Jarvis-Judice-Ninke")
    val palettes = linkedMapOf(
        "RGB primari" to intArrayOf(Color.RED, Color.GREEN, Color.BLUE),
        "RGB + bianco e nero" to intArrayOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.WHITE),
        "CMY" to intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW),
        "Scala di grigi (8)" to IntArray(8) { index ->
            val value = (index * 255.0 / 7.0).roundToInt()
            Color.rgb(value, value, value)
        },
    )

    fun convert(source: Bitmap, targetWidth: Int, paletteName: String, metric: String, dither: String,
                progress: (Int) -> Unit = {}): ConversionResult {
        require(targetWidth in 16..2048) { "La larghezza deve essere tra 16 e 2048." }
        val targetHeight = max(1, (source.height.toDouble() * targetWidth / source.width).roundToInt())
        require(targetWidth.toLong() * targetHeight <= 2_000_000) { "Superato il limite di 2.000.000 pixel." }
        val resized = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val packed = IntArray(targetWidth * targetHeight)
        resized.getPixels(packed, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        val work = FloatArray(packed.size * 3)
        for (i in packed.indices) {
            work[i * 3] = Color.red(packed[i]).toFloat()
            work[i * 3 + 1] = Color.green(packed[i]).toFloat()
            work[i * 3 + 2] = Color.blue(packed[i]).toFloat()
        }
        progress(10)
        val palette = palettes[paletteName] ?: error("Palette sconosciuta: $paletteName")
        val paletteRgb = palette.map { floatArrayOf(Color.red(it).toFloat(), Color.green(it).toFloat(), Color.blue(it).toFloat()) }
        val paletteMetric = paletteRgb.map { transform(it, metric) }
        val indices = when (dither) {
            "Nessuno" -> quantize(work, targetWidth, targetHeight, metric, paletteMetric, progress)
            "Bayer 4x4" -> bayer(work, targetWidth, targetHeight, metric, paletteMetric, progress)
            else -> {
                val (kernel, serpentine) = kernelFor(dither)
                diffuse(work, targetWidth, targetHeight, metric, paletteRgb, paletteMetric, kernel, serpentine, progress)
            }
        }
        val output = IntArray(indices.size) { palette[indices[it]] }
        val bitmap = Bitmap.createBitmap(output, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val counts = IntArray(palette.size)
        indices.forEach { counts[it]++ }
        progress(100)
        return ConversionResult(bitmap, indices, targetWidth, targetHeight, counts, palette)
    }

    private fun quantize(work: FloatArray, w: Int, h: Int, metric: String,
                         paletteMetric: List<FloatArray>, progress: (Int) -> Unit): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 3
                out[y * w + x] = nearest(floatArrayOf(work[i], work[i + 1], work[i + 2]), metric, paletteMetric)
            }
            if (y % max(1, h / 50) == 0) progress(10 + 80 * y / h)
        }
        return out
    }

    private fun bayer(work: FloatArray, w: Int, h: Int, metric: String,
                      paletteMetric: List<FloatArray>, progress: (Int) -> Unit): IntArray {
        val matrix = arrayOf(intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6), intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5))
        val adjusted = work.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            val offset = (matrix[y % 4][x % 4] / 16f - .5f) * 64f
            val i = (y * w + x) * 3
            for (c in 0..2) adjusted[i + c] = (adjusted[i + c] + offset).coerceIn(0f, 255f)
        }
        return quantize(adjusted, w, h, metric, paletteMetric, progress)
    }

    private data class Tap(val dx: Int, val dy: Int, val weight: Float)
    private fun kernelFor(name: String): Pair<List<Tap>, Boolean> = when (name) {
        "Floyd-Steinberg" -> Pair(listOf(Tap(1,0,7/16f), Tap(-1,1,3/16f), Tap(0,1,5/16f), Tap(1,1,1/16f)), false)
        "Floyd-Steinberg serpentino" -> Pair(listOf(Tap(1,0,7/16f), Tap(-1,1,3/16f), Tap(0,1,5/16f), Tap(1,1,1/16f)), true)
        "Atkinson" -> Pair(listOf(Tap(1,0,1/8f), Tap(2,0,1/8f), Tap(-1,1,1/8f), Tap(0,1,1/8f), Tap(1,1,1/8f), Tap(0,2,1/8f)), false)
        "Sierra Lite" -> Pair(listOf(Tap(1,0,2/4f), Tap(-1,1,1/4f), Tap(0,1,1/4f)), true)
        "Stucki" -> Pair(listOf(Tap(1,0,8/42f),Tap(2,0,4/42f),Tap(-2,1,2/42f),Tap(-1,1,4/42f),Tap(0,1,8/42f),Tap(1,1,4/42f),Tap(2,1,2/42f),Tap(-2,2,1/42f),Tap(-1,2,2/42f),Tap(0,2,4/42f),Tap(1,2,2/42f),Tap(2,2,1/42f)), true)
        "Jarvis-Judice-Ninke" -> Pair(listOf(Tap(1,0,7/48f),Tap(2,0,5/48f),Tap(-2,1,3/48f),Tap(-1,1,5/48f),Tap(0,1,7/48f),Tap(1,1,5/48f),Tap(2,1,3/48f),Tap(-2,2,1/48f),Tap(-1,2,3/48f),Tap(0,2,5/48f),Tap(1,2,3/48f),Tap(2,2,1/48f)), true)
        else -> error("Dithering sconosciuto: $name")
    }

    private fun diffuse(work: FloatArray, w: Int, h: Int, metric: String,
                        paletteRgb: List<FloatArray>, paletteMetric: List<FloatArray>, kernel: List<Tap>,
                        serpentine: Boolean, progress: (Int) -> Unit): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val reverse = serpentine && y % 2 == 1
            val range = if (reverse) (w - 1 downTo 0) else (0 until w)
            val direction = if (reverse) -1 else 1
            for (x in range) {
                val i = (y * w + x) * 3
                val old = floatArrayOf(work[i], work[i + 1], work[i + 2])
                val selected = nearest(old, metric, paletteMetric)
                out[y * w + x] = selected
                val error = FloatArray(3) { old[it] - paletteRgb[selected][it] }
                for (tap in kernel) {
                    val nx = x + tap.dx * direction; val ny = y + tap.dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val ni = (ny * w + nx) * 3
                        for (c in 0..2) work[ni + c] = (work[ni + c] + error[c] * tap.weight).coerceIn(0f, 255f)
                    }
                }
            }
            if (y % max(1, h / 50) == 0) progress(10 + 80 * y / h)
        }
        return out
    }

    private fun nearest(rgb: FloatArray, metric: String, paletteMetric: List<FloatArray>): Int {
        val value = transform(rgb, metric)
        var best = 0; var bestDistance = Double.POSITIVE_INFINITY
        for (i in paletteMetric.indices) {
            val d = if (metric == "CIELAB Delta E 2000") deltaE2000(value, paletteMetric[i])
                    else value.indices.sumOf { c -> (value[c] - paletteMetric[i][c]).toDouble().pow(2) }
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        return best
    }

    private fun transform(rgb: FloatArray, metric: String): FloatArray {
        if (metric == "RGB classico") return FloatArray(3) { rgb[it] / 255f }
        val linear = FloatArray(3) { c ->
            val u = rgb[c] / 255f
            if (u <= .04045f) u / 12.92f else ((u + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
        if (metric == "RGB lineare") return linear
        val r = linear[0]; val g = linear[1]; val b = linear[2]
        if (metric == "OKLab percettivo") {
            val l = cbrt((.4122214708*r + .5363325363*g + .0514459929*b).toDouble())
            val m = cbrt((.2119034982*r + .6806995451*g + .1073969566*b).toDouble())
            val s = cbrt((.0883024619*r + .2817188376*g + .6299787005*b).toDouble())
            return floatArrayOf((.2104542553*l + .793617785*m - .0040720468*s).toFloat(),
                (1.9779984951*l - 2.428592205*m + .4505937099*s).toFloat(),
                (.0259040371*l + .7827717662*m - .808675766*s).toFloat())
        }
        val x = (.4124564*r + .3575761*g + .1804375*b) / .95047
        val y = .2126729*r + .7151522*g + .072175*b
        val z = (.0193339*r + .119192*g + .9503041*b) / 1.08883
        fun f(v: Double): Double { val d = 6.0/29; return if (v > d.pow(3)) cbrt(v) else v/(3*d*d)+4.0/29 }
        val fx=f(x); val fy=f(y); val fz=f(z)
        return floatArrayOf((116*fy-16).toFloat(), (500*(fx-fy)).toFloat(), (200*(fy-fz)).toFloat())
    }

    private fun deltaE2000(a: FloatArray, b: FloatArray): Double {
        val l1=a[0].toDouble(); val aa1=a[1].toDouble(); val bb1=a[2].toDouble()
        val l2=b[0].toDouble(); val aa2=b[1].toDouble(); val bb2=b[2].toDouble()
        val c1=hypot(aa1,bb1); val c2=hypot(aa2,bb2); val cb=(c1+c2)/2
        val g=.5*(1-sqrt(cb.pow(7)/(cb.pow(7)+25.0.pow(7))))
        val ap1=(1+g)*aa1; val ap2=(1+g)*aa2; val cp1=hypot(ap1,bb1); val cp2=hypot(ap2,bb2)
        fun hue(y:Double,x:Double):Double=(Math.toDegrees(atan2(y,x))+360)%360
        val hp1=hue(bb1,ap1); val hp2=hue(bb2,ap2); val dl=l2-l1; val dc=cp2-cp1
        var dh=hp2-hp1; if(dh>180)dh-=360.0 else if(dh< -180)dh+=360.0; if(cp1*cp2==0.0)dh=0.0
        val dH=2*sqrt(cp1*cp2)*sin(Math.toRadians(dh/2)); val lb=(l1+l2)/2; val cpb=(cp1+cp2)/2
        var hpb=if(cp1*cp2==0.0)hp1+hp2 else (hp1+hp2)/2
        if(cp1*cp2!=0.0 && abs(hp1-hp2)>180) hpb += if(hp1+hp2<360)180 else -180
        val t=1-.17*cos(Math.toRadians(hpb-30))+.24*cos(Math.toRadians(2*hpb))+.32*cos(Math.toRadians(3*hpb+6))-.20*cos(Math.toRadians(4*hpb-63))
        val sl=1+.015*(lb-50).pow(2)/sqrt(20+(lb-50).pow(2)); val sc=1+.045*cpb; val sh=1+.015*cpb*t
        val rt=-2*sqrt(cpb.pow(7)/(cpb.pow(7)+25.0.pow(7)))*sin(Math.toRadians(60*exp(-((hpb-275)/25).pow(2))))
        return sqrt(max(0.0,(dl/sl).pow(2)+(dc/sc).pow(2)+(dH/sh).pow(2)+rt*(dc/sc)*(dH/sh)))
    }
}
