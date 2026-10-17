package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.max

/** Una pantalla: qué datos necesita del Karoo y cómo se dibuja (coordenadas de diseño 480 × 620). */
interface Layout {
    val types: List<String>
    /** Si necesita el estado de navegación (perfil de la ruta y puertos) */
    val navigation: Boolean get() = false
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

    fun int(v: Double?) = Fmt.int(v)
    fun dec1(v: Double?) = Fmt.dec1(v)
    fun dec2(v: Double?) = Fmt.dec2(v)
}
