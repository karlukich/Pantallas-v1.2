package com.carlos.karoopantallas.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Capa de viento del mapa: trocea la ruta cargada en tramos de ~300 m, calcula el rumbo de cada
 * tramo y lo clasifica con el viento previsto (de cara / lateral / a favor). Los tramos seguidos
 * con la misma clase se unen en una sola línea para no saturar el mapa.
 * Sin dependencias del Karoo para poder probarla aparte.
 */
object WindLayer {
    private const val CHUNK_M = 300.0
    private const val MAX_RUNS = 150

    /** Colores ARGB con algo de transparencia para que se vea la ruta del Karoo debajo. */
    const val COLOR_HEAD = 0xE6E53935.toInt()
    const val COLOR_CROSS = 0xE6F2A900.toInt()
    const val COLOR_TAIL = 0xE62E9E4F.toInt()

    class Run(val side: WindSide, val points: List<Pair<Double, Double>>) {
        val color: Int
            get() = when (side) {
                WindSide.HEAD -> COLOR_HEAD
                WindSide.TAIL -> COLOR_TAIL
                else -> COLOR_CROSS
            }
    }

    fun side(windFrom: Double, bearing: Double): WindSide {
        val r = ((windFrom - bearing) % 360 + 360) % 360
        return when {
            r <= 45 || r >= 315 -> WindSide.HEAD
            r in 135.0..225.0 -> WindSide.TAIL
            r < 180 -> WindSide.CROSS_R
            else -> WindSide.CROSS_L
        }
    }

    fun runs(route: List<Pair<Double, Double>>, windFrom: Double): List<Run> {
        if (route.size < 2) return emptyList()
        // 1) Trozos de ~300 m con su rumbo
        val chunks = ArrayList<Pair<List<Pair<Double, Double>>, Double>>()
        var cur = arrayListOf(route[0])
        var acc = 0.0
        for (i in 1 until route.size) {
            acc += meters(route[i - 1], route[i])
            cur.add(route[i])
            if (acc >= CHUNK_M || i == route.lastIndex) {
                chunks.add(cur to bearing(cur.first(), cur.last()))
                cur = arrayListOf(route[i])
                acc = 0.0
            }
        }
        // 2) Unir trozos seguidos con la misma clase (lateral izq./dcha. cuentan como uno)
        val out = ArrayList<Run>()
        var side: WindSide? = null
        var pts = ArrayList<Pair<Double, Double>>()
        for ((c, b) in chunks) {
            val s = side(windFrom, b).let { if (it == WindSide.CROSS_L) WindSide.CROSS_R else it }
            if (s != side && pts.isNotEmpty()) {
                out.add(Run(side!!, pts))
                pts = arrayListOf(pts.last())
            }
            side = s
            if (pts.isEmpty()) pts.addAll(c) else pts.addAll(c.drop(1))
        }
        if (pts.size >= 2 && side != null) out.add(Run(side, pts))
        // Si salen demasiados tramos, el mapa se resiente: nos quedamos con los primeros (lo más cercano).
        return if (out.size > MAX_RUNS) out.subList(0, MAX_RUNS) else out
    }

    fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second) * cos(Math.toRadians((a.first + b.first) / 2))
        return 6_371_000.0 * sqrt(dLat * dLat + dLon * dLon)
    }

    fun bearing(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val p1 = Math.toRadians(a.first)
        val p2 = Math.toRadians(b.first)
        val dl = Math.toRadians(b.second - a.second)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
