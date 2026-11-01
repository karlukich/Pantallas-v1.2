package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.min

/**
 * Campos pequeños para la página del mapa (o cualquier página con varios campos).
 * Se dibujan al tamaño real de la celda: el rótulo arriba a la izquierda, como los campos del Karoo.
 */
private object Cell {
    fun pad(p: Painter) = min(p.wPx, p.hPx) * 0.08f
    fun labelSize(p: Painter) = (p.hPx * 0.17f).coerceIn(14f, 30f)

    /**
     * Rótulo arriba a la izquierda y, opcionalmente, otro a la derecha. Si los dos no caben con
     * separación, se encogen juntos para no pisarse. Devuelve la base del rótulo.
     */
    fun header(p: Painter, pal: Palette, left: String, right: String? = null, rightColor: Int? = null): Float {
        val pad = pad(p)
        var ls = labelSize(p)
        if (right != null) {
            val gap = pad * 1.5f
            val need = p.wTxt(left, ls) + p.wTxt(right, ls) + gap
            val avail = p.wPx - pad * 2
            if (need > avail) ls *= (avail - gap) / (need - gap)
        }
        val base = pad + labelSize(p) * 0.8f
        p.txt(left, pad, base, ls, pal.label)
        if (right != null) p.txt(right, p.wPx - pad, base, ls, rightColor ?: pal.unit, A.R)
        return base
    }
}

/** Viento: flecha relativa a tu marcha, km/h y de cara / lateral / a favor. */
object VientoCompactLayout : Layout {
    override val types = emptyList<String>()
    override val compact = true

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val w = s.weather
        val color = if (w.hasWind) Common.windColor(w, pal) else pal.text
        val top = Cell.header(p, pal, "VIENTO", w.side?.label, color)
        val pad = Cell.pad(p)
        val areaTop = top + pad * 0.5f
        val areaH = p.hPx - areaTop - pad
        val arrow = min(areaH, p.wPx * 0.35f)
        if (w.hasWind) Common.windArrow(p, w, pal, pad + arrow / 2f, areaTop + areaH / 2f, arrow)

        val v = Fmt.int(w.windKmh)
        val unitSize = Cell.labelSize(p)
        val unitW = p.wTxt("km/h", unitSize)
        val maxW = p.wPx - pad * 3 - arrow - unitW
        val size = p.fitNum(v, areaH * 0.9f / Painter.CAP, maxW)
        val base = areaTop + areaH / 2f + size * Painter.CAP / 2f
        p.txt("km/h", p.wPx - pad, base, unitSize, pal.unit, A.R)
        p.num(v, p.wPx - pad - unitW - pad * 0.4f, base, size, color, A.R)
    }
}

/** Velocidad actual y distancia en un solo campo: lado a lado si la celda es ancha, una sobre otra si es alta. */
object VelocidadDistanciaCompactLayout : Layout {
    override val types = listOf(Type.SPEED, Type.DISTANCE)
    override val compact = true

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val pad = Cell.pad(p)
        val speed = Fmt.dec1(s.speed(s.v(Type.SPEED)))
        val dist = Fmt.dec1(s.dist(s.v(Type.DISTANCE)))
        val ls = Cell.labelSize(p)

        if (p.wPx >= p.hPx * 1.6f) {
            // Ancha: velocidad a la izquierda, distancia a la derecha
            val top = Cell.header(p, pal, "VELOCIDAD", "DISTANCIA")
            val areaTop = top + pad * 0.5f
            val areaH = p.hPx - areaTop - pad
            val half = (p.wPx - pad * 3) / 2f
            val us = ls * 0.9f
            val sw = p.wTxt(s.speedUnit, us)
            val dw = p.wTxt(s.distUnit, us)
            val sSize = p.fitNum(speed, areaH * 0.9f / Painter.CAP, half - sw - pad * 0.4f)
            val dSize = p.fitNum(dist, areaH * 0.9f / Painter.CAP, half - dw - pad * 0.4f)
            val size = min(sSize, dSize) // mismo tamaño para las dos cifras
            val b = areaTop + areaH / 2f + size * Painter.CAP / 2f
            val nw = p.num(speed, pad, b, size, pal.text)
            p.txt(s.speedUnit, pad + nw + pad * 0.4f, b, us, pal.unit)
            p.txt(s.distUnit, p.wPx - pad, b, us, pal.unit, A.R)
            p.num(dist, p.wPx - pad - dw - pad * 0.4f, b, size, pal.secondary, A.R)
        } else {
            // Alta: velocidad arriba, distancia abajo
            val rowH = (p.hPx - pad * 2) / 2f
            for ((i, item) in listOf(Triple("VELOCIDAD", speed, s.speedUnit), Triple("DISTANCIA", dist, s.distUnit)).withIndex()) {
                val y0 = pad + rowH * i
                val lb = y0 + ls * 0.8f
                p.txt(item.first, pad, lb, ls, pal.label)
                val areaH = rowH - ls - pad * 0.5f
                val us = ls * 0.9f
                val uw = p.wTxt(item.third, us)
                val size = p.fitNum(item.second, areaH * 0.9f / Painter.CAP, p.wPx - pad * 2.4f - uw)
                val b = y0 + rowH - pad * 0.3f
                p.txt(item.third, p.wPx - pad, b, us, pal.unit, A.R)
                p.num(item.second, p.wPx - pad - uw - pad * 0.4f, b, size, if (i == 0) pal.text else pal.secondary, A.R)
            }
        }
    }
}
