package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Settings
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 7 · Tiempo: viento relativo a tu marcha (flecha grande, km/h y de cara / lateral / a favor),
 * rachas, efecto de cara o a favor, de dónde viene, temperatura, lluvia en la próxima hora y
 * hora de la última previsión (Open-Meteo).
 */
object TiempoLayout : Layout {
    override val types = emptyList<String>()

    private val compass = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    private fun cardinal(deg: Double?) = deg?.let { compass[(((it % 360) + 360) % 360 / 45.0).roundToInt() % 8] } ?: Fmt.NONE

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val w = s.weather
        val color = if (w.hasWind) Common.windColor(w, pal) else pal.neutral
        p.rect(0f, 0f, 14f, 300f, color)

        if (!w.hasWind) {
            p.txt("SIN PREVISIÓN", 32f, 60f, 30f, pal.label)
            val why = if (!Settings.weatherOn.value) "Actívala en los ajustes de la app" else "Esperando GPS y conexión (wifi o móvil)"
            p.txt(why, 32f, 96f, 20f, pal.unit)
        } else {
            p.txt("VIENTO", 32f, 34f, 22f, pal.label)
            Common.windArrow(p, w, pal, 125f, 165f, 190f)
            val v = Fmt.int(w.windKmh)
            p.num(v, R, 190f, p.fitNum(v, 150f, 190f), color, A.R)
            p.txt("km/h", R, 226f, 26f, pal.unit, A.R)
            p.txt(w.side?.label ?: "SIN RUMBO", R, 270f, 30f, if (w.side != null) color else pal.unit, A.R)
        }

        // Efecto: componente de cara (+) o a favor (−)
        val head = w.headKmh
        val effect = head?.let { (if (it >= 0.5) "+" else if (it <= -0.5) "−" else "") + abs(it).roundToInt() } ?: Fmt.NONE
        Common.row3(p, pal, 340f, 418f, 70f, listOf(
            Triple("RACHAS", Fmt.int(w.gustKmh), null),
            Triple(if (head != null && head < 0) "A FAVOR" else "DE CARA", effect, null),
            Triple("VIENE DEL", cardinal(w.windFrom), null),
        ))

        val rain = w.rainProb
        val rainColor = when {
            rain == null -> null
            rain >= 60 -> pal.zone(0)
            rain >= 30 -> pal.zone(2)
            else -> null
        }
        Common.row3(p, pal, 512f, 594f, 70f, listOf(
            Triple("TEMP °C", Fmt.int(w.tempC), null),
            Triple("LLUVIA 1 H", rain?.let { "$it%" } ?: Fmt.NONE, rainColor),
            Triple("PREVISIÓN", w.updated ?: Fmt.NONE, null),
        ))
    }
}
