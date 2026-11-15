package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Coach
import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Settings
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 10 · Resumen de la salida (maqueta 8B / 8C): tiempo en movimiento, distancia, desnivel y media;
 * NP, IF, TSS y kJ; pulso medio, deriva, stamina potencial e hidratos de los avisos de comer;
 * tiempo en zonas de potencia y de pulso; km a destino y hora de llegada.
 */
object ResumenLayout : Layout {
    override val types = listOf(
        Type.ELAPSED_TIME, Type.DISTANCE, Type.ELEVATION_GAIN, Type.AVERAGE_SPEED,
        Type.NORMALIZED_POWER, Type.INTENSITY_FACTOR, Type.TRAINING_STRESS_SCORE, Type.ENERGY_OUTPUT,
        Type.AVERAGE_HR, Type.DISTANCE_TO_DESTINATION, Type.TIME_TO_DESTINATION,
    )

    private const val L = 18f

    /** Un dato de una fila: rótulo arriba y cifra (con decimales a mitad de tamaño) y unidad debajo. */
    private class Cell(val label: String, val value: String, unit: String? = null, val color: Int? = null) {
        /** Sin dato («--») no se pinta la unidad */
        val unit: String? = unit.takeIf { value != Fmt.NONE }
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        // Cabecera: tiempo en movimiento
        p.txt("RESUMEN", L, 33f, 22f, pal.label)
        val tw = p.num(Fmt.duration(s.v(Type.ELAPSED_TIME)), R, 33f, 30f, pal.text, A.R)
        p.txt("EN MOVIMIENTO", R - tw - 6f, 33f, 18f, pal.unit, A.R)

        row(p, pal, 67f, 125f, 58f, listOf(
            Cell("DISTANCIA", Fmt.dec2(s.dist(s.v(Type.DISTANCE))), s.distUnit),
            Cell("DESNIVEL +", Fmt.int(s.elev(s.v(Type.ELEVATION_GAIN))), s.elevUnit),
            Cell("MEDIA", Fmt.dec1(s.speed(s.v(Type.AVERAGE_SPEED)))),
        ))

        val ifv = s.v(Type.INTENSITY_FACTOR)
        val ifColor = when {
            ifv == null || ifv < 0.85 -> null
            ifv < 1.0 -> pal.zone(3)
            else -> pal.zone(4)
        }
        // La energía puede llegar en julios o en kilojulios
        val energy = s.v(Type.ENERGY_OUTPUT)?.let { if (it > 50_000) it / 1000.0 else it }
        row(p, pal, 177f, 228f, 50f, listOf(
            Cell("NORM", Fmt.int(s.v(Type.NORMALIZED_POWER)), "W"),
            Cell("IF", Fmt.dec2(ifv), color = ifColor),
            Cell("TSS", Fmt.int(s.v(Type.TRAINING_STRESS_SCORE))),
            Cell("KJ", Fmt.int(energy)),
        ))

        val drift = s.tracker.drift?.roundToInt()
        val pot = s.stamina.potential
        row(p, pal, 269f, 320f, 50f, listOf(
            Cell("PULSO", Fmt.int(s.v(Type.AVERAGE_HR)), "ppm"),
            Cell("DERIVA", drift?.let { (if (it > 0) "+" else if (it < 0) "-" else "") + abs(it) } ?: Fmt.NONE, if (drift != null) "%" else null),
            Cell("STAMINA", Fmt.int(pot), if (pot != null) "%" else null, if (pot != null) StaminaLayout.level(pot, pal) else null),
            Cell("COMIDO", if (Settings.fuelOn.value) Coach.fuel.eatenGrams().toString() else Fmt.NONE, if (Settings.fuelOn.value) "g" else null),
        ))

        zones(p, pal, "ZONAS DE POTENCIA", s.tracker.powerZoneMs, 363f, 372f)
        zones(p, pal, "ZONAS DE PULSO", s.tracker.hrZoneMs, 443f, 452f)

        // Destino (solo con ruta)
        val toDest = s.v(Type.DISTANCE_TO_DESTINATION, Field.DISTANCE_TO_DESTINATION)
        p.txt("A DESTINO", L, 511f, 20f, pal.label)
        p.numDec(Fmt.dec2(s.dist(toDest)), L, 584f, 76f, pal.text,
            unit = if (toDest != null) s.distUnit else null, unitSize = 18f, unitColor = pal.unit, unitGap = 4f, maxW = 200f)
        p.txt("LLEGADA", R, 511f, 20f, pal.label, A.R)
        val ttd = s.v(Type.TIME_TO_DESTINATION)
        val eta = if (ttd != null && ttd > 0 && toDest != null) Fmt.clock(System.currentTimeMillis() + ttd.toLong()) else Fmt.NONE
        p.num(eta, R, 584f, 76f, pal.lime, A.R)
    }

    /**
     * Fila con los datos repartidos de borde a borde (el primero a la izquierda, el último a la derecha
     * y el espacio libre repartido entre ellos). Si no caben, se encogen todas las cifras por igual.
     */
    private fun row(p: Painter, pal: Palette, labelBase: Float, valueBase: Float, size: Float, cells: List<Cell>) {
        val avail = R - L
        val minGap = 10f * (cells.size - 1)
        fun valueW(c: Cell, sz: Float): Float {
            val comma = c.value.indexOf(',')
            val ip = if (comma >= 0) c.value.substring(0, comma) else c.value
            val dp = if (comma >= 0 && c.label == "DISTANCIA") c.value.substring(comma) else ""
            val numW = if (dp.isNotEmpty()) p.wNum(ip, sz) + 2f + p.wNum(dp, sz / 2f) else p.wNum(c.value, sz)
            return numW + (c.unit?.let { 4f + p.wTxt(it, 18f) } ?: 0f)
        }
        var sz = size
        fun widths() = cells.map { max(p.wTxt(it.label, 20f), valueW(it, sz)) }
        var ws = widths()
        while (ws.sum() + minGap > avail && sz > size * 0.6f) {
            sz -= 2f
            ws = widths()
        }
        val gap = if (cells.size > 1) (avail - ws.sum()) / (cells.size - 1) else 0f
        var x = L
        cells.forEachIndexed { i, c ->
            val w = ws[i]
            val a = when (i) {
                0 -> A.L
                cells.lastIndex -> A.R
                else -> A.C
            }
            val anchor = when (a) {
                A.L -> x
                A.C -> x + w / 2f
                A.R -> x + w
            }
            p.txt(c.label, anchor, labelBase, 20f, pal.label, a)
            val vw = valueW(c, sz)
            val left = when (a) {
                A.L -> x
                A.C -> x + (w - vw) / 2f
                A.R -> x + w - vw
            }
            val color = c.color ?: pal.text
            val comma = c.value.indexOf(',')
            var vx = left
            if (comma >= 0 && c.label == "DISTANCIA") {
                vx += p.num(c.value.substring(0, comma), vx, valueBase, sz, color)
                vx += 2f + p.num(c.value.substring(comma), vx + 2f, valueBase, sz / 2f, color)
            } else {
                vx += p.num(c.value, vx, valueBase, sz, color)
            }
            c.unit?.let { p.txt(it, vx + 4f, valueBase, 18f, pal.unit) }
            x += w + gap
        }
    }

    /** Barra fina con el reparto del tiempo por zonas; resalta la zona en la que más tiempo llevas. */
    private fun zones(p: Painter, pal: Palette, title: String, ms: List<Long>, titleBase: Float, top: Float) {
        p.txt(title, L, titleBase, 20f, pal.label)
        val total = ms.sum()
        val bottom = top + 24f
        if (total <= 0) {
            p.rect(L, top, R, bottom, pal.track)
            return
        }
        val most = ms.indices.maxByOrNull { ms[it] }
        if (most != null) p.txt("Z${most + 1} · ${(ms[most] * 100.0 / total).roundToInt()} %", R, titleBase, 18f, pal.unit, A.R)
        val used = ms.withIndex().filter { it.value > 0 }
        val gap = 3f
        val avail = (R - L) - gap * (used.size - 1)
        var x = L
        for ((i, v) in used) {
            val w = avail * v / total
            val isMost = i == most
            p.rect(x, top, x + w, bottom, pal.zone(i), if (isMost) 1f else 0.55f)
            if (v * 100.0 / total >= 9) p.txt("Z${i + 1}", x, top + 40f, 15f, if (isMost) pal.text else pal.unit)
            x += w + gap
        }
    }
}
