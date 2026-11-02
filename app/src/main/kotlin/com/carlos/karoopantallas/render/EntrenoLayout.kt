package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Settings
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.max

/**
 * 6B / 6C · Entreno: sigue el entreno estructurado que esté ejecutando el Karoo (el que llega
 * sincronizado desde intervals.icu). Potencia 3 s frente al objetivo del paso, barra con el rango,
 * cuenta atrás del paso, cadencia frente a su objetivo, paso n de N y lo que queda de entreno.
 * Sin entreno cargado muestra «SIN ENTRENO» y sigue enseñando potencia, cadencia y pulso.
 */
object EntrenoLayout : Layout {
    override val types = listOf(
        Type.SMOOTHED_3S_AVERAGE_POWER, Type.SMOOTHED_5S_AVERAGE_POWER, Type.SMOOTHED_10S_AVERAGE_POWER,
        Type.SMOOTHED_3S_AVERAGE_CADENCE, Type.HEART_RATE,
        Type.POWER_LAP,
        Type.WORKOUT_POWER_TARGET, Type.WORKOUT_CADENCE_TARGET,
        Type.WORKOUT_REMAINING_INTERVAL_DURATION, Type.WORKOUT_REMAINING_TOTAL_DURATION,
        Type.WORKOUT_INTERVAL_COUNT,
    )

    /** Rango objetivo [lo, hi]; si el paso tiene un único valor, se usa ± [tol]. */
    private data class Target(val lo: Double, val hi: Double)

    private fun target(s: Snapshot, type: String, tol: Double): Target? {
        val lo = s.v(type, Field.WORKOUT_TARGET_MIN_VALUE)?.takeIf { it > 0 }
        val hi = s.v(type, Field.WORKOUT_TARGET_MAX_VALUE)?.takeIf { it > 0 }
        val v = s.v(type, Field.WORKOUT_TARGET_VALUE)?.takeIf { it > 0 }
        return when {
            lo != null && hi != null && hi > lo -> Target(lo, hi)
            v != null -> Target(v * (1 - tol), v * (1 + tol))
            lo != null -> Target(lo * (1 - tol), lo * (1 + tol))
            else -> null
        }
    }

    /** Debajo / dentro / encima del rango (con la tolerancia del ajuste) → azul / verde / rojo. */
    private fun statusColor(value: Double?, t: Target?, pal: Palette): Int? {
        if (value == null || t == null) return null
        val tol = Settings.workoutTolerance.value / 100.0
        return when {
            value < t.lo * (1 - tol) -> pal.zone(0)
            value > t.hi * (1 + tol) -> pal.zone(4)
            else -> pal.lime
        }
    }

    /** Potencia que se compara con el objetivo: 3, 5 o 10 s según el ajuste. */
    private fun powerType() = when (Settings.workoutPower.value) {
        1 -> Type.SMOOTHED_5S_AVERAGE_POWER
        2 -> Type.SMOOTHED_10S_AVERAGE_POWER
        else -> Type.SMOOTHED_3S_AVERAGE_POWER
    }

    private fun powerLabel() = when (Settings.workoutPower.value) {
        1 -> "POTENCIA 5 S"
        2 -> "POTENCIA 10 S"
        else -> "POTENCIA 3 S"
    }

    /**
     * El SDK no documenta la unidad de los tiempos del entreno. El Karoo usa milisegundos en el
     * resto de tiempos; si el total restante supera un día en segundos, son milisegundos seguro.
     * Una vez detectado se recuerda para toda la salida.
     */
    @Volatile private var millis: Boolean? = null

    /** Para el pitido de fin de paso (Coach): tiempo que queda del paso en ms, o null. */
    fun stepLeftMs(intervalRemaining: Double?, total: Double?): Double? = toMs(intervalRemaining, total)

    private fun toMs(x: Double?, total: Double?): Double? {
        if (x == null) return null
        if (millis == null && total != null && total > 0) millis = total > 86_400
        return if (millis == false) x * 1000 else x
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val watts = s.v(powerType())
        val pTarget = target(s, Type.WORKOUT_POWER_TARGET, 0.03)
        val inWorkout = s.has(Type.WORKOUT_INTERVAL_COUNT) || pTarget != null
        val pColor = statusColor(watts, pTarget, pal)
        val stripe = pColor ?: pal.neutral

        p.rect(0f, 0f, 14f, 470f, stripe)
        header(p, s, pal, inWorkout)
        power(p, pal, watts, pTarget, pColor)
        bar(p, pal, watts, pTarget, pColor)
        stepAndCadence(p, s, pal, inWorkout)

        val bpm = s.v(Type.HEART_RATE)
        val hrZone = s.hrZone(bpm)
        val w = s.weather
        val wind = Fmt.int(w.windKmh)
        Common.row3(p, pal, 512f, 594f, 64f, listOf(
            Triple("PULSO", Fmt.int(bpm), hrZone?.let { pal.zone(it) }),
            Triple("VUELTA W", Fmt.int(s.v(Type.POWER_LAP)), null),
            Triple(w.side?.label ?: "VIENTO", wind, if (w.hasWind) Common.windColor(w, pal) else null),
        ))
        if (w.hasWind) {
            val nw = p.wNum(wind, p.fitNum(wind, 64f, Common.COL_W))
            Common.windArrow(p, w, pal, R - nw - 26f, 572f, 40f)
        }
    }

    /** 0–50: paso n de N a la izquierda y lo que queda de entreno a la derecha. */
    private fun header(p: Painter, s: Snapshot, pal: Palette, inWorkout: Boolean) {
        if (!inWorkout) {
            p.txt("SIN ENTRENO", 32f, 36f, 26f, pal.label)
            p.txt("carga uno en el Karoo", R, 36f, 20f, pal.unit, A.R)
            return
        }
        val step = s.v(Type.WORKOUT_INTERVAL_COUNT, Field.WORKOUT_CURRENT_STEP)
        val count = s.v(Type.WORKOUT_INTERVAL_COUNT, Field.WORKOUT_STEP_COUNT)
        val stepText = if (step != null && count != null && count > 0) {
            // El Karoo puede contar desde 0
            val shown = if (step < count) step + 1 else step
            "PASO ${Fmt.int(shown)} / ${Fmt.int(count)}"
        } else "PASO --"
        p.txt(stepText, 32f, 36f, 26f, pal.label)

        val total = s.v(Type.WORKOUT_REMAINING_TOTAL_DURATION, Field.WORKOUT_REMAINING_TIME)
        val rem = Fmt.duration(toMs(total, total))
        val rw = p.num(rem, R, 38f, 34f, pal.secondary, A.R)
        p.txt("QUEDA", R - rw - 8f, 36f, 20f, pal.unit, A.R)
    }

    /** 55–235: potencia 3 s en el color del estado; objetivo del paso a la derecha. */
    private fun power(p: Painter, pal: Palette, watts: Double?, t: Target?, color: Int?) {
        p.txt(powerLabel(), 32f, 84f, 22f, pal.label)
        p.txt("OBJETIVO", R, 84f, 22f, pal.label, A.R)

        val tText = t?.let { "${Fmt.int(it.lo)}–${Fmt.int(it.hi)}" } ?: Fmt.NONE
        val tSize = p.fitNum(tText, 64f, 190f)
        p.num(tText, R, 152f, tSize, pal.secondary, A.R)
        p.txt("W", R, 188f, 24f, pal.unit, A.R)
        val tW = max(p.wNum(tText, tSize), p.wTxt("OBJETIVO", 22f))

        val v = Fmt.int(watts)
        val unitW = p.wTxt("W", 32f)
        val size = p.fitNum(v, 160f, R - tW - 24f - 32f - 10f - unitW)
        val w = p.num(v, 32f, 228f, size, color ?: pal.text, sy = 1.05f)
        p.txt("W", 32f + w + 10f, 228f, 32f, pal.unit)
    }

    /**
     * 248–290: barra con el rango objetivo resaltado y una marca en la potencia actual.
     * La escala va de un 30 % por debajo del objetivo a un 30 % por encima.
     */
    private fun bar(p: Painter, pal: Palette, watts: Double?, t: Target?, color: Int?) {
        val l = 32f
        val r = R
        p.rect(l, 256f, r, 282f, pal.track)
        if (t == null) return
        val min = t.lo * 0.7
        val maxV = t.hi * 1.3
        fun x(v: Double) = (l + (r - l) * ((v - min) / (maxV - min)).coerceIn(0.0, 1.0)).toFloat()
        p.rect(x(t.lo), 256f, x(t.hi), 282f, pal.lime, alpha = 0.45f)
        if (watts != null) {
            val mx = x(watts)
            p.rect(mx - 4f, 246f, mx + 4f, 292f, color ?: pal.text)
        }
    }

    /** 300–470: cuenta atrás del paso (izquierda) y cadencia frente a su objetivo (derecha). */
    private fun stepAndCadence(p: Painter, s: Snapshot, pal: Palette, inWorkout: Boolean) {
        p.txt("FIN DEL PASO", 32f, 330f, 22f, pal.label)
        val total = s.v(Type.WORKOUT_REMAINING_TOTAL_DURATION, Field.WORKOUT_REMAINING_TIME)
        val stepLeft = if (inWorkout) {
            toMs(s.v(Type.WORKOUT_REMAINING_INTERVAL_DURATION, Field.WORKOUT_TIME_TO_STEP_FINISH), total)
        } else null
        val stepText = Fmt.duration(stepLeft)
        // Últimos segundos del paso en naranja para avisar del cambio (ajuste «Aviso de fin de paso»)
        val warnMs = Settings.stepWarn.value * 1000.0
        val warn = warnMs > 0 && stepLeft != null && stepLeft in 0.0..warnMs
        p.num(stepText, 32f, 455f, p.fitNum(stepText, 120f, 230f), if (warn) pal.zone(3) else pal.text)

        val rpm = s.v(Type.SMOOTHED_3S_AVERAGE_CADENCE)
        val cTarget = target(s, Type.WORKOUT_CADENCE_TARGET, 0.03)
        p.txt("CADENCIA", R, 330f, 22f, pal.label, A.R)
        p.num(Fmt.int(rpm), R, 420f, 84f, statusColor(rpm, cTarget, pal) ?: pal.text, A.R)
        val sub = cTarget?.let { "OBJ ${Fmt.int(it.lo)}–${Fmt.int(it.hi)} rpm" } ?: "rpm"
        p.txt(sub, R, 455f, 22f, pal.unit, A.R)
    }
}
