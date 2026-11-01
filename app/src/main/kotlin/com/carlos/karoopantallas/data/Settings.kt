package com.carlos.karoopantallas.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes de la app, guardados en el Karoo (SharedPreferences). Se editan desde la app
 * (MainActivity) y los leen las pantallas y los avisos en cada uso, así que los cambios
 * se aplican al momento, sin reiniciar.
 */
object Settings {
    private const val FILE = "ajustes"
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** Un ajuste numérico con sus límites y paso, para que la pantalla de ajustes lo pinte sola. */
    class IntSetting(
        val key: String, val label: String, val unit: String,
        val default: Int, val min: Int, val max: Int, val step: Int,
        val help: String = "",
    ) {
        val value: Int get() = prefs?.getInt(key, default)?.coerceIn(min, max) ?: default
        fun set(v: Int) { prefs?.edit()?.putInt(key, v.coerceIn(min, max))?.apply() }
    }

    class BoolSetting(val key: String, val label: String, val default: Boolean, val help: String = "") {
        val value: Boolean get() = prefs?.getBoolean(key, default) ?: default
        fun set(v: Boolean) { prefs?.edit()?.putBoolean(key, v)?.apply() }
    }

    class ChoiceSetting(val key: String, val label: String, val options: List<String>, val default: Int, val help: String = "") {
        val value: Int get() = (prefs?.getInt(key, default) ?: default).coerceIn(0, options.lastIndex)
        fun set(v: Int) { prefs?.edit()?.putInt(key, v.coerceIn(0, options.lastIndex))?.apply() }
    }

    // ---------- Color ----------
    /** 0 = automático, 1 = oscuro, 2 = claro */
    val colorMode = ChoiceSetting(
        "color", "Color de las pantallas", listOf("Automático", "Oscuro", "Claro"), 0,
        "Automático: oscuro si el Karoo está en modo noche o ya no hay luz donde estás.",
    )
    val lightMargin = IntSetting(
        "margen_luz", "Margen de luz", "min", 20, 0, 60, 5,
        "Pasa a oscuro estos minutos antes del atardecer y vuelve a claro estos minutos después del amanecer.",
    )

    // ---------- Entreno ----------
    /** 0 = 3 s, 1 = 5 s, 2 = 10 s */
    val workoutPower = ChoiceSetting(
        "entreno_potencia", "Potencia del entreno", listOf("3 s", "5 s", "10 s"), 0,
        "Media que se compara con el objetivo. En exterior, 5 o 10 s bailan menos.",
    )
    val workoutTolerance = IntSetting(
        "entreno_tolerancia", "Tolerancia del objetivo", "%", 2, 0, 10, 1,
        "Margen fuera del objetivo antes de ponerse azul (corto) o rojo (pasado).",
    )
    val stepWarn = IntSetting(
        "entreno_aviso", "Aviso de fin de paso", "s", 10, 0, 30, 5,
        "Segundos finales del paso en naranja. 0 = sin aviso.",
    )
    val stepBeep = BoolSetting("entreno_pitido", "Pitido al acabar el paso", true, "Pita al empezar el aviso de fin de paso.")

    // ---------- Comer y beber ----------
    val fuelOn = BoolSetting(
        "comer", "Recordatorio de comer", true,
        "Calcula los hidratos que tocan según tu esfuerzo (potencia frente a FTP) y avisa al completar una toma.",
    )
    val fuelPortion = IntSetting("comer_toma", "Hidratos por toma", "g", 25, 10, 60, 5, "Lo que llevas en cada gel o barrita.")
    val fuelMax = IntSetting(
        "comer_max", "Máximo por hora", "g/h", 90, 30, 120, 10,
        "Lo que tu estómago tolera. Por encima de 60 g/h hace falta haberlo entrenado.",
    )
    val fuelStart = IntSetting("comer_inicio", "Primer aviso a partir de", "min", 30, 0, 90, 5)
    val drinkOn = BoolSetting("beber", "Recordatorio de beber", true, "Avisa más a menudo con más esfuerzo o más calor.")
    val drinkEvery = IntSetting(
        "beber_cada", "Beber cada (suave y fresco)", "min", 20, 10, 40, 5,
        "Intervalo base; con esfuerzo alto o calor se acorta, nunca por debajo de 8 min.",
    )

    // ---------- Tiempo ----------
    val weatherOn = BoolSetting(
        "tiempo", "Previsión del tiempo", true,
        "Viento, temperatura y lluvia de Open-Meteo para tu posición, cada 15 min o 5 km. Usa el wifi o el móvil (Companion).",
    )
    val windMap = BoolSetting(
        "tiempo_mapa", "Viento en el mapa", true,
        "Colorea la ruta cargada: rojo con viento de cara, ámbar lateral y verde a favor. Solo con viento de 8 km/h o más.",
    )
    val rainAlert = BoolSetting("tiempo_lluvia", "Aviso de lluvia", true, "Avisa si la probabilidad de lluvia en la próxima hora pasa del 60 %.")

    // ---------- Bici (para estimar tu paso en la pantalla 8 · Ruta) ----------
    /** 0 = carretera, 1 = MTB */
    val bikeType = ChoiceSetting(
        "bici_tipo", "Tipo de bici", listOf("Carretera", "MTB"), 0,
        "Cambia el rozamiento y el aire del cálculo de velocidad de la pantalla 8.",
    )
    val bikeKg = IntSetting("bici_kg", "Peso de la bici y bolsas", "kg", 10, 6, 25, 1, "Se suma a tu peso del perfil del Karoo.")

    // ---------- Pulso ----------
    val driftMinutes = IntSetting("deriva_min", "Deriva a partir de", "min", 20, 10, 60, 5, "Minutos grabados antes de mostrar la deriva pulso/potencia.")

    /** Grupos tal y como aparecen en la pantalla de ajustes. */
    val groups: List<Pair<String, List<Any>>> = listOf(
        "COLOR" to listOf(colorMode, lightMargin),
        "ENTRENO" to listOf(workoutPower, workoutTolerance, stepWarn, stepBeep),
        "COMER Y BEBER" to listOf(fuelOn, fuelPortion, fuelMax, fuelStart, drinkOn, drinkEvery),
        "TIEMPO" to listOf(weatherOn, windMap, rainAlert),
        "BICI" to listOf(bikeType, bikeKg),
        "PULSO" to listOf(driftMinutes),
    )
}
