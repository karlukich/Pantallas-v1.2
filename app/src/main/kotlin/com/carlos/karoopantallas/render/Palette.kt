package com.carlos.karoopantallas.render

import android.graphics.Color

/**
 * Colores de las pantallas. B = fondo negro, C = fondo claro (con la versión oscura de cada
 * color para que se lea al sol). Mismos valores que las maquetas del lienzo.
 */
class Palette(
    val bg: Int,
    /** Cifras principales */
    val text: Int,
    /** Rótulos (PULSO, PENDIENTE…) */
    val label: Int,
    /** Unidades y rótulos secundarios (km/h, W, MEDIA…) */
    val unit: Int,
    /** Cifras secundarias (media, NORM…) */
    val secondary: Int,
    /** Fondo de las barras de progreso */
    val track: Int,
    /** Verde de llegada / cima */
    val lime: Int,
    /** Franja lateral cuando no hay zona (sin sensor) */
    val neutral: Int,
    /** Colores de zona: Z1…Z7 (el pulso usa Z1…Z5). Z3 = amarillo, como en las maquetas. */
    private val zones: List<Int>,
    /** Pendiente: <1 %, <3 %, <6 %, <9 %, <12 %, resto */
    private val grades: List<Int>,
) {
    fun zone(index: Int): Int = zones[index.coerceIn(0, zones.lastIndex)]

    fun grade(g: Double?): Int = when {
        g == null || g < 1 -> grades[0]
        g < 3 -> grades[1]
        g < 6 -> grades[2]
        g < 9 -> grades[3]
        g < 12 -> grades[4]
        else -> grades[5]
    }

    companion object {
        private fun c(hex: String) = Color.parseColor(hex)

        val DARK = Palette(
            bg = c("#000000"),
            text = c("#FFFFFF"),
            label = c("#E6EAEE"),
            unit = c("#AEB8C4"),
            secondary = c("#E6EAEE"),
            track = c("#1E252E"),
            lime = c("#C8F03C"),
            neutral = c("#3A4552"),
            zones = listOf("#6FA8FF", "#35D0BA", "#F4E04D", "#FF8A1F", "#FF5A4E", "#C86BFF", "#FF4FD8").map(::c),
            grades = listOf("#FFFFFF", "#35D0BA", "#F4E04D", "#FF8A1F", "#FF5A4E", "#C86BFF").map(::c),
        )

        val LIGHT = Palette(
            bg = c("#F4F2EC"),
            text = c("#0C0F13"),
            label = c("#1A2028"),
            unit = c("#4A525C"),
            secondary = c("#1A2028"),
            track = c("#DDD9CE"),
            lime = c("#4D7A00"),
            neutral = c("#B9B5AA"),
            zones = listOf("#1F5FD1", "#0B8A78", "#A07D00", "#C85A00", "#C62828", "#7B2FBF", "#B0167A").map(::c),
            grades = listOf("#0C0F13", "#0B8A78", "#A07D00", "#C85A00", "#C62828", "#7B2FBF").map(::c),
        )
    }
}
