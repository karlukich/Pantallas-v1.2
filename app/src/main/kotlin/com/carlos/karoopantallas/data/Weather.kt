package com.carlos.karoopantallas.data

import com.carlos.karoopantallas.R
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.PlayBeepPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Viento respecto a tu marcha. */
enum class WindSide(val label: String) { HEAD("DE CARA"), CROSS_L("LATERAL IZQ"), CROSS_R("LATERAL DCHA"), TAIL("A FAVOR") }

data class WeatherState(
    /** km/h a 10 m de altura (lo que da la previsión) */
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    /** Grados de donde VIENE el viento (0 = norte) */
    val windFrom: Double? = null,
    val tempC: Double? = null,
    /** mm en la última hora */
    val precipMm: Double? = null,
    /** % máximo de probabilidad de lluvia entre esta hora y la siguiente */
    val rainProb: Int? = null,
    /** Rumbo actual (0 = norte) */
    val heading: Double? = null,
    /** Hora local de la última actualización, HH:mm */
    val updated: String? = null,
) {
    val hasWind get() = windKmh != null && windFrom != null

    /** Ángulo del viento respecto a tu rumbo: 0 = de cara, 180 = a favor, 90 = desde la derecha. */
    val relativeFrom: Double?
        get() = if (windFrom != null && heading != null) ((windFrom - heading) % 360 + 360) % 360 else null

    /** Componente de cara (+) o a favor (−) en km/h. */
    val headKmh: Double? get() = relativeFrom?.let { r -> windKmh?.let { it * cos(Math.toRadians(r)) } }

    val side: WindSide?
        get() = relativeFrom?.let {
            when {
                it <= 45 || it >= 315 -> WindSide.HEAD
                it in 135.0..225.0 -> WindSide.TAIL
                it < 180 -> WindSide.CROSS_R
                else -> WindSide.CROSS_L
            }
        }

    /** Hacia dónde dibujar la flecha (hacia dónde sopla), relativo a tu marcha: 0 = hacia delante. */
    val arrowDeg: Double? get() = relativeFrom?.let { (it + 180) % 360 }

    companion object {
        val EMPTY = WeatherState()
    }
}

/**
 * Previsión de Open-Meteo (gratis y sin clave) para tu posición. La petición la hace el Karoo
 * por wifi o por el móvil (app Companion). Se actualiza cada 15 min o al moverte 5 km.
 * El rumbo sale del GPS (orientación del Karoo o, si no la da, de dos posiciones seguidas).
 * Si la probabilidad de lluvia de la próxima hora pasa del 60 %, avisa (como mucho una vez por hora).
 */
object Weather {
    private const val REFRESH_MS = 15 * 60_000L
    private const val REFRESH_KM = 5.0

    private var job: Job? = null
    @Volatile private var state = WeatherState.EMPTY
    @Volatile private var lat: Double? = null
    @Volatile private var lng: Double? = null
    private var prevLat: Double? = null
    private var prevLng: Double? = null
    private var lastFetchMs = 0L
    private var lastFetchLat = 0.0
    private var lastFetchLng = 0.0
    private var inFlight = false
    private var lastRainAlertMs = 0L
    private var lastRainProb: Int? = null

    fun current(): WeatherState = state

    fun start(karoo: KarooSystemService) {
        if (job != null) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            launch {
                karoo.consumerFlow<OnLocationChanged>().collect { loc ->
                    val heading = loc.orientation ?: bearingFromPrevious(loc.lat, loc.lng)
                    lat = loc.lat
                    lng = loc.lng
                    if (heading != null) state = state.copy(heading = heading)
                }
            }
            while (isActive) {
                if (Settings.weatherOn.value) maybeFetch(karoo)
                delay(30_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Rumbo a partir de la posición anterior, solo si te has movido al menos 15 m. */
    private fun bearingFromPrevious(la: Double, lo: Double): Double? {
        val pla = prevLat
        val plo = prevLng
        if (pla == null || plo == null || distanceKm(pla, plo, la, lo) > 0.015) {
            prevLat = la
            prevLng = lo
        }
        if (pla == null || plo == null || distanceKm(pla, plo, la, lo) < 0.015) return state.heading
        val p1 = Math.toRadians(pla)
        val p2 = Math.toRadians(la)
        val dl = Math.toRadians(lo - plo)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun maybeFetch(karoo: KarooSystemService) {
        val la = lat ?: return
        val lo = lng ?: return
        val now = System.currentTimeMillis()
        val moved = distanceKm(lastFetchLat, lastFetchLng, la, lo)
        if (inFlight || (now - lastFetchMs < REFRESH_MS && moved < REFRESH_KM)) return
        inFlight = true
        lastFetchMs = now
        lastFetchLat = la
        lastFetchLng = lo
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${"%.3f".format(java.util.Locale.US, la)}&longitude=${"%.3f".format(java.util.Locale.US, lo)}" +
            "&current=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=precipitation_probability&forecast_days=2&wind_speed_unit=kmh&timezone=auto"
        var id: String? = null
        id = karoo.addConsumer(
            OnHttpResponse.MakeHttpRequest("GET", url, waitForConnection = false),
            onError = { err ->
                Timber.w("Tiempo: error $err")
                inFlight = false
                id?.let { karoo.removeConsumer(it) }
            },
        ) { event: OnHttpResponse ->
            val st = event.state as? HttpResponseState.Complete ?: return@addConsumer
            inFlight = false
            id?.let { karoo.removeConsumer(it) }
            val body = st.body
            if (st.statusCode != 200 || body == null) {
                Timber.w("Tiempo: HTTP ${st.statusCode} ${st.error}")
                lastFetchMs = System.currentTimeMillis() - REFRESH_MS + 2 * 60_000 // reintento en 2 min
                return@addConsumer
            }
            try {
                state = parse(String(body), state.heading)
                rainAlert(karoo)
            } catch (e: Exception) {
                Timber.e(e, "Tiempo: respuesta no válida")
            }
        }
    }

    internal fun parse(json: String, heading: Double?): WeatherState {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        fun d(k: String) = if (cur.has(k) && !cur.isNull(k)) cur.getDouble(k) else null
        val time = cur.optString("time") // hora local, "2026-11-01T12:15"
        var prob: Int? = null
        o.optJSONObject("hourly")?.let { h ->
            val times = h.getJSONArray("time")
            val probs = h.getJSONArray("precipitation_probability")
            val hourKey = time.take(13) // "2026-11-01T12"
            for (i in 0 until times.length()) {
                if (times.getString(i).take(13) == hourKey) {
                    val a = probs.optInt(i, -1)
                    val b = if (i + 1 < probs.length()) probs.optInt(i + 1, -1) else -1
                    prob = maxOf(a, b).takeIf { it >= 0 }
                    break
                }
            }
        }
        return WeatherState(
            windKmh = d("wind_speed_10m"), gustKmh = d("wind_gusts_10m"), windFrom = d("wind_direction_10m"),
            tempC = d("temperature_2m"), precipMm = d("precipitation"), rainProb = prob,
            heading = heading, updated = time.takeLast(5).takeIf { it.contains(':') },
        )
    }

    private fun rainAlert(karoo: KarooSystemService) {
        val p = state.rainProb ?: return
        val prev = lastRainProb
        lastRainProb = p
        val now = System.currentTimeMillis()
        if (!Settings.rainAlert.value || p < 60 || (prev != null && prev >= 60) || now - lastRainAlertMs < 60 * 60_000) return
        lastRainAlertMs = now
        karoo.dispatch(
            InRideAlert(
                id = "lluvia", icon = R.drawable.ic_lluvia, title = "Lluvia probable",
                detail = "$p % en la próxima hora" + (state.tempC?.let { " · ${it.roundToInt()} °C" } ?: ""),
                autoDismissMs = 15_000L, backgroundColor = R.color.aviso_beber_fondo, textColor = R.color.aviso_texto,
            ),
        )
        karoo.dispatch(PlayBeepPattern(listOf(PlayBeepPattern.Tone(2000, 300))))
    }

    /** Distancia aproximada (equirectangular, sobra para unos km). Sin punto previo (0, 0) cuenta como lejos. */
    private fun distanceKm(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        if (abs(a1) < 1e-9 && abs(o1) < 1e-9) return Double.MAX_VALUE
        val dLat = Math.toRadians(a2 - a1)
        val dLon = Math.toRadians(o2 - o1) * cos(Math.toRadians((a1 + a2) / 2))
        return 6371.0 * sqrt(dLat * dLat + dLon * dLon)
    }
}
