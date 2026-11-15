package com.carlos.karoopantallas.render

import android.graphics.Path
import com.carlos.karoopantallas.data.AdviceKind
import com.carlos.karoopantallas.data.Fmt
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.render.Common.R
import com.carlos.karoopantallas.render.Painter.A
import io.hammerhead.karooext.models.DataType.Type
import kotlin.math.roundToInt

/**
 * 9 · Stamina (maqueta 7B / 7C): stamina potencial y actual con barras, recomendación según la evolución,
 * tiempo y km que quedan a este ritmo, W' restante, potencia, % FTP de 5 min y gráfica de la salida.
 * Los cálculos están en [com.carlos.karoopantallas.data.Stamina].
 */
object StaminaLayout : Layout {
    override val types = listOf(Type.SMOOTHED_3S_AVERAGE_POWER)

    private const val L = 18f
    private const val BAR_L = 178f
    private const val MID = 222.5f

    /** Color según el nivel: > 50 % turquesa, > 25 % amarillo, > 10 % naranja y rojo por debajo. */
    private fun level(v: Double?, pal: Palette): Int = when {
        v == null -> pal.neutral
        v > 50 -> pal.zone(1)
        v > 25 -> pal.zone(2)
        v > 10 -> pal.zone(3)
        else -> pal.zone(4)
    }

    override fun draw(p: Painter, s: Snapshot, pal: Palette) {
        val st = s.stamina
        val potColor = level(st.potential, pal)
        val curColor = level(st.current, pal)

        // ---------- Cabecera y barras (0–240) ----------
        p.rect(0f, 0f, 14f, 240f, curColor)
        p.txt("STAMINA", L, 29f, 22f, pal.label)
        val info = "FTP ${Fmt.int(st.ftp)} W · W' ${(st.wPrimeJ / 1000).roundToInt()} kJ"
        p.txt(info, R, 29f, 18f, pal.unit, A.R)

        bigPct(p, st.potential, 133.2f, potColor)
        p.txt("POTENCIAL", BAR_L, 77.2f, 20f, pal.label)
        p.bar(BAR_L, 88f, R, 134f, st.potential?.div(100), pal.track, potColor)

        bigPct(p, st.current, 239.2f, curColor)
        p.txt("ACTUAL", BAR_L, 183.2f, 20f, pal.label)
        p.bar(BAR_L, 194f, R, 240f, st.current?.div(100), pal.track, curColor)
        // Raya donde está la potencial: la actual nunca puede pasar de ahí
        st.potential?.let {
            val x = BAR_L + (R - BAR_L) * (it / 100).toFloat().coerceIn(0f, 1f)
            p.rect(x - 2f, 188f, x + 2f, 240f, pal.text)
        }

        // ---------- Recomendación (244–306) ----------
        advice(p, s, pal)

        // ---------- Te quedan (306–420) ----------
        p.txt("A ESTE RITMO TE QUEDAN", L, 333f, 22f, pal.label)
        val time = st.remainingS?.let { if (it >= 24 * 3600) "+24:00" else hm(it) } ?: Fmt.NONE
        val tw = p.num(time, L, 400.7f, 72f, pal.lime)
        if (st.remainingS != null) p.txt("h", L + tw + 5f, 400.7f, 22f, pal.unit)
        p.numDec(
            Fmt.dec2(s.dist(st.remainingM)), R, 400.7f, 72f, pal.text, A.R,
            unit = if (st.remainingM != null) s.distUnit else null, unitSize = 22f, unitColor = pal.unit, maxW = R - 230f,
        )

        // ---------- W' · Potencia · % FTP 5 min (420–500) ----------
        val watts = s.v(Type.SMOOTHED_3S_AVERAGE_POWER)
        val zone = s.powerZone(watts)
        cell(p, pal, "W'", Fmt.dec1(st.wBalJ?.div(1000)), "kJ", L, A.L, pal.text)
        cell(p, pal, "POTENCIA", Fmt.int(watts), "W", MID, A.C, zone?.let { pal.zone(it) } ?: pal.text)
        cell(p, pal, "%FTP 5 MIN", Fmt.int(st.pctFtp5), "%", R, A.R, pal.text)

        // ---------- Evolución (500–620) ----------
        graph(p, st.history, st.elapsedS, pal, curColor)
    }

    private fun bigPct(p: Painter, v: Double?, base: Float, color: Int) {
        val text = Fmt.int(v)
        val size = p.fitNum(text, 100f, BAR_L - 14f - 32f - (if (v != null) 4f + p.wTxt("%", 28f) else 0f))
        val w = p.num(text, 32f, base, size, color)
        if (v != null) p.txt("%", 32f + w + 4f, base, 28f, color)
    }

    private fun hm(seconds: Double): String {
        val m = (seconds / 60).roundToInt()
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** Dato con rótulo arriba y unidad pequeña detrás de la cifra. */
    private fun cell(p: Painter, pal: Palette, label: String, value: String, unit: String, x: Float, a: A, color: Int) {
        p.txt(label, x, 433f, 20f, pal.label, a)
        val uw = p.wTxt(unit, 18f)
        val size = p.fitNum(value, 54f, 125f - uw - 4f)
        val total = p.wNum(value, size) + 4f + uw
        val left = when (a) {
            A.L -> x
            A.C -> x - total / 2f
            A.R -> x - total
        }
        val nw = p.num(value, left, 490.7f, size, color)
        p.txt(unit, left + nw + 4f, 490.7f, 18f, pal.unit)
    }

    private fun advice(p: Painter, s: Snapshot, pal: Palette) {
        val st = s.stamina
        val adv = st.advice
        val (title, text, color) = when {
            st.ftp == null -> Triple("SIN FTP", "Pon tu FTP en el perfil del Karoo", pal.unit)
            adv == null -> Triple("EN ESPERA", "Los consejos salen tras el primer minuto grabando", pal.unit)
            else -> Triple(
                when (adv.kind) {
                    AdviceKind.AFLOJA -> "AFLOJA"
                    AdviceKind.NO_LLEGAS -> "NO LLEGAS"
                    AdviceKind.RECUPERA -> "RECUPERA"
                    AdviceKind.COME -> "COME"
                    AdviceKind.MARGEN -> "TIENES MARGEN"
                    AdviceKind.SOSTENIBLE -> "BIEN"
                },
                adv.text,
                when (adv.kind) {
                    AdviceKind.AFLOJA -> pal.zone(4)
                    AdviceKind.NO_LLEGAS -> pal.zone(3)
                    AdviceKind.RECUPERA -> pal.zone(2)
                    AdviceKind.COME -> pal.lime
                    AdviceKind.MARGEN -> pal.zone(1)
                    AdviceKind.SOSTENIBLE -> pal.unit
                },
            )
        }
        p.rect(L, 244f, R, 306f, pal.track)
        val tw = p.txt(title, 32f, 284f, 26f, color)
        val x = 32f + tw + 12f
        val lines = wrap(p, text, R - 14f - x, 19f)
        when (lines.size) {
            1 -> p.txt(lines[0], x, 282f, 19f, pal.label)
            else -> {
                p.txt(lines[0], x, 271f, 19f, pal.label)
                p.txt(lines[1], x, 293f, 19f, pal.label)
            }
        }
    }

    /** Parte el texto en dos líneas como mucho; si no cabe, recorta con «…». */
    private fun wrap(p: Painter, text: String, maxW: Float, size: Float): List<String> {
        if (p.wTxt(text, size) <= maxW) return listOf(text)
        val words = text.split(" ")
        var first = ""
        var i = 0
        while (i < words.size) {
            val next = if (first.isEmpty()) words[i] else "$first ${words[i]}"
            if (p.wTxt(next, size) > maxW && first.isNotEmpty()) break
            first = next
            i++
        }
        var second = words.drop(i).joinToString(" ")
        while (second.isNotEmpty() && p.wTxt(second, size) > maxW) second = second.dropLast(2) + "…"
        return listOf(first, second)
    }

    private fun graph(p: Painter, hist: List<Pair<Float, Float>>, elapsedS: Int, pal: Palette, curColor: Int) {
        p.txt("EVOLUCIÓN", L, 531f, 22f, pal.label)
        // Leyenda
        val aw = p.txt("ACTUAL", R, 529.5f, 16f, pal.unit, A.R)
        val sw = R - aw - 5f - 16f
        p.rect(sw, 526f, sw + 16f, 534f, curColor)
        val pw = p.txt("POTENCIAL", sw - 14f, 529.5f, 16f, pal.unit, A.R)
        val lx = sw - 14f - pw - 5f - 16f
        p.rect(lx, 528.5f, lx + 16f, 531.5f, pal.text)

        val top = 545f
        val bottom = 595f
        val h = bottom - top
        p.rect(L, top + h / 2 - 1f, R, top + h / 2 + 1f, pal.track)
        p.rect(L, bottom - 1f, R, bottom + 1f, pal.track)

        // Un punto por minuto; empieza en el 100 % al minuto 0
        val pts = listOf(100f to 100f) + hist
        if (pts.size >= 2) {
            val n = pts.size - 1
            fun x(i: Int) = L + (R - L) * i / n
            fun y(v: Float) = bottom - h * (v.coerceIn(0f, 100f) / 100f)
            val area = Path().apply {
                moveTo(L, bottom)
                pts.forEachIndexed { i, pr -> lineTo(x(i), y(pr.second)) }
                lineTo(R, bottom)
                close()
            }
            p.path(area, curColor, 0.35f)
            val cur = Path().apply { pts.forEachIndexed { i, pr -> if (i == 0) moveTo(x(i), y(pr.second)) else lineTo(x(i), y(pr.second)) } }
            p.line(cur, curColor, 3f)
            val pot = Path().apply { pts.forEachIndexed { i, pr -> if (i == 0) moveTo(x(i), y(pr.first)) else lineTo(x(i), y(pr.first)) } }
            p.line(pot, pal.text, 3f)
        } else {
            p.txt("Se dibuja a partir del primer minuto", (L + R) / 2, top + h / 2 - 8f, 16f, pal.unit, A.C)
        }
        p.txt("0:00", L, 612f, 16f, pal.unit)
        p.txt(hm(elapsedS.toDouble()), R, 612f, 16f, pal.unit, A.R)
    }
}
