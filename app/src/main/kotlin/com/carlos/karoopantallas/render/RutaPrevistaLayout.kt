package com.carlos.karoopantallas.render

import android.graphics.Path
import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.RouteState
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.data.WindSide
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 8 · Ruta prevista: la ruta cargada en miniatura con los tramos coloreados por el viento que
 * tendrás al pasar, y seis puntos de control con hora estimada de paso, temperatura, lluvia y
 * viento relativo. La velocidad sale de [com.carlos.karoopantallas.data.RoutePlan].
 */
object RutaPrevistaLayout : Layout {
    override val types = emptyList<String>()

    private const val MX0 = 18f
    private const val MY0 = 48f
    private const val MX1 = R
    private const val MY1 = 258f

    private fun sideColor(side: WindSide?, pal: Palette): Int = when (side) {
        WindSide.HEAD -> pal.zone(4)
        WindSide.TAIL -> pal.lime
        WindSide.CROSS_L, WindSide.CROSS_R -> pal.zone(2)
        null -> pal.secondary
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val r = s.route
        if (!r.hasRoute) {
            p.txt("SIN RUTA CARGADA", 18f, 60f, 30f, pal.label)
            p.txt("Carga una ruta para ver la previsión", 18f, 96f, 20f, pal.unit)
            p.txt("a tu paso por cada tramo.", 18f, 122f, 20f, pal.unit)
            return
        }

        // Cabecera: lo que queda y la llegada
        val lw = p.txt("QUEDAN ", 18f, 34f, 22f, pal.label)
        p.txt("${Fmt.dec1(s.dist(r.remainingM))} ${s.distUnit}", 18f + lw, 34f, 22f, pal.text)
        val eta = r.etaEndMs?.let { Fmt.clock(it) } ?: Fmt.NONE
        val ew = p.num(eta, R, 37f, 34f, pal.lime, A.R)
        p.txt("LLEGADA", R - ew - 8f, 34f, 18f, pal.unit, A.R)

        miniMap(p, r, pal)

        // Resumen de viento
        if (r.hasForecast) {
            var x = 18f
            x += p.txt("VIENTO  ", x, 288f, 20f, pal.label)
            x += p.txt("de cara ${Fmt.int(r.headKm)} km", x, 288f, 20f, pal.zone(4))
            x += p.txt("  ·  ", x, 288f, 20f, pal.unit)
            p.txt("a favor ${Fmt.int(r.tailKm)} km", x, 288f, 20f, pal.lime)
        } else {
            p.txt("Previsión en camino (wifi o móvil)", 18f, 288f, 20f, pal.unit)
        }

        table(p, s, r, pal)

        r.powerW?.let { p.txt("Paso calculado con ${it.roundToInt()} W, tu peso y la pendiente", R, 612f, 14f, pal.unit, A.R) }
    }

    private fun miniMap(p: Painter, r: RouteState, pal: Palette) {
        val pts = r.route
        val lat0 = pts.minOf { it.first }
        val lat1 = pts.maxOf { it.first }
        val lng0 = pts.minOf { it.second }
        val lng1 = pts.maxOf { it.second }
        val k = cos(Math.toRadians((lat0 + lat1) / 2))
        val spanX = ((lng1 - lng0) * k).coerceAtLeast(1e-6)
        val spanY = (lat1 - lat0).coerceAtLeast(1e-6)
        val scale = min((MX1 - MX0 - 20f) / spanX, (MY1 - MY0 - 20f) / spanY)
        val cx = (MX0 + MX1) / 2
        val cy = (MY0 + MY1) / 2
        val mLng = (lng0 + lng1) / 2
        val mLat = (lat0 + lat1) / 2
        fun x(i: Int) = (cx + (pts[i].second - mLng) * k * scale).toFloat()
        fun y(i: Int) = (cy - (pts[i].first - mLat) * scale).toFloat()

        p.rect(MX0, MY0, MX1, MY1, pal.track, 0.5f)
        // Hecho, en gris
        if (r.progressIdx > 0) {
            val done = Path().apply { moveTo(x(0), y(0)); for (i in 1..r.progressIdx) lineTo(x(i), y(i)) }
            p.line(done, pal.neutral, 5f)
        }
        // Lo que queda, por tramos según el viento al pasar
        for (c in r.chunks) {
            val path = Path().apply { moveTo(x(c.from), y(c.from)); for (i in c.from + 1..c.to) lineTo(x(i), y(i)) }
            p.line(path, sideColor(c.side, pal), 7f)
        }
        // Puntos de control numerados
        r.checkpoints.forEachIndexed { n, cp ->
            p.dot(x(cp.idx), y(cp.idx), 9f, pal.bg, pal.text)
            p.txt("${n + 1}", x(cp.idx), y(cp.idx) + 5f, 14f, pal.text, A.C)
        }
        // Tu posición
        p.dot(x(r.progressIdx), y(r.progressIdx), 6f, pal.text, pal.bg)
    }

    private fun table(p: Painter, s: Snapshot, r: RouteState, pal: Palette) {
        val hy = 322f
        p.txt("PUNTO", 18f, hy, 16f, pal.unit)
        p.txt("HORA", 168f, hy, 16f, pal.unit, A.C)
        p.txt("°C", 238f, hy, 16f, pal.unit, A.C)
        p.txt("LLUVIA", 312f, hy, 16f, pal.unit, A.C)
        p.txt("VIENTO", R, hy, 16f, pal.unit, A.R)
        if (r.checkpoints.isEmpty()) {
            p.txt("Estás llegando al final de la ruta", 18f, 370f, 20f, pal.unit)
            return
        }
        r.checkpoints.forEachIndexed { n, cp ->
            val base = 362f + n * 43f
            p.dot(28f, base - 11f, 11f, pal.bg, pal.text)
            p.txt("${n + 1}", 28f, base - 5f, 15f, pal.text, A.C)
            p.num(Fmt.dist(s.dist(cp.distM)), 48f, base, 30f, pal.text)
            p.num(Fmt.clock(cp.etaMs), 168f, base, 30f, pal.secondary, A.C)
            p.num(Fmt.int(cp.tempC), 238f, base, 30f, pal.text, A.C)
            val rain = cp.rainProb
            val rc = when {
                rain == null -> pal.text
                rain >= 60 -> pal.zone(0)
                rain >= 30 -> pal.zone(2)
                else -> pal.text
            }
            p.num(rain?.let { "$it%" } ?: Fmt.NONE, 312f, base, 30f, rc, A.C)
            val wc = if ((cp.windKmh ?: 0.0) >= 8.0) sideColor(cp.side, pal) else pal.text
            val ww = p.num(Fmt.int(cp.windKmh), R, base, 30f, wc, A.R)
            cp.arrowDeg?.let { p.arrow(R - ww - 18f, base - 11f, 24f, it.toFloat(), wc) }
        }
    }
}
