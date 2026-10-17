package com.carlos.karoopantallas.render

import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.max

/** 2B / 2C · Velocidad y tiempo: velocidad, media, máxima, distancia, vuelta, tiempos, destino y llegada. */
object VelocidadLayout : Layout {
    override val types = listOf(
        Type.SPEED, Type.AVERAGE_SPEED, Type.MAX_SPEED,
        Type.DISTANCE, Type.DISTANCE_LAP,
        Type.ELAPSED_TIME, Type.RIDE_TIME,
        Type.DISTANCE_TO_DESTINATION, Type.TIME_TO_DESTINATION,
    )

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        // Columna derecha: media y máxima
        val avg = Fmt.dec1(s.speed(s.v(Type.AVERAGE_SPEED)))
        val maxS = Fmt.dec1(s.speed(s.v(Type.MAX_SPEED)))
        p.txt("MEDIA", R, 61.6f, 22f, pal.unit, A.R)
        p.num(avg, R, 108f, 50f, pal.secondary, A.R)
        p.txt("MÁX", R, 140.6f, 22f, pal.unit, A.R)
        p.num(maxS, R, 187f, 50f, pal.secondary, A.R)
        val colW = max(max(p.wNum(avg, 50f), p.wNum(maxS, 50f)), p.wTxt("MEDIA", 22f))

        // Velocidad
        val v = Fmt.dec1(s.speed(s.v(Type.SPEED)))
        val unit = s.speedUnit
        val size = p.fitNum(v, 138f, R - colW - 14f - 18f - 6f - p.wTxt(unit, 24f))
        val w = p.num(v, 18f, 185.2f, size, pal.text, sy = 1.15f)
        p.txt(unit, 18f + w + 6f, 185f, 24f, pal.unit)

        // Distancia total y de la vuelta
        p.txt("DISTANCIA", 18f, 228.6f, 22f, pal.label)
        p.numDec(Fmt.dec2(s.dist(s.v(Type.DISTANCE))), 18f, 322.3f, 92f, pal.text,
            unit = s.distUnit, unitSize = 22f, unitColor = pal.unit, maxW = 222f)
        p.txt("VUELTA", R, 228.6f, 22f, pal.label, A.R)
        p.numDec(Fmt.dec2(s.dist(s.v(Type.DISTANCE_LAP))), R, 322.3f, 92f, pal.text, A.R,
            unit = s.distUnit, unitSize = 22f, unitColor = pal.unit, maxW = R - 262f)

        // Tiempos
        p.txt("EN MOVIMIENTO", 18f, 368.6f, 22f, pal.label)
        val moving = Fmt.duration(s.v(Type.ELAPSED_TIME))
        p.num(moving, 18f, 449.4f, p.fitNum(moving, 61f, 215f), pal.text)
        p.txt("TOTAL", R, 368.6f, 22f, pal.label, A.R)
        val total = Fmt.duration(s.v(Type.RIDE_TIME))
        p.num(total, R, 449.4f, p.fitNum(total, 61f, R - 250f), pal.text, A.R)

        // Destino y hora de llegada (solo con ruta cargada)
        p.txt("A DESTINO", 18f, 508.6f, 22f, pal.label)
        val toDest = s.v(Type.DISTANCE_TO_DESTINATION, Field.DISTANCE_TO_DESTINATION)
        p.numDec(Fmt.dec2(s.dist(toDest)), 18f, 599.1f, 83f, pal.text,
            unit = if (toDest != null) s.distUnit else null, unitSize = 22f, unitColor = pal.unit, maxW = 222f)
        p.txt("LLEGADA", R, 508.6f, 22f, pal.label, A.R)
        val ttd = s.v(Type.TIME_TO_DESTINATION)
        val eta = if (ttd != null && ttd > 0 && toDest != null) Fmt.clock(System.currentTimeMillis() + ttd.toLong()) else Fmt.NONE
        p.num(eta, R, 599.1f, 83f, pal.lime, A.R)
    }
}
