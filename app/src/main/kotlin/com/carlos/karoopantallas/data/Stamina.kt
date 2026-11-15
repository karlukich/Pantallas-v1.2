package com.carlos.karoopantallas.data

import android.content.Context
import android.content.SharedPreferences
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Recomendación de la pantalla de stamina, por orden de prioridad. */
enum class AdviceKind { AFLOJA, NO_LLEGAS, RECUPERA, COME, MARGEN, SOSTENIBLE }

data class Advice(val kind: AdviceKind, val text: String)

data class StaminaState(
    /** % de stamina potencial (fondo: solo baja) */
    val potential: Double?,
    /** % de stamina actual (nunca por encima de la potencial) */
    val current: Double?,
    /** W' que queda, en julios */
    val wBalJ: Double?,
    val wPrimeJ: Double,
    val ftp: Double?,
    /** Tiempo (s) y distancia (m) que quedan al ritmo de los últimos 5 min; null si es un ritmo que aguantas de sobra */
    val remainingS: Double?,
    val remainingM: Double?,
    /** Potencia media de 5 min respecto al FTP, en % */
    val pctFtp5: Double?,
    /** Un punto por minuto: potencial y actual */
    val history: List<Pair<Float, Float>>,
    val elapsedS: Int,
    val advice: Advice?,
) {
    companion object {
        val EMPTY = StaminaState(null, null, null, 20_000.0, null, null, null, null, emptyList(), 0, null)
    }
}

/**
 * Stamina al estilo de Garmin, calculada en el Karoo segundo a segundo mientras se graba.
 * No es el algoritmo de Garmin (es de Firstbeat y no es público); es un equivalente con modelos conocidos:
 *
 *  - **Actual**: W' balance (modelo de Skiba, forma diferencial de Froncioni-Clarke) con el FTP del perfil
 *    del Karoo como potencia crítica y el W' de los ajustes. Por encima del FTP se gasta W' 1 J por julio;
 *    por debajo se recupera tanto más rápido cuanto más suave vas. Se muestra como fracción de la potencial.
 *  - **Potencial**: depósito de fondo. Cada segundo gasta 1 / (tiempo hasta el agotamiento) a esa intensidad,
 *    con la curva TTE = 2 h × (P/FTP)^-4 (≈2 h al FTP, ≈3 h al 90 %, ≈6 h al 75 %, ≈11 h al 65 %). Solo baja.
 *    Lo que pasa por encima del FTP en minutos ya lo recoge el W' (la actual).
 *    Es una aproximación: da la tendencia, no un valor exacto (no sabe lo que comes ni cómo vienes de descansado).
 *  - **Te quedan**: potencial que queda × TTE a la potencia media de los últimos 5 min; en km, con tu velocidad
 *    media de esos 5 min.
 *
 * El estado se guarda en el Karoo cada 30 s, así que si se reinicia la extensión a mitad de salida
 * sigue donde iba en vez de volver al 100 %. Se pone a cero al empezar una salida nueva.
 */
object Stamina {
    private const val TTE_EXP = 4.0
    private const val TTE_AT_FTP = 7200.0
    private const val WINDOW = 300
    /** Ventana larga (30 min) para «no llegas»: un puerto o un tirón no deben dispararlo */
    private const val LONG = 1800
    private const val SAVE_EVERY = 30
    private const val ADVICE_HOLD = 30
    private const val PREFS = "stamina"

    private var job: Job? = null
    private var prefs: SharedPreferences? = null
    private val lock = Any()

    @Volatile private var power: Double? = null
    @Volatile private var speed: Double? = null
    @Volatile private var toDestination: Double? = null
    @Volatile private var profile: UserProfile? = null
    @Volatile private var rideState: RideState? = null

    // Estado de la salida
    private var used = 0.0          // fracción del depósito de fondo gastada (0..1)
    private var wBal: Double? = null
    private var seconds = 0
    private val history = ArrayList<Pair<Float, Float>>()
    private val pWin = DoubleArray(WINDOW)
    private val vWin = DoubleArray(WINDOW)
    private var winIdx = 0
    private var winN = 0
    private val pLong = DoubleArray(LONG)
    private val vLong = DoubleArray(LONG)
    private var longIdx = 0
    private var longN = 0
    private val p3 = DoubleArray(3)
    private var advice: Advice? = null
    private var adviceSince = 0

    @Volatile private var snapshot = StaminaState.EMPTY

    fun current(): StaminaState = snapshot

    fun start(karoo: KarooSystemService, context: Context) {
        if (job != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            launch { karoo.streamDataFlow(Type.POWER).collect { power = (it as? StreamState.Streaming)?.dataPoint?.singleValue } }
            launch { karoo.streamDataFlow(Type.SPEED).collect { speed = (it as? StreamState.Streaming)?.dataPoint?.singleValue } }
            launch {
                karoo.streamDataFlow(Type.DISTANCE_TO_DESTINATION).collect {
                    val dp = (it as? StreamState.Streaming)?.dataPoint
                    toDestination = dp?.values?.get(Field.DISTANCE_TO_DESTINATION) ?: dp?.singleValue
                }
            }
            launch { karoo.consumerFlow<UserProfile>().collect { profile = it } }
            launch {
                karoo.consumerFlow<RideState>().collect { state ->
                    val prev = rideState
                    rideState = state
                    when {
                        // Primer estado tras arrancar la extensión: si ya hay una salida en marcha, seguimos la guardada
                        prev == null -> if (state is RideState.Idle) Unit else restore()
                        prev is RideState.Idle && state is RideState.Recording -> reset()
                    }
                }
            }
            while (isActive) {
                delay(1000)
                try {
                    if (rideState is RideState.Recording) tick()
                    publish()
                } catch (e: Exception) {
                    Timber.e(e, "Error en stamina")
                }
            }
        }
    }

    fun stop() {
        save()
        job?.cancel()
        job = null
    }

    private fun ftp(): Double? = profile?.ftp?.takeIf { it > 0 }?.toDouble()
    private fun wPrime(): Double = Settings.wPrime.value * 1000.0

    /** Tiempo hasta el agotamiento (s) a una intensidad relativa al FTP. */
    fun tte(rel: Double): Double = TTE_AT_FTP * rel.coerceAtLeast(0.05).pow(-TTE_EXP)

    private fun reset() = synchronized(lock) {
        used = 0.0
        wBal = null
        seconds = 0
        history.clear()
        pWin.fill(0.0); vWin.fill(0.0); winIdx = 0; winN = 0
        pLong.fill(0.0); vLong.fill(0.0); longIdx = 0; longN = 0
        p3.fill(0.0)
        advice = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun tick() = synchronized(lock) {
        val cp = ftp() ?: return@synchronized
        val wp = wPrime()
        val p = (power ?: 0.0).coerceIn(0.0, 2500.0)
        val v = (speed ?: 0.0).coerceAtLeast(0.0)

        // W' balance (Froncioni-Clarke)
        var w = (wBal ?: wp).coerceAtMost(wp)
        w = if (p > cp) w - (p - cp) else w + (wp - w) * (cp - p) / wp
        wBal = w.coerceIn(0.0, wp)

        // Depósito de fondo
        used = (used + 1.0 / tte(p / cp)).coerceAtMost(1.0)

        pWin[winIdx] = p; vWin[winIdx] = v
        winIdx = (winIdx + 1) % WINDOW
        if (winN < WINDOW) winN++
        pLong[longIdx] = p; vLong[longIdx] = v
        longIdx = (longIdx + 1) % LONG
        if (longN < LONG) longN++
        p3[seconds % 3] = p

        seconds++
        if (seconds % 60 == 0) {
            val pot = potentialPct()
            history.add(pot.toFloat() to currentPct(pot, w, wp).toFloat())
        }
        if (seconds % SAVE_EVERY == 0) save()
    }

    private fun potentialPct() = (1 - used) * 100.0
    private fun currentPct(pot: Double, w: Double, wp: Double) = pot * (w / wp)

    private fun publish() {
        val st = synchronized(lock) { build() }
        snapshot = st
    }

    private fun build(): StaminaState {
        val cp = ftp()
        val wp = wPrime()
        if (cp == null) return StaminaState.EMPTY.copy(wPrimeJ = wp)
        val w = wBal ?: wp
        val pot = potentialPct()
        val cur = currentPct(pot, w, wp)
        val p5 = if (winN > 0) pWin.sum() / winN else null
        val v5 = if (winN > 0) vWin.sum() / winN else null
        val rel5 = p5?.let { it / cp }
        // Por debajo del 45 % FTP el depósito apenas baja: «te quedan» no tiene sentido
        val remS = if (rel5 != null && rel5 >= 0.45 && winN >= 60) pot / 100.0 * tte(rel5) else null
        val remM = if (remS != null && v5 != null && v5 > 1.0) remS * v5 else null
        val p3s = p3.average()
        // Ritmo de la última media hora para «no llegas»
        val pL = if (longN >= 600) pLong.sum() / longN else null
        val vL = if (longN >= 600) vLong.sum() / longN else null
        val remLongM = if (pL != null && vL != null && pL / cp >= 0.45) pot / 100.0 * tte(pL / cp) * vL else null
        val adv = pickAdvice(cp, wp, w, pot, cur, p3s, p5, remM, vL, remLongM)
        return StaminaState(pot, cur, w, wp, cp, remS, remM, rel5?.times(100), ArrayList(history), seconds, adv)
    }

    /** Elige la recomendación más importante y la mantiene al menos 30 s para que no parpadee. */
    private fun pickAdvice(
        cp: Double, wp: Double, w: Double, pot: Double, cur: Double,
        p3s: Double, p5: Double?, remM: Double?, vLong: Double?, remLongM: Double?,
    ): Advice? {
        if (rideState !is RideState.Recording || seconds < 60) return null
        val candidates = ArrayList<Advice>()

        // 1. Por encima del FTP con poca reserva
        if (p3s > cp * 1.02 && w / wp < 0.35) {
            val left = w / (p3s - cp)
            candidates += Advice(
                AdviceKind.AFLOJA,
                if (left < 10) "Reserva agotada. Baja de ${cp.roundToInt()} W"
                else "A ${p3s.roundToInt()} W tu reserva dura ${Fmt.seconds(left.toLong())}. Baja de ${cp.roundToInt()} W",
            )
        }
        // 2. Con ruta: ni al ritmo de la última media hora ni al de los últimos 5 min llegas a destino
        //    (y no durante una recuperación), para que cuadre con el «te quedan» de la pantalla
        val dest = toDestination
        val v5 = vLong
        if (dest != null && dest > 1000 && remLongM != null && v5 != null && v5 > 2.0 && remLongM < dest && (remM == null || remM < dest) && cur >= pot - 10) {
            val km = (dest / 1000).roundToInt()
            // Intensidad con la que el fondo que queda dura lo que tardas en llegar (a la velocidad de ahora: pesimista)
            val rel = if (pot >= 1) (pot / 100.0 * TTE_AT_FTP / (dest / v5)).pow(1.0 / TTE_EXP) else 0.0
            candidates += Advice(
                AdviceKind.NO_LLEGAS,
                if (rel >= 0.45) "Faltan $km km. A ${(rel * cp).roundToInt()} W de media sí llegas"
                else "Faltan $km km y vas muy justo: rueda suave y come",
            )
        }
        // 3. Recuperando: por debajo del FTP pero la actual muy por debajo de la potencial
        if (p3s <= cp && cur < pot - 20) {
            val target = 0.8
            val easy = 0.85 * cp
            val tau = wp / (cp - easy)
            val frac = w / wp
            val t = if (frac < target) tau * ln((1 - frac) / (1 - target)) else 0.0
            candidates += Advice(
                AdviceKind.RECUPERA,
                "${(t / 60).roundToInt().coerceAtLeast(1)} min por debajo de ${easy.roundToInt()} W y vuelves al ${(pot * target).roundToInt()} %",
            )
        }
        // 4. Toca comer (aviso de comer de los últimos 5 min)
        val sinceEat = Coach.fuel.secondsSinceEat()
        if (Settings.fuelOn.value && sinceEat != null && sinceEat in 0..300) {
            candidates += Advice(AdviceKind.COME, "Toca comer: ~${Settings.fuelPortion.value} g de hidratos para mantener la potencial")
        }
        // 5. Margen: reserva casi llena y fondo alto
        if (cur >= pot - 3 && pot >= 50 && (p5 ?: 0.0) > 0.3 * cp) {
            val p10 = cp + 0.7 * wp / 600.0
            candidates += Advice(AdviceKind.MARGEN, "Puedes ir a ${p10.roundToInt()} W unos 10 min")
        }

        val best = candidates.minByOrNull { it.kind.ordinal }
            ?: Advice(AdviceKind.SOSTENIBLE, "Ritmo que puedes mantener")
        val prev = advice
        val keep = prev != null && seconds - adviceSince < ADVICE_HOLD && best.kind.ordinal >= prev.kind.ordinal &&
            candidates.any { it.kind == prev.kind }
        return if (keep) {
            // Mismo aviso: se actualiza el texto con los datos del momento
            candidates.first { it.kind == prev!!.kind }.also { advice = it }
        } else {
            if (prev?.kind != best.kind) adviceSince = seconds
            advice = best
            best
        }
    }

    // ---------- Guardado ----------
    private fun save() = synchronized(lock) {
        val e = prefs?.edit() ?: return@synchronized
        e.putFloat("used", used.toFloat())
        e.putFloat("wbal", (wBal ?: -1.0).toFloat())
        e.putInt("seconds", seconds)
        e.putString("history", history.joinToString(";") { "${it.first},${it.second}" })
        e.putLong("saved", System.currentTimeMillis())
        e.apply()
    }

    private fun restore() = synchronized(lock) {
        val pr = prefs ?: return@synchronized
        val saved = pr.getLong("saved", 0L)
        if (saved == 0L || System.currentTimeMillis() - saved > 12 * 3600_000L) return@synchronized
        used = pr.getFloat("used", 0f).toDouble()
        wBal = pr.getFloat("wbal", -1f).toDouble().takeIf { it >= 0 }
        seconds = pr.getInt("seconds", 0)
        history.clear()
        pr.getString("history", "")?.split(";")?.forEach { s ->
            val parts = s.split(",")
            if (parts.size == 2) {
                val a = parts[0].toFloatOrNull()
                val b = parts[1].toFloatOrNull()
                if (a != null && b != null) history.add(a to b)
            }
        }
        Timber.i("Stamina: recuperada la salida en curso (${seconds / 60} min)")
    }
}
