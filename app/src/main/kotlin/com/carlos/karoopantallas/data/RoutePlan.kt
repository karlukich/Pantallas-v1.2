package com.carlos.karoopantallas.data

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/** Previsión horaria de un punto de la ruta: hora (epoch s) → valores. */
class ForecastSample(val distM: Double, val hours: Map<Long, Hour>) {
    class Hour(val tempC: Double?, val rainProb: Int?, val windKmh: Double?, val windFrom: Double?, val code: Int?)

    fun at(epochMs: Long): Hour? = hours[(epochMs / 3_600_000L) * 3600L]
}

data class Checkpoint(
    /** Índice del punto de la ruta más cercano (para dibujarlo en el mapa) */
    val idx: Int,
    val distM: Double,
    val etaMs: Long,
    val bearing: Double,
    val tempC: Double?,
    val rainProb: Int?,
    val windKmh: Double?,
    val windFrom: Double?,
) {
    val side: WindSide? get() = windFrom?.let { WindLayer.side(it, bearing) }
    /** Hacia dónde dibujar la flecha relativa a la marcha (0 = hacia delante). */
    val arrowDeg: Double? get() = windFrom?.let { (((it - bearing) % 360 + 360) % 360 + 180) % 360 }
}

/** Tramo de la ruta por dibujar: índices en la lista de puntos y cómo da el viento al pasar. */
class Chunk(val from: Int, val to: Int, val side: WindSide?)

data class RouteState(
    val route: List<Pair<Double, Double>> = emptyList(),
    val totalM: Double = 0.0,
    val progressM: Double = 0.0,
    val progressIdx: Int = 0,
    val etaEndMs: Long? = null,
    val checkpoints: List<Checkpoint> = emptyList(),
    val chunks: List<Chunk> = emptyList(),
    val headKm: Double = 0.0,
    val tailKm: Double = 0.0,
    val powerW: Double? = null,
    val hasForecast: Boolean = false,
) {
    val hasRoute get() = route.size >= 2
    val remainingM get() = (totalM - progressM).coerceAtLeast(0.0)

    companion object {
        val EMPTY = RouteState()
    }
}

/**
 * Plan de paso por la ruta: estima tu velocidad en cada tramo con un modelo físico (potencia,
 * peso, pendiente, rozamiento y aire), saca la hora de paso por cada punto y cruza esa hora
 * con la previsión de ese punto. Sin dependencias del Karoo para poder probarlo aparte.
 */
object RoutePlan {
    private const val STEP_M = 200.0
    private const val CHUNK_M = 300.0
    private const val G = 9.81
    private const val RHO = 1.2
    private const val EFF = 0.97
    const val MIN_WIND_KMH = 8.0
    const val CHECKPOINTS = 6

    /** Parámetros del ciclista y la bici. */
    class Rider(val powerW: Double, val massKg: Double, val cda: Double, val crr: Double, val maxKmh: Double = 55.0)

    /** Velocidad (m/s) que da [r] con su potencia en una pendiente [grade] (0,05 = 5 %). Bisección. */
    fun speed(r: Rider, grade: Double): Double {
        val th = atan(grade)
        val resist = r.massKg * G * (sin(th) + r.crr * cos(th))
        fun f(v: Double) = v * resist + 0.5 * RHO * r.cda * v * v * v - r.powerW * EFF
        var lo = 0.3
        var hi = r.maxKmh / 3.6
        if (f(hi) <= 0) return hi // bajada: tope de velocidad
        repeat(50) {
            val mid = (lo + hi) / 2
            if (f(mid) > 0) hi = mid else lo = mid
        }
        return ((lo + hi) / 2).coerceAtLeast(5.0 / 3.6)
    }

    private fun elevAt(profile: List<Pair<Double, Double>>, d: Double): Double {
        if (profile.isEmpty()) return 0.0
        if (d <= profile.first().first) return profile.first().second
        if (d >= profile.last().first) return profile.last().second
        var lo = 0
        var hi = profile.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (profile[mid].first <= d) lo = mid else hi = mid
        }
        val (d0, e0) = profile[lo]
        val (d1, e1) = profile[hi]
        return if (d1 > d0) e0 + (e1 - e0) * (d - d0) / (d1 - d0) else e0
    }

    /** Distancia acumulada (m) de cada punto de la ruta, escalada a la distancia oficial de la ruta. */
    fun cumulative(route: List<Pair<Double, Double>>, routeDistanceM: Double?): DoubleArray {
        val c = DoubleArray(route.size)
        for (i in 1 until route.size) c[i] = c[i - 1] + WindLayer.meters(route[i - 1], route[i])
        val k = if (routeDistanceM != null && routeDistanceM > 0 && c.last() > 0) routeDistanceM / c.last() else 1.0
        for (i in c.indices) c[i] *= k
        return c
    }

    private fun indexAt(cum: DoubleArray, d: Double): Int {
        var lo = 0
        var hi = cum.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cum[mid] <= d) lo = mid else hi = mid
        }
        return lo
    }

    fun compute(
        route: List<Pair<Double, Double>>,
        routeDistanceM: Double?,
        elevProfile: List<Pair<Double, Double>>,
        progressM: Double,
        rider: Rider,
        nowMs: Long,
        samples: List<ForecastSample>,
    ): RouteState {
        if (route.size < 2) return RouteState.EMPTY
        val cum = cumulative(route, routeDistanceM)
        val total = cum.last()
        val start = progressM.coerceIn(0.0, total)

        // Tiempo acumulado desde tu posición, cada 200 m
        val stepD = ArrayList<Double>()
        val stepT = ArrayList<Double>()
        var d = start
        var t = 0.0
        stepD.add(d); stepT.add(0.0)
        while (d < total) {
            val len = minOf(STEP_M, total - d)
            val grade = if (elevProfile.size >= 2) (elevAt(elevProfile, d + len) - elevAt(elevProfile, d)) / len else 0.0
            t += len / speed(rider, grade.coerceIn(-0.25, 0.25))
            d += len
            stepD.add(d); stepT.add(t)
        }
        fun etaAt(x: Double): Long {
            if (x <= start) return nowMs
            var i = 1
            while (i < stepD.size && stepD[i] < x) i++
            if (i >= stepD.size) return nowMs + (stepT.last() * 1000).toLong()
            val f = (x - stepD[i - 1]) / (stepD[i] - stepD[i - 1])
            return nowMs + ((stepT[i - 1] + f * (stepT[i] - stepT[i - 1])) * 1000).toLong()
        }
        fun sampleNear(x: Double) = samples.minByOrNull { abs(it.distM - x) }
        fun bearingAround(x: Double): Double {
            val a = indexAt(cum, (x - 150).coerceAtLeast(0.0))
            val b = indexAt(cum, (x + 150).coerceAtMost(total)).coerceAtLeast(a + 1).coerceAtMost(route.lastIndex)
            return WindLayer.bearing(route[a], route[b])
        }

        // Puntos de control repartidos por lo que queda
        val remaining = total - start
        val cps = if (remaining < 500) emptyList() else (1..CHECKPOINTS).map { k ->
            val x = start + remaining * k / CHECKPOINTS
            val eta = etaAt(x)
            val h = sampleNear(x)?.at(eta)
            Checkpoint(indexAt(cum, x), x, eta, bearingAround(x), h?.tempC, h?.rainProb, h?.windKmh, h?.windFrom)
        }

        // Tramos de ~300 m con el viento que habrá al pasar
        val chunks = ArrayList<Chunk>()
        var head = 0.0
        var tail = 0.0
        val i0 = indexAt(cum, start)
        var a = i0
        while (a < route.lastIndex) {
            var b = a + 1
            while (b < route.lastIndex && cum[b] - cum[a] < CHUNK_M) b++
            val mid = (cum[a] + cum[b]) / 2
            val h = sampleNear(mid)?.at(etaAt(mid))
            val side = if (h?.windFrom != null && (h.windKmh ?: 0.0) >= MIN_WIND_KMH) WindLayer.side(h.windFrom, WindLayer.bearing(route[a], route[b])) else null
            val len = (cum[b] - cum[a]) / 1000.0
            if (side == WindSide.HEAD) head += len
            if (side == WindSide.TAIL) tail += len
            chunks.add(Chunk(a, b, side))
            a = b
        }

        return RouteState(
            route = route, totalM = total, progressM = start, progressIdx = i0,
            etaEndMs = etaAt(total), checkpoints = cps, chunks = chunks,
            headKm = head, tailKm = tail, powerW = rider.powerW, hasForecast = samples.isNotEmpty(),
        )
    }
}
