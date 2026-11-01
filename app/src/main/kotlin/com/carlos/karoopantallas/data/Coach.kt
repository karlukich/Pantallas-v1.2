package com.carlos.karoopantallas.data

import com.carlos.karoopantallas.R
import com.carlos.karoopantallas.render.EntrenoLayout
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Avisos que funcionan en segundo plano mientras se graba, aunque no haya ninguna pantalla
 * de la app a la vista:
 *  - Pitido al acabar cada paso del entreno estructurado.
 *  - Recordatorio de comer según el esfuerzo: [Fuel].
 *  - Recordatorio de beber según esfuerzo y temperatura: [Fuel].
 */
object Coach {
    private var job: Job? = null

    @Volatile private var power: Double? = null
    @Volatile private var temp: Double? = null
    @Volatile private var stepRemaining: Double? = null
    @Volatile private var totalRemaining: Double? = null
    @Volatile private var profile: UserProfile? = null
    @Volatile private var rideState: RideState = RideState.Idle

    private var lastStepLeft: Double? = null
    val fuel = Fuel()

    private fun StreamState.value(field: String? = null): Double? {
        val dp = (this as? StreamState.Streaming)?.dataPoint ?: return null
        return if (field == null) dp.singleValue else dp.values[field] ?: dp.singleValue
    }

    fun start(karoo: KarooSystemService) {
        if (job != null) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            launch { karoo.streamDataFlow(Type.POWER).collect { power = it.value() } }
            launch { karoo.streamDataFlow(Type.TEMPERATURE).collect { temp = it.value() } }
            launch {
                karoo.streamDataFlow(Type.WORKOUT_REMAINING_INTERVAL_DURATION)
                    .collect { stepRemaining = it.value(Field.WORKOUT_TIME_TO_STEP_FINISH) }
            }
            launch {
                karoo.streamDataFlow(Type.WORKOUT_REMAINING_TOTAL_DURATION)
                    .collect { totalRemaining = it.value(Field.WORKOUT_REMAINING_TIME) }
            }
            launch { karoo.consumerFlow<UserProfile>().collect { profile = it } }
            launch {
                karoo.consumerFlow<RideState>().collect { state ->
                    if (rideState is RideState.Idle && state is RideState.Recording) {
                        fuel.reset()
                        lastStepLeft = null
                    }
                    rideState = state
                }
            }
            while (isActive) {
                delay(1000)
                if (rideState !is RideState.Recording) continue
                try {
                    stepBeep(karoo)
                    val ftp = profile?.ftp?.takeIf { it > 0 }?.toDouble() ?: 250.0
                    fuel.tick(power ?: 0.0, ftp, temp)?.let { karoo.dispatch(toAlert(it)); beep(karoo, 2) }
                } catch (e: Exception) {
                    Timber.e(e, "Error en los avisos")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Pita al entrar en el aviso de fin de paso (o al cambiar de paso si el aviso está a 0 s). */
    private fun stepBeep(karoo: KarooSystemService) {
        val left = EntrenoLayout.stepLeftMs(stepRemaining, totalRemaining)
        val prev = lastStepLeft
        lastStepLeft = left
        if (!Settings.stepBeep.value || left == null || prev == null) return
        val warnMs = Settings.stepWarn.value * 1000.0
        val fire = if (warnMs > 0) prev > warnMs && left in 0.0..warnMs else left > prev + 2000
        if (fire) beep(karoo, 3)
    }

    private fun beep(karoo: KarooSystemService, times: Int) {
        val tones = (1..times).flatMap {
            listOf(PlayBeepPattern.Tone(3000, 150), PlayBeepPattern.Tone(null, 120))
        }
        karoo.dispatch(PlayBeepPattern(tones))
    }

    private fun toAlert(r: Fuel.Reminder): InRideAlert = when (r) {
        is Fuel.Reminder.Eat -> InRideAlert(
            id = "comer",
            icon = R.drawable.ic_comer,
            title = "Come ~${r.grams} g de hidratos",
            detail = "Ritmo ${r.ratePerHour} g/h por tu esfuerzo · llevas ${r.eaten} g · gastados ~${r.burned} g",
            autoDismissMs = 20_000L,
            backgroundColor = R.color.aviso_comer_fondo,
            textColor = R.color.aviso_texto,
        )
        is Fuel.Reminder.Drink -> InRideAlert(
            id = "beber",
            icon = R.drawable.ic_beber,
            title = "Bebe",
            detail = "Siguiente en ~${r.nextMin} min" + (r.temp?.let { " · ${it.roundToInt()} °C" } ?: ""),
            autoDismissMs = 12_000L,
            backgroundColor = R.color.aviso_beber_fondo,
            textColor = R.color.aviso_texto,
        )
    }
}

/**
 * Cálculo de comer y beber, sin dependencias del Karoo para poder probarlo aparte.
 *
 * Comer: el ritmo objetivo de hidratos sube con la intensidad (potencia media de los últimos
 * 10 min frente al FTP): 30 g/h en suave (< 50 % FTP), 60 g/h hacia el 75 % y 90 g/h desde el 90 %,
 * con el tope del ajuste «Máximo por hora». Se acumula lo que «debes» cada segundo y se avisa al
 * llegar a una toma completa, nunca antes del ajuste «Primer aviso a partir de».
 * Los gramos gastados son una estimación: kJ de trabajo ≈ kcal gastadas (eficiencia ~24 %) y la
 * parte que sale de hidratos sube con la intensidad (~30 % en suave, ~95 % por encima del FTP).
 *
 * Beber: intervalo base del ajuste, acortado hasta un 50 % con esfuerzo alto y un 60 % más con
 * calor (desde 20 °C); nunca menos de 8 min.
 */
class Fuel {
    sealed class Reminder {
        data class Eat(val grams: Int, val ratePerHour: Int, val eaten: Int, val burned: Int) : Reminder()
        data class Drink(val nextMin: Int, val temp: Double?) : Reminder()
    }

    private val window = DoubleArray(600) // potencia de los últimos 10 min
    private var idx = 0
    private var sum = 0.0
    private var seconds = 0
    private var owed = 0.0
    private var eaten = 0
    private var burned = 0.0
    private var drinkProgress = 0.0

    fun reset() {
        window.fill(0.0); idx = 0; sum = 0.0; seconds = 0
        owed = 0.0; eaten = 0; burned = 0.0; drinkProgress = 0.0
    }

    /** Intensidad relativa (potencia media 10 min / FTP). */
    fun intensity(ftp: Double): Double = if (seconds == 0) 0.0 else sum / minOf(seconds, window.size) / ftp

    /** Gramos de hidratos por hora que tocan a esta intensidad, sin pasar del máximo. */
    fun carbRate(rel: Double, max: Int): Double {
        val r = when {
            rel < 0.5 -> 30.0
            rel < 0.75 -> 30.0 + (rel - 0.5) / 0.25 * 30.0
            rel < 0.9 -> 60.0 + (rel - 0.75) / 0.15 * 30.0
            else -> 90.0
        }
        return minOf(r, max.toDouble())
    }

    /** Parte de la energía que sale de hidratos según la intensidad. */
    fun carbFraction(rel: Double) = (0.3 + (rel - 0.45) * 1.2).coerceIn(0.3, 0.95)

    fun drinkInterval(rel: Double, temp: Double?): Double {
        val effort = 1 + ((rel - 0.6).coerceAtLeast(0.0) * 1.25).coerceAtMost(0.5)
        val heat = 1 + (((temp ?: 15.0).takeIf { it < 60 } ?: 15.0) - 20).coerceIn(0.0, 15.0) * 0.04
        return (Settings.drinkEvery.value / (effort * heat)).coerceAtLeast(8.0)
    }

    /** Un segundo grabando. Devuelve un aviso si toca. */
    fun tick(watts: Double, ftp: Double, temp: Double?): Reminder? {
        val w = watts.coerceIn(0.0, 2500.0)
        sum += w - window[idx]
        window[idx] = w
        idx = (idx + 1) % window.size
        seconds++

        val rel = intensity(ftp)
        // kJ de trabajo de este segundo ≈ kcal gastadas; 4 kcal por gramo de hidratos
        burned += w / 1000.0 * carbFraction(w / ftp) / 4.0

        if (Settings.drinkOn.value) {
            val interval = drinkInterval(rel, temp)
            drinkProgress += 1.0 / (interval * 60.0)
            if (drinkProgress >= 1.0) {
                drinkProgress = 0.0
                return Reminder.Drink(interval.roundToInt(), temp)
            }
        }

        if (!Settings.fuelOn.value) return null
        val rate = carbRate(rel, Settings.fuelMax.value)
        owed += rate / 3600.0
        val portion = Settings.fuelPortion.value
        if (seconds >= Settings.fuelStart.value * 60 && owed >= portion) {
            owed -= portion
            eaten += portion
            return Reminder.Eat(portion, rate.roundToInt(), eaten, burned.roundToInt())
        }
        return null
    }
}
