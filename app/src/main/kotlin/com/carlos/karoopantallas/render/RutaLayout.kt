package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.max

/** 1B / 1C · Ruta: velocidad, potencia con zona, pulso, pendiente, distancia y desnivel. */
object RutaLayout : Layout {
    override val types = listOf(
        Type.SPEED, Type.AVERAGE_SPEED,
        Type.SMOOTHED_3S_AVERAGE_POWER, Type.NORMALIZED_POWER,
        Type.HEART_RATE, Type.ELEVATION_GRADE,
        Type.DISTANCE, Type.ELEVATION_GAIN,
    )

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        speed(p, s, pal)
        power(p, s, pal)
        hrAndGrade(p, s, pal)
        distanceAndElevation(p, s, pal)
    }

    /** 0–165: velocidad estirada con km/h arriba a su derecha; media abajo a la derecha. */
    private fun speed(p: Painter, s: Snapshot, pal: Palette) {
        val avg = Fmt.dec1(s.speed(s.v(Type.AVERAGE_SPEED)))
        p.txt("MEDIA", R, 85f, 26f, pal.unit, A.R)
        p.num(avg, R, 150.8f, 72f, pal.secondary, A.R)
        val avgW = max(p.wNum(avg, 72f), p.wTxt("MEDIA", 26f))

        val v = Fmt.dec1(s.speed(s.v(Type.SPEED)))
        val unit = s.speedUnit
        val maxW = R - avgW - 14f - 18f - 8f - p.wTxt(unit, 24f)
        val size = p.fitNum(v, 138f, maxW)
        val w = p.num(v, 18f, 150.2f, size, pal.text, sy = 1.23f)
        // km/h con su parte de arriba alineada con la de la cifra
        val pivot = 150.2f + 0.05f * size
        val top = pivot - (pivot - (150.2f - Painter.CAP * size)) * 1.23f
        p.txt(unit, 18f + w + 8f, top + Painter.CAP * 24f, 24f, pal.unit)
    }

    /** 165–320: franja y barra de zona, cifra en color de zona con Zx / W; NORM a la derecha. */
    private fun power(p: Painter, s: Snapshot, pal: Palette) {
        val watts = s.v(Type.SMOOTHED_3S_AVERAGE_POWER)
        val zone = s.powerZone(watts)
        val color = zone?.let { pal.zone(it) } ?: pal.neutral
        p.rect(0f, 165f, 14f, 320f, color)
        p.bar(32f, 175f, R, 187f, s.fractionInZone(watts, s.powerZoneRange(zone)), pal.track, color)

        val norm = Fmt.int(s.v(Type.NORMALIZED_POWER))
        p.txt("NORM", R, 249.9f, 26f, pal.unit, A.R)
        p.num(norm, R, 305.9f, 61f, pal.secondary, A.R)
        val normW = max(p.wNum(norm, 61f), p.wTxt("NORM", 26f))

        Common.heroWithZone(
            p, Fmt.int(watts), 32f, 304.4f, 136f, 1.05f, if (zone != null) color else pal.text,
            zone?.let { "Z${it + 1}" }, 48f, 239f, color,
            "W", 36f, 303.8f, pal.unit,
            gap = 10f, maxRight = R - normW - 16f,
        )
    }

    /** 320–470: pulso (izquierda, con franja de zona) y pendiente (derecha, color por inclinación). */
    private fun hrAndGrade(p: Painter, s: Snapshot, pal: Palette) {
        val bpm = s.v(Type.HEART_RATE)
        val zone = s.hrZone(bpm)
        val color = zone?.let { pal.zone(it) } ?: pal.neutral
        p.rect(0f, 320f, 14f, 470f, color)
        p.txt("PULSO", 32f, 348.6f, 22f, pal.label)
        Common.heroWithZone(
            p, Fmt.int(bpm), 32f, 456f, 110f, 1.12f, if (zone != null) color else pal.text,
            zone?.let { "Z${it + 1}" }, 42f, 402.6f, color,
            "PPM", 24f, 455.2f, pal.unit,
            gap = 6f, maxRight = 250f,
        )

        val g = s.v(Type.ELEVATION_GRADE)
        val gColor = pal.grade(g)
        p.txt("PENDIENTE", R, 348.6f, 22f, pal.label, A.R)
        val text = Fmt.dec1(g)
        if (g == null) {
            p.num(text, R, 448.7f, 96f, gColor, A.R)
        } else {
            val pctW = p.num("%", R, 448.7f, 24f, gColor, A.R)
            val x = R - pctW - 4f
            p.num(text, x, 448.7f, p.fitNum(text, 96f, x - 262f), gColor, A.R)
        }
    }

    /** 470–620: distancia con 2 decimales (a mitad de tamaño) y desnivel acumulado. */
    private fun distanceAndElevation(p: Painter, s: Snapshot, pal: Palette) {
        val lw = p.txt("DISTANCIA ", 18f, 498.6f, 22f, pal.label)
        p.txt(s.distUnit, 18f + lw, 498.6f, 22f, pal.unit)
        p.numDec(Fmt.dec2(s.dist(s.v(Type.DISTANCE))), 18f, 595.9f, 88f, pal.text, maxW = 225f)

        p.txt("DESNIVEL +", R, 498.6f, 22f, pal.label, A.R)
        p.numDec(
            Fmt.int(s.elev(s.v(Type.ELEVATION_GAIN))), R, 595.9f, 88f, pal.text, A.R,
            unit = s.elevUnit, unitSize = 24f, unitColor = pal.unit, unitGap = 6f, maxW = R - 258f,
        )
    }
}
