package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.DeviceBattery
import com.carlos.karoopantallas.data.Devices
import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.max
import kotlin.math.min

/**
 * 12 · Baterías (maqueta 10B / 10C): batería del Karoo y de cada sensor emparejado (horquilla, amortiguador,
 * tija, cambio, potenciómetro y el resto), en 5 niveles, con aviso de lo que hay que cargar.
 */
object BateriasLayout : Layout {
    override val types = listOf(Type.BATTERY_PERCENT)

    private const val TOP = 126f

    private fun levelColor(b: DeviceBattery, pal: Palette) = when (b.level) {
        5, 4 -> pal.zone(1)
        3 -> pal.zone(2)
        2 -> pal.zone(3)
        1 -> pal.zone(4)
        else -> pal.unit
    }

    private fun levelText(b: DeviceBattery) = when (b.level) {
        5 -> "NUEVA"
        4 -> "BIEN"
        3 -> "MEDIA"
        2 -> "BAJA"
        1 -> "CRÍTICA"
        else -> Fmt.NONE
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        p.txt("BATERÍAS", 32f, 34f, 22f, pal.label)
        val karoo = s.v(Type.BATTERY_PERCENT)
        val kw = p.txt("%", R, 34f, 16f, pal.unit, A.R)
        val nw = p.num(Fmt.int(karoo), R - kw - 4f, 34f, 32f, pal.text, A.R)
        p.txt("KAROO", R - kw - 4f - nw - 6f, 34f, 16f, pal.unit, A.R)

        val list = Devices.current()
        // Aviso
        p.rect(32f, 50f, R, 116f, pal.track)
        val low = list.filter { it.low }
        val (title, text, color) = when {
            list.isEmpty() -> Triple("SIN DATOS", "No hay sensores emparejados con batería", pal.unit)
            low.isEmpty() -> Triple("BIEN", "Todo con batería para la salida", pal.zone(1))
            low.size == 1 -> Triple("CARGA", "${pretty(low[0].label)} está baja. El resto aguanta", if (low[0].level == 1) pal.zone(4) else pal.zone(3))
            else -> Triple("CARGA", low.joinToString(", ") { pretty(it.label) } + " están bajas", pal.zone(4))
        }
        val tw = p.txt(title, 46f, 92f, 26f, color)
        val x = 46f + tw + 12f
        val lines = wrap(p, text, R - 14f - x, 19f)
        if (lines.size == 1) p.txt(lines[0], x, 90f, 19f, pal.label)
        else { p.txt(lines[0], x, 79f, 19f, pal.label); p.txt(lines[1], x, 101f, 19f, pal.label) }

        if (list.isEmpty()) return
        val shown = list.take(7)
        val rowH = min(98f, (620f - TOP) / shown.size)
        shown.forEachIndexed { i, b -> row(p, pal, b, TOP + i * rowH, rowH) }
    }

    /** «TIJA» → «La tija», para el aviso */
    private fun pretty(label: String): String = when (label) {
        "HORQUILLA" -> "La horquilla"
        "AMORTIGUADOR" -> "El amortiguador"
        "TIJA" -> "La tija"
        "CAMBIO" -> "El cambio"
        "POTENCIÓMETRO" -> "El potenciómetro"
        else -> label.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun row(p: Painter, pal: Palette, b: DeviceBattery, top: Float, h: Float) {
        val color = levelColor(b, pal)
        if (b.low) p.rect(0f, top, 14f, top + h, color)
        p.rect(32f, top, R, top + 2f, pal.track)
        val mid = top + h / 2f
        // Barra de 5 tramos a la derecha
        val segW = 22f
        val segGap = 3f
        val barW = 5 * segW + 4 * segGap
        val segH = min(34f, h - 40f).coerceAtLeast(16f)
        val barTop = mid - (segH + 24f) / 2f
        for (i in 0 until 5) {
            val sx = R - barW + i * (segW + segGap)
            p.rect(sx, barTop, sx + segW, barTop + segH, if (i < b.level) color else pal.track)
        }
        val tw = p.txt(levelText(b), R, barTop + segH + 22f, 18f, color, A.R)
        ago(b.updatedMs)?.let { p.txt(it, R - tw - 8f, barTop + segH + 22f, 16f, pal.unit, A.R) }

        // Nombre y sensor a la izquierda (recortados si no caben)
        val maxW = R - barW - 16f - 32f
        p.txt(clip(p, b.label, maxW, 24f), 32f, mid - 2f, 24f, pal.label)
        p.txt(clip(p, b.device, maxW, 16f), 32f, mid + 22f, 16f, pal.unit)
    }

    private fun ago(ms: Long?): String? {
        if (ms == null || ms <= 0) return null
        val min = max(0L, (System.currentTimeMillis() - ms) / 60_000)
        return when {
            min < 1 -> "ahora"
            min < 60 -> "hace $min min"
            min < 48 * 60 -> "hace ${min / 60} h"
            else -> "hace ${min / 1440} d"
        }
    }

    private fun clip(p: Painter, s: String, maxW: Float, size: Float): String {
        if (p.wTxt(s, size) <= maxW) return s
        var t = s
        while (t.length > 1 && p.wTxt("$t…", size) > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun wrap(p: Painter, text: String, maxW: Float, size: Float): List<String> {
        if (p.wTxt(text, size) <= maxW) return listOf(text)
        val words = text.split(" ")
        var first = ""
        var i = 0
        while (i < words.size) {
            val next = if (first.isEmpty()) words[i] else "$first ${words[i]}"
            if (p.wTxt(next, size) > maxW && first.isNotEmpty()) break
            first = next
            i++
        }
        return listOf(first, clip(p, words.drop(i).joinToString(" "), maxW, size))
    }
}
