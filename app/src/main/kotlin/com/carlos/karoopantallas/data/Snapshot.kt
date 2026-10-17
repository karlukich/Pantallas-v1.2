package com.carlos.karoopantallas.data

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Foto de todos los datos en un instante; cada pantalla se dibuja a partir de ella. */
class Snapshot(
    private val points: Map<String, DataPoint>,
    val profile: UserProfile?,
    val rideState: RideState?,
    val navigation: NavigationState?,
    val tracker: TrackerState = TrackerState.EMPTY,
) {
    /**
     * Valor de un tipo de dato. Si se indica campo se usa ese (o el único valor, si solo hay uno);
     * si no, el primero disponible.
     */
    fun v(type: String, field: String? = null): Double? {
        val point = points[type] ?: return null
        if (field == null) return point.singleValue
        return point.values[field] ?: if (point.values.size == 1) point.singleValue else null
    }

    fun has(type: String) = points.containsKey(type)

    private val metric: Boolean
        get() = profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL
    private val metricElevation: Boolean
        get() = profile?.preferredUnit?.elevation != UserProfile.PreferredUnit.UnitType.IMPERIAL

    // ---------- Unidades ----------
    val speedUnit get() = if (metric) "km/h" else "mph"
    val distUnit get() = if (metric) "km" else "mi"
    val elevUnit get() = if (metricElevation) "m" else "ft"

    /** m/s -> km/h o mph */
    fun speed(ms: Double?) = ms?.let { if (metric) it * 3.6 else it * 2.236936 }

    /** m -> km o mi */
    fun dist(m: Double?) = m?.let { if (metric) it / 1000.0 else it / 1609.344 }

    /** m -> m o ft */
    fun elev(m: Double?) = m?.let { if (metricElevation) it else it * 3.28084 }

    // ---------- Zonas ----------
    /** Índice de zona (0 = Z1) según las zonas de potencia del perfil del Karoo. */
    fun powerZone(watts: Double?): Int? = zoneOf(watts, profile?.powerZones)
    fun hrZone(bpm: Double?): Int? = zoneOf(bpm, profile?.heartRateZones)
    val powerZoneCount get() = profile?.powerZones?.size?.takeIf { it > 0 } ?: 7
    val hrZoneCount get() = profile?.heartRateZones?.size?.takeIf { it > 0 } ?: 5

    private fun zoneOf(value: Double?, zones: List<UserProfile.Zone>?): Int? {
        if (value == null || zones.isNullOrEmpty()) return null
        val idx = zones.indexOfFirst { value >= it.min && value <= it.max }
        return when {
            idx >= 0 -> idx
            value > zones.last().max -> zones.lastIndex
            else -> 0
        }
    }

    fun powerZoneRange(i: Int?): UserProfile.Zone? = i?.let { profile?.powerZones?.getOrNull(it) }
    fun hrZoneRange(i: Int?): UserProfile.Zone? = i?.let { profile?.heartRateZones?.getOrNull(it) }

    /** Cuánto se ha avanzado dentro de la zona actual (0 = límite inferior, 1 = superior). */
    fun fractionInZone(value: Double?, zone: UserProfile.Zone?): Double? {
        if (value == null || zone == null || zone.max <= zone.min) return null
        return ((value - zone.min) / (zone.max - zone.min)).coerceIn(0.0, 1.0)
    }

    val isRecording get() = rideState is RideState.Recording
    val isPaused get() = rideState is RideState.Paused
}

/** Formato en español: coma decimal y "--" si no hay dato. */
object Fmt {
    private val es = Locale("es", "ES")
    const val NONE = "--"

    fun int(x: Double?) = x?.roundToInt()?.toString() ?: NONE
    fun dec1(x: Double?) = x?.let { String.format(es, "%.1f", it) } ?: NONE
    fun dec2(x: Double?) = x?.let { String.format(es, "%.2f", it) } ?: NONE

    /** Número con 1 decimal si es pequeño, entero si es grande (para distancias). */
    fun dist(x: Double?) = x?.let { if (abs(it) < 100) dec1(it) else int(it) } ?: NONE

    /** Milisegundos a h:mm:ss o m:ss */
    fun duration(ms: Double?): String {
        if (ms == null || ms < 0) return NONE
        val total = (ms / 1000).toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(es, "%d:%02d:%02d", h, m, s) else String.format(es, "%d:%02d", m, s)
    }

    /** Entero con separador de miles (1.028). */
    fun grouped(x: Double?) = x?.let { String.format(Locale.US, "%,d", it.roundToInt()).replace(',', '.') } ?: NONE

    /** Segundos a h:mm:ss o m:ss */
    fun seconds(s: Long?) = duration(s?.times(1000.0))

    fun clock(epochMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HH:mm", es).format(Date(epochMs))
}
