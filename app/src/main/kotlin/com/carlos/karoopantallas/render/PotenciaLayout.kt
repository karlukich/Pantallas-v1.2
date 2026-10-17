package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.max

/** 3B / 3C · Potencia a fondo: potencia 3 s con zona, W/kg, medias de 10 s / 30 s / vuelta, NP, IF, TSS y kJ. */
object PotenciaLayout : Layout {
    override val types = listOf(
        Type.SMOOTHED_3S_AVERAGE_POWER, Type.POWER_TO_WEIGHT,
        Type.SMOOTHED_10S_AVERAGE_POWER, Type.SMOOTHED_30S_AVERAGE_POWER, Type.POWER_LAP,
        Type.NORMALIZED_POWER, Type.AVERAGE_POWER, Type.MAX_POWER,
        Type.INTENSITY_FACTOR, Type.TRAINING_STRESS_SCORE, Type.ENERGY_OUTPUT,
    )

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val watts = s.v(Type.SMOOTHED_3S_AVERAGE_POWER)
        val zone = s.powerZone(watts)
        val color = zone?.let { pal.zone(it) } ?: pal.neutral
        val range = s.powerZoneRange(zone)

        p.rect(0f, 0f, 14f, 230f, color)
        p.txt("POTENCIA 3 S", 32f, 28.6f, 22f, pal.label)
        p.txt(Common.zoneLimits(range, zone == s.powerZoneCount - 1, "W"), R, 29.4f, 18f, pal.unit, A.R)
        p.bar(32f, 38.2f, R, 50.2f, s.fractionInZone(watts, range), pal.track, color)

        val wkgValue = s.v(Type.POWER_TO_WEIGHT)
            ?: s.profile?.weight?.takeIf { it > 0 }?.let { w -> watts?.div(w) }
        val wkg = Fmt.dec1(wkgValue)
        p.txt("W/KG", R, 166.6f, 24f, pal.unit, A.R)
        p.num(wkg, R, 216.6f, 54f, pal.secondary, A.R)
        val wkgW = max(p.wNum(wkg, 54f), p.wTxt("W/KG", 24f))

        Common.heroWithZone(
            p, Fmt.int(watts), 32f, 213.5f, 165f, 1.05f, if (zone != null) color else pal.text,
            zone?.let { "Z${it + 1}" }, 48f, 122.9f, color,
            "W", 32f, 214.6f, pal.unit,
            gap = 10f, maxRight = R - wkgW - 16f,
        )

        Common.row3(p, pal, 257f, 339.6f, 76f, listOf(
            Triple("10 S", Fmt.int(s.v(Type.SMOOTHED_10S_AVERAGE_POWER)), null),
            Triple("30 S", Fmt.int(s.v(Type.SMOOTHED_30S_AVERAGE_POWER)), null),
            Triple("VUELTA", Fmt.int(s.v(Type.POWER_LAP)), null),
        ))
        Common.row3(p, pal, 387f, 469.6f, 76f, listOf(
            Triple("NORM", Fmt.int(s.v(Type.NORMALIZED_POWER)), null),
            Triple("MEDIA", Fmt.int(s.v(Type.AVERAGE_POWER)), null),
            Triple("MÁX", Fmt.int(s.v(Type.MAX_POWER)), null),
        ))

        val ifv = s.v(Type.INTENSITY_FACTOR)
        val ifColor = when {
            ifv == null || ifv < 0.85 -> null
            ifv < 1.0 -> pal.zone(3)
            else -> pal.zone(4)
        }
        // La energía puede llegar en julios o en kilojulios
        val energy = s.v(Type.ENERGY_OUTPUT)?.let { if (it > 50_000) it / 1000.0 else it }
        Common.row3(p, pal, 517f, 594f, 60f, listOf(
            Triple("IF", Fmt.dec2(ifv), ifColor),
            Triple("TSS", Fmt.int(s.v(Type.TRAINING_STRESS_SCORE)), null),
            Triple("KJ", Fmt.int(energy), null),
        ))
    }
}
