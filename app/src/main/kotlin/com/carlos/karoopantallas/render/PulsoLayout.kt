package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 4B / 4C · Pulso a fondo: pulso con zona, % FC máx, medias, tiempo en zona, Z4+, deriva y reparto por zonas. */
object PulsoLayout : Layout {
    override val types = listOf(
        Type.HEART_RATE, Type.PERCENT_MAX_HR,
        Type.AVERAGE_HR, Type.MAX_HR, Type.AVERAGE_LAP_HR,
    )

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val bpm = s.v(Type.HEART_RATE)
        val zone = s.hrZone(bpm)
        val color = zone?.let { pal.zone(it) } ?: pal.neutral
        val range = s.hrZoneRange(zone)

        p.rect(0f, 0f, 14f, 230f, color)
        p.txt("PULSO", 32f, 28.6f, 22f, pal.label)
        p.txt(Common.zoneLimits(range, zone == s.hrZoneCount - 1, ""), R, 29.4f, 18f, pal.unit, A.R)
        p.bar(32f, 38.2f, R, 50.2f, s.fractionInZone(bpm, range), pal.track, color)

        // % de la FC máxima (por si llega como fracción, se pasa a %)
        val pctValue = s.v(Type.PERCENT_MAX_HR)?.let { if (it <= 1.5) it * 100 else it }
            ?: s.profile?.maxHr?.takeIf { it > 0 }?.let { m -> bpm?.div(m)?.times(100) }
        val pct = Fmt.int(pctValue)
        p.txt("%FC", R, 166.6f, 24f, pal.unit, A.R)
        p.num(pct, R, 216.6f, 54f, pal.secondary, A.R)
        val pctW = max(p.wNum(pct, 54f), p.wTxt("%FC", 24f))

        Common.heroWithZone(
            p, Fmt.int(bpm), 32f, 213.5f, 165f, 1.05f, if (zone != null) color else pal.text,
            zone?.let { "Z${it + 1}" }, 48f, 122.9f, color,
            "PPM", 32f, 214.6f, pal.unit,
            gap = 10f, maxRight = R - pctW - 16f,
        )

        Common.row3(p, pal, 257f, 339.6f, 76f, listOf(
            Triple("MEDIA", Fmt.int(s.v(Type.AVERAGE_HR)), null),
            Triple("MÁX", Fmt.int(s.v(Type.MAX_HR)), null),
            Triple("VUELTA", Fmt.int(s.v(Type.AVERAGE_LAP_HR)), null),
        ))

        val t = s.tracker
        val drift = t.drift?.let { d ->
            val r = d.roundToInt()
            (if (r > 0) "+" else if (r < 0) "-" else "") + abs(r) + " %"
        } ?: Fmt.NONE
        Common.row3(p, pal, 387f, 455.9f, 54f, listOf(
            Triple("EN ZONA", Fmt.duration(t.currentZoneMs?.toDouble()), if (zone != null) color else null),
            Triple("Z4 +", if (t.hrZoneMs.sum() > 0) Fmt.duration(t.z4PlusMs.toDouble()) else Fmt.NONE, pal.zone(3)),
            Triple("DERIVA", drift, null),
        ))

        Common.zoneBars(p, pal, s.tracker.hrZoneMs, zone)
    }
}
