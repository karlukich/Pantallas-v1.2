package com.carlos.karoopantallas.render

import android.graphics.Path
import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Polyline
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 5B / 5C · Subida (Climber): perfil del puerto en el que estás (el que detecta el Karoo en la ruta),
 * pendiente, VAM, lo que queda, cuándo llegas arriba, potencia, W/kg y pulso.
 */
object ClimberLayout : Layout {
    override val types = listOf(
        Type.CLIMB_NUMBER, Type.DISTANCE_TO_TOP, Type.ELEVATION_TO_TOP,
        Type.DISTANCE_FROM_BOTTOM, Type.ELEVATION_FROM_BOTTOM,
        Type.ELEVATION_GRADE, Type.AVERAGE_VERTICAL_SPEED_30S, Type.VERTICAL_SPEED, Type.SPEED,
        Type.SMOOTHED_3S_AVERAGE_POWER, Type.POWER_TO_WEIGHT, Type.HEART_RATE,
    )
    override val navigation = true

    private const val SEGMENTS = 16
    private const val PX = 32f
    private const val PY = 44f
    private const val PW = 395f
    private const val PH = 128f

    /** Perfil de la ruta ya decodificado (se guarda para no decodificarlo cada segundo) */
    private var cachedPolyline: String? = null
    private var cachedProfile: List<Pair<Double, Double>> = emptyList()

    private class Climb(val number: Int?, val total: Int?, val start: Double?, val length: Double, val gain: Double?, val grade: Double?)

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val climb = currentClimb(s)
        val fromBottom = s.v(Type.DISTANCE_FROM_BOTTOM)
        val toTop = s.v(Type.DISTANCE_TO_TOP)

        // Cabecera: puerto n de N y resumen
        val title = when {
            climb?.number != null && climb.total != null -> "PUERTO ${climb.number} DE ${climb.total}"
            climb != null -> "PUERTO"
            else -> "SUBIDA"
        }
        p.txt(title, 18f, 28.6f, 22f, pal.label)
        if (climb != null) {
            val parts = listOfNotNull(
                "${Fmt.dec1(s.dist(climb.length))} ${s.distUnit}",
                climb.grade?.let { "${Fmt.dec1(it)} %" },
                climb.gain?.let { "${Fmt.int(s.elev(it))} ${s.elevUnit}" },
            )
            p.txt(parts.joinToString(" · "), R, 29.4f, 18f, pal.unit, A.R)
        }

        profile(p, s, pal, climb, fromBottom)

        // Pendiente y VAM
        val g = s.v(Type.ELEVATION_GRADE)
        val gColor = pal.grade(g)
        p.rect(0f, 200f, 14f, 350f, gColor)
        p.txt("PENDIENTE", 32f, 228.6f, 22f, pal.label)
        val vam = Fmt.int(s.elev(s.v(Type.AVERAGE_VERTICAL_SPEED_30S) ?: s.v(Type.VERTICAL_SPEED)))
        p.txt("VAM", R, 283.2f, 24f, pal.unit, A.R)
        val unitW = p.txt("${s.elevUnit}/h", R, 339f, 18f, pal.unit, A.R)
        val vamW = p.num(vam, R - unitW - 4f, 339f, 60f, pal.secondary, A.R) + 4f + unitW

        val gText = Fmt.dec1(g)
        val pctW = if (g != null) 4f + p.wTxt("%", 28f) else 0f
        val gw = p.num(gText, 32f, 338f, p.fitNum(gText, 120f, R - vamW - 16f - 32f - pctW), gColor)
        if (g != null) p.txt("%", 32f + gw + 4f, 338f, 28f, gColor)

        // Quedan · Por subir · Cima en
        p.txt("QUEDAN", Common.COL_L, 377f, 20f, pal.label)
        p.numDec(Fmt.dec1(s.dist(toTop)), Common.COL_L, 458.6f, 66f, pal.text,
            unit = if (toTop != null) s.distUnit else null, unitSize = 18f, unitColor = pal.unit, unitGap = 4f, maxW = Common.COL_W)
        p.txt("POR SUBIR", Common.COL_C, 377f, 20f, pal.label, A.C)
        val elevToTop = s.v(Type.ELEVATION_TO_TOP)
        p.numDec(Fmt.int(s.elev(elevToTop)), Common.COL_C, 458.6f, 66f, pal.text, A.C,
            unit = if (elevToTop != null) s.elevUnit else null, unitSize = 18f, unitColor = pal.unit, unitGap = 4f, maxW = Common.COL_W)
        p.txt("CIMA EN", R, 377f, 20f, pal.label, A.R)
        val speed = s.v(Type.SPEED)
        val eta = if (toTop != null && speed != null && speed > 0.8) Fmt.duration(toTop / speed * 1000) else Fmt.NONE
        p.num(eta, R, 458.6f, p.fitNum(eta, 58f, Common.COL_W), pal.lime, A.R)

        // Potencia · W/kg · Pulso
        val watts = s.v(Type.SMOOTHED_3S_AVERAGE_POWER)
        val bpm = s.v(Type.HEART_RATE)
        val wkg = s.v(Type.POWER_TO_WEIGHT) ?: s.profile?.weight?.takeIf { it > 0 }?.let { w -> watts?.div(w) }
        Common.row3(p, pal, 512f, 597.1f, 76f, listOf(
            Triple("POTENCIA", Fmt.int(watts), s.powerZone(watts)?.let { pal.zone(it) }),
            Triple("W/KG", Fmt.dec1(wkg), null),
            Triple("PULSO", Fmt.int(bpm), s.hrZone(bpm)?.let { pal.zone(it) }),
        ))
    }

    /** El puerto actual: datos del Karoo y, si hay ruta cargada, su posición y desnivel en la ruta. */
    private fun currentClimb(s: Snapshot): Climb? {
        val number = s.v(Type.CLIMB_NUMBER, Field.CLIMB_NUMBER)?.roundToInt()
        val total = s.v(Type.CLIMB_NUMBER, Field.TOTAL_CLIMBS)?.roundToInt()
        val fromBottom = s.v(Type.DISTANCE_FROM_BOTTOM)
        val toTop = s.v(Type.DISTANCE_TO_TOP)
        val climbs = when (val n = s.navigation) {
            is NavigationState.NavigatingRoute -> n.climbs
            is NavigationState.NavigatingToDestination -> n.climbs
            else -> emptyList()
        }
        val nav = number?.let { climbs.getOrNull(it - 1) }
        if (nav == null && (toTop == null || toTop <= 0)) return null
        val length = nav?.length ?: ((fromBottom ?: 0.0) + (toTop ?: 0.0))
        if (length <= 0) return null
        val gain = nav?.totalElevation
            ?: s.v(Type.ELEVATION_TO_TOP)?.let { it + (s.v(Type.ELEVATION_FROM_BOTTOM) ?: 0.0) }
        val grade = nav?.grade ?: gain?.let { it / length * 100 }
        return Climb(number, total ?: climbs.size.takeIf { it > 0 }, nav?.startDistance, length, gain, grade)
    }

    @Synchronized
    private fun routeProfile(s: Snapshot): List<Pair<Double, Double>> {
        val encoded = when (val n = s.navigation) {
            is NavigationState.NavigatingRoute -> n.routeElevationPolyline
            is NavigationState.NavigatingToDestination -> n.elevationPolyline
            else -> null
        } ?: return emptyList()
        if (encoded != cachedPolyline) {
            cachedProfile = try { Polyline.decode(encoded, 1) } catch (e: Exception) { emptyList() }
            cachedPolyline = encoded
        }
        return cachedProfile
    }

    /** Altitud en una distancia de la ruta, interpolando entre los puntos del perfil. */
    private fun elevationAt(points: List<Pair<Double, Double>>, d: Double): Double {
        if (d <= points.first().first) return points.first().second
        if (d >= points.last().first) return points.last().second
        var lo = 0
        var hi = points.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (points[mid].first <= d) lo = mid else hi = mid
        }
        val (d0, e0) = points[lo]
        val (d1, e1) = points[hi]
        return if (d1 > d0) e0 + (e1 - e0) * (d - d0) / (d1 - d0) else e0
    }

    private fun profile(p: Painter, s: Snapshot, pal: Palette, climb: Climb?, fromBottom: Double?) {
        if (climb == null) {
            p.rect(PX, PY, PX + PW, PY + PH, pal.track)
            p.txt("SIN PUERTO ACTIVO", PX + PW / 2, PY + PH / 2 + 8f, 22f, pal.unit, A.C)
            p.txt("Carga una ruta con subidas para ver el perfil", PX + PW / 2, 191.8f, 16f, pal.unit, A.C)
            return
        }
        // Altitudes en los extremos de cada tramo (relativas a la base del puerto)
        val route = routeProfile(s)
        val elev = DoubleArray(SEGMENTS + 1) { i ->
            val f = i.toDouble() / SEGMENTS
            if (route.size >= 2 && climb.start != null) {
                elevationAt(route, climb.start + climb.length * f)
            } else {
                (climb.gain ?: 0.0) * f // sin perfil de ruta: rampa con la pendiente media
            }
        }
        val eMin = elev.min()
        val eMax = elev.max()
        fun y(e: Double) = PY + 124f - if (eMax > eMin) ((e - eMin) / (eMax - eMin) * 106).toFloat() else 0f
        val segW = PW / SEGMENTS
        val pos = fromBottom?.let { (it / climb.length).coerceIn(0.0, 1.0) }
        val posX = pos?.let { PX + PW * it.toFloat() }
        val segLen = climb.length / SEGMENTS
        val bottom = PY + PH

        for (i in 0 until SEGMENTS) {
            val x0 = PX + segW * i
            val x1 = x0 + segW
            val y0 = y(elev[i])
            val y1 = y(elev[i + 1])
            val color = pal.grade((elev[i + 1] - elev[i]) / segLen * 100)
            fun piece(a: Float, b: Float, alpha: Float) {
                if (b <= a) return
                val ya = y0 + (y1 - y0) * (a - x0) / segW
                val yb = y0 + (y1 - y0) * (b - x0) / segW
                val path = Path().apply { moveTo(a, bottom); lineTo(a, ya); lineTo(b, yb); lineTo(b, bottom); close() }
                p.path(path, color, alpha)
            }
            if (posX == null) {
                piece(x0, x1, 0.5f)
            } else {
                piece(x0, min(x1, posX), 1f)
                piece(max(x0, posX), x1, 0.5f)
            }
        }
        val outline = Path().apply {
            moveTo(PX, y(elev[0]))
            for (i in 1..SEGMENTS) lineTo(PX + segW * i, y(elev[i]))
        }
        p.line(outline, pal.text, 2f, 0.6f)

        if (posX != null && pos != null) {
            val dash = Path().apply { moveTo(posX, PY); lineTo(posX, bottom) }
            p.line(dash, pal.text, 2f, dash = true)
            val idx = min(SEGMENTS - 1, (pos * SEGMENTS).toInt())
            val local = (pos * SEGMENTS - idx).toFloat()
            val yPos = y(elev[idx]) + (y(elev[idx + 1]) - y(elev[idx])) * local
            p.dot(posX, yPos, 5.5f, pal.text, pal.bg)
        }

        // Eje: 0 · posición y % hecho · longitud
        p.txt("0", PX, 191.8f, 16f, pal.unit)
        if (fromBottom != null && pos != null) {
            p.txt("${Fmt.dec1(s.dist(fromBottom))} ${s.distUnit} · ${(pos * 100).roundToInt()} %", PX + PW / 2, 191.8f, 16f, pal.text, A.C)
        }
        p.txt("${Fmt.dec1(s.dist(climb.length))} ${s.distUnit}", R, 191.8f, 16f, pal.unit, A.R)
    }
}
