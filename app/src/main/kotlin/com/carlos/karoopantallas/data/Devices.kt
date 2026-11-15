package com.carlos.karoopantallas.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/** Batería de un sensor o de una pieza de un sensor (p. ej. la horquilla del Flight Attendant). */
data class DeviceBattery(
    /** Nombre corto para la pantalla: HORQUILLA, TIJA, CAMBIO… o el nombre del sensor */
    val label: String,
    /** Nombre del sensor tal y como lo guarda el Karoo */
    val device: String,
    val status: BatteryStatus?,
    /** Momento del último dato de batería (ms), si el Karoo lo da */
    val updatedMs: Long?,
    /** Orden en la pantalla: suspensión, tija, cambio, potenciómetro y el resto */
    val order: Int,
) {
    /** Nivel de 0 a 5 para la barra de 5 tramos */
    val level: Int
        get() = when (status) {
            BatteryStatus.NEW -> 5
            BatteryStatus.GOOD -> 4
            BatteryStatus.OK -> 3
            BatteryStatus.LOW -> 2
            BatteryStatus.CRITICAL -> 1
            else -> 0
        }
    val low: Boolean get() = level in 1..2
}

/**
 * Sensores emparejados en el Karoo (evento SavedDevices) con su último estado de batería.
 * El Karoo da la batería en 5 niveles (nueva, bien, media, baja, crítica), no en %.
 * Si un sensor tiene varias piezas con batería propia (componentes), sale una fila por pieza.
 */
object Devices {
    private var job: Job? = null
    @Volatile private var list: List<DeviceBattery> = emptyList()

    fun current(): List<DeviceBattery> = list

    fun start(karoo: KarooSystemService) {
        if (job != null) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            karoo.consumerFlow<SavedDevices>().collect { saved ->
                try {
                    list = build(saved)
                } catch (e: Exception) {
                    Timber.e(e, "Baterías: no se pudo leer la lista de sensores")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    internal fun build(saved: SavedDevices): List<DeviceBattery> {
        val out = ArrayList<DeviceBattery>()
        for (d in saved.devices) {
            if (!d.enabled) continue
            val comps = d.components.orEmpty().filterValues { it.lastBattery != null }
            if (comps.isNotEmpty()) {
                for ((key, c) in comps) {
                    // Primero por el nombre de la pieza (FORK, SHOCK…); si no dice nada, por el del sensor
                    val (label, order) = classify(key).takeIf { it.second < 9 } ?: classify(d.name).let { (l, o) -> if (o < 9) l to o else "${d.name} · $key".uppercase().take(18) to 9 }
                    out += DeviceBattery(label, d.name, c.lastBattery, c.lastBatteryUpdate, order)
                }
            } else if (d.details.lastBattery != null) {
                val (label, order) = classify(d.name)
                out += DeviceBattery(label, d.name, d.details.lastBattery, d.details.lastBatteryUpdate, order)
            }
        }
        return out.sortedWith(compareBy({ it.order }, { it.label }))
    }

    /** Pone nombre corto y orden según el nombre del sensor o de la pieza. */
    internal fun classify(name: String): Pair<String, Int> {
        val n = name.lowercase()
        return when {
            listOf("fork", "horquilla", "sid ", "sid-", "zeb", "pike", "lyrik", "front susp").any { it in n } &&
                "luxe" !in n && "shock" !in n -> "HORQUILLA" to 0
            listOf("shock", "luxe", "amortig", "rear susp", "super deluxe").any { it in n } -> "AMORTIGUADOR" to 1
            listOf("reverb", "dropper", "seatpost", "tija").any { it in n } -> "TIJA" to 2
            // Antes que el cambio: un potenciómetro SRAM puede llevar «Eagle» en el nombre
            listOf("power", "quarq", "dzero", "potenc").any { it in n } -> "POTENCIÓMETRO" to 4
            listOf("derailleur", "transmission", "eagle", "shift", "cambio").any { it in n } ||
                n.split(" ", "_", "-").any { it == "rd" || it == "fd" } -> "CAMBIO" to 3
            else -> name.uppercase().take(18) to 9
        }
    }
}
