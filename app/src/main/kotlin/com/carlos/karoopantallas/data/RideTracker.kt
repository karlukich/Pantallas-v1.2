package com.carlos.karoopantallas.data

import io.hammerhead.karooext.KarooSystemService
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

/** Datos que el Karoo no da hechos y calculamos nosotros durante la salida. */
data class TrackerState(
    /** Milisegundos en cada zona de pulso (índice 0 = Z1) */
    val hrZoneMs: List<Long>,
    /** Tiempo seguido en la zona de pulso actual */
    val currentZoneMs: Long?,
    /** Tiempo total en Z4 o superior */
    val z4PlusMs: Long,
    /** Deriva pulso/potencia en % (positiva = el pulso sube para la misma potencia) */
    val drift: Double?,
) {
    companion object {
        val EMPTY = TrackerState(emptyList(), null, 0L, null)
    }
}

/**
 * Cuenta, segundo a segundo mientras se graba la salida, el tiempo en cada zona de pulso
 * y guarda potencia y pulso para calcular la deriva (Pw:HR, primera mitad frente a la segunda).
 * Se pone a cero al empezar una salida nueva.
 */
object RideTracker {
    private const val MIN_DRIFT_SECONDS = 20 * 60

    private var job: Job? = null
    private val lock = Any()

    @Volatile private var hr: Double? = null
    @Volatile private var power: Double? = null
    @Volatile private var rideState: RideState = RideState.Idle
    @Volatile private var profile: UserProfile? = null

    private var zoneMs = LongArray(7)
    private var lastZone: Int? = null
    private var streakMs = 0L
    private var hrSamples = FloatArray(4 * 3600)
    private var powerSamples = FloatArray(4 * 3600)
    private var samples = 0

    fun start(karoo: KarooSystemService) {
        if (job != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job = scope.launch {
            launch { karoo.streamDataFlow(Type.HEART_RATE).collect { hr = (it as? StreamState.Streaming)?.dataPoint?.singleValue } }
            launch { karoo.streamDataFlow(Type.POWER).collect { power = (it as? StreamState.Streaming)?.dataPoint?.singleValue } }
            launch { karoo.consumerFlow<UserProfile>().collect { profile = it } }
            launch {
                karoo.consumerFlow<RideState>().collect { state ->
                    if (rideState is RideState.Idle && state is RideState.Recording) reset()
                    rideState = state
                }
            }
            while (isActive) {
                delay(1000)
                if (rideState is RideState.Recording) tick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun reset() {
        synchronized(lock) {
            zoneMs = LongArray(7)
            lastZone = null
            streakMs = 0L
            samples = 0
        }
    }

    private fun tick() {
        synchronized(lock) { tickLocked() }
    }

    private fun tickLocked() {
        val bpm = hr?.takeIf { it > 0 } ?: return
        val zones = profile?.heartRateZones.orEmpty()
        if (zones.isNotEmpty()) {
            val z = zones.indexOfFirst { bpm >= it.min && bpm <= it.max }.let {
                when {
                    it >= 0 -> it
                    bpm > zones.last().max -> zones.lastIndex
                    else -> 0
                }
            }.coerceAtMost(zoneMs.lastIndex)
            zoneMs[z] += 1000
            streakMs = if (z == lastZone) streakMs + 1000 else 1000
            lastZone = z
        }
        if (samples == hrSamples.size) {
            hrSamples = hrSamples.copyOf(samples * 2)
            powerSamples = powerSamples.copyOf(samples * 2)
        }
        hrSamples[samples] = bpm.toFloat()
        powerSamples[samples] = (power ?: 0.0).toFloat()
        samples++
    }

    fun state(): TrackerState = synchronized(lock) {
        val count = profile?.heartRateZones?.size?.takeIf { it > 0 } ?: 5
        val ms = zoneMs.take(count.coerceAtMost(zoneMs.size))
        TrackerState(
            hrZoneMs = ms,
            currentZoneMs = if (lastZone != null) streakMs else null,
            z4PlusMs = ms.drop(3).sum(),
            drift = drift(),
        )
    }

    private fun drift(): Double? {
        if (samples < MIN_DRIFT_SECONDS) return null
        val half = samples / 2
        fun ef(from: Int, to: Int): Double? {
            var p = 0.0
            var h = 0.0
            for (i in from until to) { p += powerSamples[i]; h += hrSamples[i] }
            return if (h > 0 && p > 0) p / h else null
        }
        val first = ef(0, half) ?: return null
        val second = ef(half, samples) ?: return null
        return (first - second) / first * 100.0
    }
}
