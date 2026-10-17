package com.carlos.karoopantallas.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Lápiz de las pantallas: todo se dibuja en coordenadas de diseño (480 × 620, como en las
 * maquetas) y se escala al tamaño real del campo. Las posiciones y tamaños salen de medir
 * las maquetas, así que lo que se ve en el Karoo coincide con el lienzo.
 */
class Painter(context: Context) {

    enum class A { L, C, R }

    companion object {
        const val W = 480f
        const val H = 620f
        /** Altura de las cifras de Barlow respecto al tamaño de letra */
        const val CAP = 0.7f
        /** Borde derecho del contenido: deja libre la franja del radar */
        const val RIGHT = 427f
    }

    private val condensed = Typeface.createFromAsset(context.assets, "fonts/BarlowCondensed-Bold.ttf")
    private val barlow = Typeface.createFromAsset(context.assets, "fonts/Barlow-Bold.ttf")

    private val numP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = condensed
        fontFeatureSettings = "'tnum'" // cifras de ancho fijo: no "bailan" al cambiar
    }
    private val txtP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = barlow }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    lateinit var c: Canvas
        private set

    fun begin(widthPx: Int, heightPx: Int, bg: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.RGB_565)
        c = Canvas(bmp)
        c.drawColor(bg)
        c.scale(widthPx / W, heightPx / H)
        return bmp
    }

    // ---------- Medidas ----------
    fun wNum(s: String, size: Float): Float { numP.textSize = size; return numP.measureText(s) }
    fun wTxt(s: String, size: Float): Float { txtP.textSize = size; return txtP.measureText(s) }

    /** Tamaño de letra que hace caber [s] en [maxW] (nunca mayor que [size]). */
    fun fitNum(s: String, size: Float, maxW: Float): Float {
        val w = wNum(s, size)
        return if (w <= maxW || w <= 0f) size else size * maxW / w
    }

    private fun startX(x: Float, w: Float, a: A) = when (a) {
        A.L -> x
        A.C -> x - w / 2f
        A.R -> x - w
    }

    // ---------- Texto ----------
    /**
     * Cifra en Barlow Condensed. [sy] estira en vertical desde la base de su caja
     * (como `transform: scaleY` con origen abajo en la maqueta). Devuelve el ancho.
     */
    fun num(s: String, x: Float, base: Float, size: Float, color: Int, a: A = A.L, sy: Float = 1f): Float {
        numP.textSize = size
        numP.color = color
        val w = numP.measureText(s)
        val px = startX(x, w, a)
        if (sy != 1f) {
            c.save()
            c.scale(1f, sy, px, base + 0.05f * size)
            c.drawText(s, px, base, numP)
            c.restore()
        } else {
            c.drawText(s, px, base, numP)
        }
        return w
    }

    /** Texto en Barlow (rótulos y unidades). Devuelve el ancho. */
    fun txt(s: String, x: Float, base: Float, size: Float, color: Int, a: A = A.L): Float {
        txtP.textSize = size
        txtP.color = color
        val w = txtP.measureText(s)
        c.drawText(s, startX(x, w, a), base, txtP)
        return w
    }

    /**
     * Cifra con decimales a mitad de tamaño ("113" + ",75") y unidad opcional detrás.
     * Se encoge sola si no cabe en [maxW]. Devuelve el ancho total.
     */
    fun numDec(
        value: String, x: Float, base: Float, size: Float, color: Int, a: A = A.L,
        unit: String? = null, unitSize: Float = 22f, unitColor: Int = color, unitGap: Float = 5f,
        maxW: Float = Float.MAX_VALUE,
    ): Float {
        val comma = value.indexOf(',')
        val ip = if (comma >= 0) value.substring(0, comma) else value
        val dp = if (comma >= 0) value.substring(comma) else ""
        fun total(sz: Float) = wNum(ip, sz) + (if (dp.isNotEmpty()) 2f + wNum(dp, sz / 2f) else 0f) +
            (if (unit != null) unitGap + wTxt(unit, unitSize) else 0f)
        var sz = size
        val t = total(sz)
        if (t > maxW) sz = size * min(1f, (maxW - (if (unit != null) unitGap + wTxt(unit, unitSize) else 0f)) / (t - (if (unit != null) unitGap + wTxt(unit, unitSize) else 0f)))
        val tw = total(sz)
        var px = startX(x, tw, a)
        px += num(ip, px, base, sz, color)
        if (dp.isNotEmpty()) px += 2f + num(dp, px + 2f, base, sz / 2f, color)
        if (unit != null) txt(unit, px + unitGap, base, unitSize, unitColor)
        return tw
    }

    // ---------- Formas ----------
    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Float = 1f) {
        fill.color = color
        fill.alpha = (alpha * 255).toInt()
        c.drawRect(l, t, r, b, fill)
        fill.alpha = 255
    }

    /** Barra de progreso dentro de la zona: fondo + relleno del color de la zona. */
    fun bar(l: Float, t: Float, r: Float, b: Float, fraction: Double?, track: Int, color: Int) {
        rect(l, t, r, b, track)
        if (fraction != null) rect(l, t, l + (r - l) * fraction.coerceIn(0.0, 1.0).toFloat(), b, color)
    }

    fun path(p: Path, color: Int, alpha: Float = 1f) {
        fill.color = color
        fill.alpha = (alpha * 255).toInt()
        c.drawPath(p, fill)
        fill.alpha = 255
    }

    fun line(p: Path, color: Int, width: Float, alpha: Float = 1f, dash: Boolean = false) {
        stroke.color = color
        stroke.alpha = (alpha * 255).toInt()
        stroke.strokeWidth = width
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.pathEffect = if (dash) DashPathEffect(floatArrayOf(4f, 4f), 0f) else null
        c.drawPath(p, stroke)
        stroke.alpha = 255
    }

    fun dot(x: Float, y: Float, r: Float, color: Int, ring: Int) {
        fill.color = ring
        c.drawCircle(x, y, r + 3f, fill)
        fill.color = color
        c.drawCircle(x, y, r, fill)
    }
}
