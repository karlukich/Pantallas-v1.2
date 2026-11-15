package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.data.WeatherState
import com.carlos.karoopantallas.data.WindSide
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** Una pantalla: qué datos necesita del Karoo y cómo se dibuja (coordenadas de diseño 480 × 620). */
interface Layout {
    val types: List<String>
    /** Si necesita el estado de navegación (perfil de la ruta y puertos) */
    val navigation: Boolean get() = false
    /**
     * Campo pequeño (para la página del mapa u otras con varios campos): se dibuja en píxeles reales
     * ([Painter.wPx] × [Painter.hPx]) en vez de en el lienzo de diseño 480 × 620.
     */
    val compact: Boolean get() = false
    fun draw(p: Painter, s: Snapshot, pal: Palette)
}

/** Piezas comunes a varias pantallas. */
internal object Common {
    const val R = Painter.RIGHT
    /** Columnas de las filas de tres datos: izquierda, centro y derecha */
    const val COL_L = 32f
    const val COL_C = 229.5f
    const val COL_W = 125f

    /** Fila de tres datos (rótulo arriba, cifra debajo), alineados izquierda / centro / derecha. */
    fun row3(
        p: Painter, pal: Palette, labelBase: Float, valueBase: Float, size: Float,
        cells: List<Triple<String, String, Int?>>,
    ) {
        val xs = listOf(COL_L to A.L, COL_C to A.C, R to A.R)
        cells.forEachIndexed { i, (label, value, color) ->
            val (x, a) = xs[i]
            p.txt(label, x, labelBase, 20f, pal.label, a)
            p.num(value, x, valueBase, p.fitNum(value, size, COL_W), color ?: pal.text, a)
        }
    }

    /** "191 W · 228 W" o "> 340 W" para la última zona */
    fun zoneLimits(z: UserProfile.Zone?, last: Boolean, unit: String): String {
        if (z == null) return ""
        val u = if (unit.isEmpty()) "" else " $unit"
        return if (last) "> ${z.min}$u" else "${z.min}$u · ${z.max}$u"
    }

    /**
     * Cifra grande con la zona encima a su derecha y la unidad abajo (263 Z3 / W), como en 1B, 3B y 4B.
     * Devuelve el borde derecho de lo dibujado.
     */
    fun heroWithZone(
        p: Painter, value: String, x: Float, base: Float, size: Float, sy: Float, color: Int,
        zone: String?, zoneSize: Float, zoneBase: Float, zoneColor: Int,
        unit: String, unitSize: Float, unitBase: Float, unitColor: Int,
        gap: Float, maxRight: Float,
    ): Float {
        val colW = max(p.wTxt(zone ?: "Z0", zoneSize), p.wTxt(unit, unitSize))
        val sz = p.fitNum(value, size, maxRight - x - gap - colW)
        val w = p.num(value, x, base, sz, color, sy = sy)
        if (zone != null) p.txt(zone, x + w + gap, zoneBase, zoneSize, zoneColor)
        p.txt(unit, x + w + gap, unitBase, unitSize, unitColor)
        return x + w + gap + colW
    }

    /** Color del viento: de cara rojo, lateral amarillo, a favor verde; sin rumbo, el de texto; dato viejo, gris. */
    fun windColor(w: WeatherState, pal: Palette): Int = if (w.stale) pal.unit else when (w.side) {
        WindSide.HEAD -> pal.zone(4)
        WindSide.TAIL -> pal.lime
        WindSide.CROSS_L, WindSide.CROSS_R -> pal.zone(2)
        null -> pal.text
    }

    /** Flecha del viento relativa a tu marcha (o absoluta respecto al norte si aún no hay rumbo). */
    fun windArrow(p: Painter, w: WeatherState, pal: Palette, cx: Float, cy: Float, size: Float) {
        val deg = w.arrowDeg ?: w.windFrom?.let { (it + 180) % 360 } ?: return
        p.arrow(cx, cy, size, deg.toFloat(), windColor(w, pal))
    }

    /** «TIEMPO EN ZONAS»: barra con el reparto real del tiempo en cada zona (pulso o potencia), 500–620. */
    fun zoneBars(p: Painter, pal: Palette, ms: List<Long>, current: Int?) {
        val total = ms.sum()
        p.txt("TIEMPO EN ZONAS", 32f, 508.6f, 22f, pal.label)
        if (current != null && total > 0) {
            val share = (ms.getOrElse(current) { 0L } * 100.0 / total).roundToInt()
            p.txt("Z${current + 1} · $share %", R, 509.4f, 18f, pal.unit, A.R)
        }
        val top = 538.5f
        val bottom = 572.5f
        if (total <= 0) {
            p.rect(32f, top, R, bottom, pal.track)
            return
        }
        val used = ms.withIndex().filter { it.value > 0 }
        val gap = 3f
        val avail = (R - 32f) - gap * (used.size - 1)
        var x = 32f
        for ((i, v) in used) {
            val w = avail * v / total
            val isCurrent = i == current
            p.rect(x, top, x + w, bottom, pal.zone(i), if (isCurrent) 1f else 0.55f)
            if (v * 100.0 / total >= 9) p.txt("Z${i + 1}", x, 590.3f, 16f, if (isCurrent) pal.text else pal.unit)
            x += w + gap
        }
    }

    /** Fila de cuatro datos (rótulo arriba, cifra debajo): izquierda, dos centradas y derecha. */
    fun row4(
        p: Painter, pal: Palette, labelBase: Float, valueBase: Float, size: Float,
        cells: List<Triple<String, String, Int?>>,
    ) {
        val colW = ((R - COL_L) - 3 * 10f) / 4
        val xs = listOf(
            COL_L to A.L,
            COL_L + colW + 10f + colW / 2 to A.C,
            COL_L + 2 * (colW + 10f) + colW / 2 to A.C,
            R to A.R,
        )
        cells.forEachIndexed { i, (label, value, color) ->
            val (x, a) = xs[i]
            p.txt(label, x, labelBase, 20f, pal.label, a)
            p.num(value, x, valueBase, p.fitNum(value, size, colW), color ?: pal.text, a)
        }
    }

    fun int(v: Double?) = Fmt.int(v)
    fun dec1(v: Double?) = Fmt.dec1(v)
    fun dec2(v: Double?) = Fmt.dec2(v)
}
