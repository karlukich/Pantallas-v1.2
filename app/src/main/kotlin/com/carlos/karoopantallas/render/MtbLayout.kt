package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Settings
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.roundToInt

/**
 * 11 · MTB con Flight Attendant (maqueta 9B / 9C): posición de horquilla y amortiguador (OPEN / PEDAL / LOCK),
 * modo, bias, LSC, zona de esfuerzo, cambios de posición y batería; potencia y pulso con zona; velocidad y
 * pendiente; distancia, desnivel y presión de ruedas (o piñón si no hay sensores de presión).
 *
 * El Karoo da posición, modo y zona como números sin documentar. Se supone 0 = OPEN, 1 = PEDAL, 2 = LOCK y
 * 0 = AUTO, 1 = MANUAL, 2 = OVERRIDE; el ajuste «Flight Attendant: valores en bruto» los muestra para comprobarlo.
 */
object MtbLayout : Layout {
    override val types = listOf(
        Type.SUSPENSION_STATE_FRONT, Type.SUSPENSION_STATE_REAR, Type.SUSPENSION_MODE,
        Type.SUSPENSION_STATE_COUNT_FRONT, Type.SUSPENSION_STATE_COUNT_REAR, Type.SUSPENSION_BATTERY,
        Type.SMOOTHED_3S_AVERAGE_POWER, Type.HEART_RATE, Type.SPEED, Type.ELEVATION_GRADE,
        Type.DISTANCE, Type.ELEVATION_GAIN, Type.TIRE_PRESSURE_FRONT, Type.TIRE_PRESSURE_REAR, Type.SHIFTING_REAR_GEAR,
    )

    private const val L = 18f
    private val POSITIONS = listOf("OPEN", "PEDAL", "LOCK")

    private fun posColor(i: Int, pal: Palette) = when (i) {
        0 -> pal.zone(1)
        1 -> pal.zone(2)
        else -> pal.zone(4)
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        suspension(p, s, pal)
        riding(p, s, pal)
    }

    // ---------- Suspensión (0–222) ----------
    private fun suspension(p: Painter, s: Snapshot, pal: Palette) {
        val front = s.v(Type.SUSPENSION_STATE_FRONT, Field.SUSPENSION_STATE_FRONT)?.roundToInt()
        val rear = s.v(Type.SUSPENSION_STATE_REAR, Field.SUSPENSION_STATE_REAR)?.roundToInt()
        val mode = s.v(Type.SUSPENSION_MODE, Field.SUSPENSION_MODE)?.roundToInt()
        val zone = s.v(Type.SUSPENSION_MODE, Field.SUSPENSION_EFFORT_ZONE)?.roundToInt()
        val bias = s.v(Type.SUSPENSION_MODE, Field.SUSPENSION_BIAS)?.roundToInt()
        val lscF = s.v(Type.SUSPENSION_MODE, Field.SUSPENSION_LSC_FRONT)?.roundToInt()
        val lscR = s.v(Type.SUSPENSION_MODE, Field.SUSPENSION_LSC_REAR)?.roundToInt()
        val countF = s.v(Type.SUSPENSION_STATE_COUNT_FRONT, Field.SUSPENSION_STATE_COUNT_FRONT)?.roundToInt()
        val countR = s.v(Type.SUSPENSION_STATE_COUNT_REAR, Field.SUSPENSION_STATE_COUNT_REAR)?.roundToInt()
        val bat = s.v(Type.SUSPENSION_BATTERY, Field.SUSPENSION_BATTERY_STATUS)?.roundToInt()

        val zoneColor = zone?.let { pal.zone((it - 1).coerceIn(0, 4)) } ?: pal.neutral
        p.rect(0f, 0f, 14f, 222f, zoneColor)

        val tw = p.txt("SUSPENSIÓN", L, 29f, 22f, pal.label)
        if (front == null && rear == null && mode == null) {
            p.txt("SIN FLIGHT ATTENDANT", L, 90f, 26f, pal.unit)
            p.txt("Enciende la suspensión y emparéjala con el Karoo", L, 120f, 18f, pal.unit)
            return
        }

        // Modo
        val (modeText, modeColor) = when (mode) {
            0 -> "AUTO" to pal.lime
            1 -> "MANUAL" to pal.unit
            2 -> "OVERRIDE" to pal.zone(3)
            null -> Fmt.NONE to pal.track
            else -> "MODO $mode" to pal.zone(4)
        }
        val bx = L + tw + 10f
        val bw = p.wTxt(modeText, 16f) + 16f
        p.rect(bx, 12f, bx + bw, 34f, modeColor)
        p.txt(modeText, bx + 8f, 29f, 16f, pal.bg)

        // Bias de −2 (firme) a +2 (blando)
        if (bias != null) {
            val vw = p.txt(if (bias > 0) "+$bias" else "$bias", R, 29f, 18f, pal.text, A.R)
            var x = R - vw - 8f - 5 * 18f
            for (b in -2..2) {
                p.dot(x + 7f, 23f, 5f, if (b == bias) pal.text else pal.track, pal.unit)
                x += 18f
            }
            p.txt("BIAS", R - vw - 8f - 5 * 18f - 6f, 29f, 16f, pal.unit, A.R)
        }

        selector(p, pal, "HORQUILLA", lscF, front, 42f)
        selector(p, pal, "AMORTIG.", lscR, rear, 98f)

        // Zona de esfuerzo · cambios · batería
        var x = L
        x += p.txt("ESFUERZO", x, 180f, 16f, pal.unit) + 6f
        x += p.num(zone?.let { "Z$it" } ?: Fmt.NONE, x, 180f, 26f, zoneColor)
        val counts = if (countF != null || countR != null) "${countF ?: 0}·${countR ?: 0}" else Fmt.NONE
        val cw = p.wTxt("CAMBIOS", 16f) + 6f + p.wNum(counts, 26f)
        val cx = (x + 20f + R - 110f) / 2f - cw / 2f
        p.txt("CAMBIOS", cx, 180f, 16f, pal.unit)
        p.num(counts, cx + p.wTxt("CAMBIOS", 16f) + 6f, 180f, 26f, pal.text)
        val status = bat?.let { BatteryStatus.entries.getOrNull(it) }
        val (bt, bc) = when (status) {
            BatteryStatus.NEW, BatteryStatus.GOOD, BatteryStatus.OK -> "ALTA" to pal.zone(1)
            BatteryStatus.LOW -> "BAJA" to pal.zone(3)
            BatteryStatus.CRITICAL -> "CRÍTICA" to pal.zone(4)
            else -> Fmt.NONE to pal.unit
        }
        val bw2 = p.txt(bt, R, 180f, 18f, bc, A.R)
        p.txt("BAT.", R - bw2 - 6f, 180f, 16f, pal.unit, A.R)

        if (Settings.faRaw.value) {
            p.txt(
                "bruto: H=${front ?: "-"} A=${rear ?: "-"} modo=${mode ?: "-"} zona=${zone ?: "-"} bias=${bias ?: "-"} bat=${bat ?: "-"}",
                L, 208f, 14f, pal.unit,
            )
        }
    }

    /** Fila de horquilla o amortiguador: nombre, LSC y los tres botones con la posición actual iluminada. */
    private fun selector(p: Painter, pal: Palette, name: String, lsc: Int?, current: Int?, top: Float) {
        p.txt(name, L, top + 20f, 20f, pal.label)
        p.txt("LSC ${lsc ?: Fmt.NONE}", L, top + 40f, 16f, pal.unit)
        val left = 136f
        val gap = 4f
        val w = (R - left - 2 * gap) / 3f
        for (i in 0..2) {
            val x = left + i * (w + gap)
            val on = i == current
            p.rect(x, top, x + w, top + 46f, if (on) posColor(i, pal) else pal.track)
            p.txt(POSITIONS[i], x + w / 2f, top + 30f, 20f, if (on) pal.bg else pal.unit, A.C)
        }
    }

    // ---------- Datos de marcha (222–620) ----------
    private fun riding(p: Painter, s: Snapshot, pal: Palette) {
        // Potencia y pulso
        val watts = s.v(Type.SMOOTHED_3S_AVERAGE_POWER)
        val pz = s.powerZone(watts)
        val pc = pz?.let { pal.zone(it) } ?: pal.text
        p.rect(0f, 222f, 14f, 352f, pz?.let { pal.zone(it) } ?: pal.neutral)
        p.txt("POTENCIA W", 32f, 250f, 20f, pal.label)
        val pw = p.num(Fmt.int(watts), 32f, 335f, 92f, pc)
        pz?.let { p.txt("Z${it + 1}", 32f + pw + 6f, 335f, 28f, pc) }

        val bpm = s.v(Type.HEART_RATE)
        val hz = s.hrZone(bpm)
        val hc = hz?.let { pal.zone(it) } ?: pal.text
        p.txt("PULSO", R, 250f, 20f, pal.label, A.R)
        val zw = hz?.let { p.txt("Z${it + 1}", R, 335f, 28f, hc, A.R) + 6f } ?: 0f
        p.num(Fmt.int(bpm), R - zw, 335f, 92f, hc, A.R)

        // Velocidad y pendiente
        p.txt("VELOCIDAD ${s.speedUnit}", 32f, 380f, 20f, pal.label)
        p.num(Fmt.dec1(s.speed(s.v(Type.SPEED))), 32f, 458f, 84f, pal.text)
        val g = s.v(Type.ELEVATION_GRADE)
        p.txt("PENDIENTE", R, 380f, 20f, pal.label, A.R)
        val gw = if (g != null) p.txt("%", R, 458f, 18f, pal.unit, A.R) + 5f else 0f
        p.num(Fmt.dec1(g), R - gw, 458f, 84f, pal.grade(g), A.R)

        // Distancia · desnivel · presión (o piñón)
        val lw = p.txt("DISTANCIA ", 32f, 503f, 20f, pal.label)
        p.txt(s.distUnit, 32f + lw, 503f, 20f, pal.unit)
        p.numDec(Fmt.dec2(s.dist(s.v(Type.DISTANCE))), 32f, 575f, 64f, pal.text, maxW = 140f)
        p.txt("DESNIVEL +", 236f, 503f, 20f, pal.label, A.C)
        val elev = s.v(Type.ELEVATION_GAIN)
        p.numDec(Fmt.int(s.elev(elev)), 236f, 575f, 64f, pal.text, A.C,
            unit = if (elev != null) s.elevUnit else null, unitSize = 18f, unitColor = pal.unit, unitGap = 4f, maxW = 130f)

        val pf = bar(s.v(Type.TIRE_PRESSURE_FRONT, Field.TIRE_PRESSURE))
        val pr = bar(s.v(Type.TIRE_PRESSURE_REAR, Field.TIRE_PRESSURE))
        if (pf != null || pr != null) {
            p.txt("PRESIÓN bar", R, 503f, 20f, pal.label, A.R)
            val fw = p.num(Fmt.dec2(pf), R, 545f, 36f, pal.text, A.R)
            p.txt("D", R - fw - 5f, 545f, 18f, pal.unit, A.R)
            val rw = p.num(Fmt.dec2(pr), R, 583f, 36f, pal.text, A.R)
            p.txt("T", R - rw - 5f, 583f, 18f, pal.unit, A.R)
        } else {
            val gear = s.v(Type.SHIFTING_REAR_GEAR, Field.SHIFTING_REAR_GEAR)?.roundToInt()
            val max = s.v(Type.SHIFTING_REAR_GEAR, Field.SHIFTING_REAR_GEAR_MAX)?.roundToInt()
            val teeth = s.v(Type.SHIFTING_REAR_GEAR, Field.SHIFTING_REAR_GEAR_TEETH)?.roundToInt()
            p.txt("PIÑÓN", R, 503f, 20f, pal.label, A.R)
            val tw = teeth?.let { p.txt("${it}t", R, 575f, 18f, pal.unit, A.R) + 5f } ?: 0f
            p.num(gear?.let { if (max != null) "$it/$max" else "$it" } ?: Fmt.NONE, R - tw, 575f, 56f, pal.text, A.R)
        }
    }

    /** Presión en bar: el Karoo puede darla en bar, psi o kPa según el sensor; se deduce por el valor. */
    private fun bar(v: Double?): Double? = when {
        v == null || v <= 0 -> null
        v < 6 -> v               // bar
        v < 80 -> v / 14.5038    // psi
        else -> v / 100.0        // kPa
    }
}
