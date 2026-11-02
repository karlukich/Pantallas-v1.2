package com.carlos.karoopantallas.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

/**
 * Pantalla 8 · Ruta: con una ruta cargada, pide a Open-Meteo la previsión de 8 puntos repartidos
 * por la ruta (una sola petición, cada 30 min o al cambiar de ruta) y cada 20 s recalcula tu paso
 * con [RoutePlan]: potencia (la media de la salida en curso tras 10 min, o el 65 % del FTP),
 * tu peso más el de la bici y la pendiente de cada tramo.
 */
object RouteForecast {
    private const val SAMPLES = 8
    private const val REFRESH_MS = 30 * 60_000L

    private var job: Job? = null
    @Volatile private var state = RouteState.EMPTY
    @Volatile private var nav: NavigationState.NavigatingRoute? = null
    @Volatile private var profile: UserProfile? = null
    @Volatile private var toDest: Double? = null
    @Volatile private var avgPower: Double? = null
    @Volatile private var elapsedMs: Double? = null
    @Volatile private var samples: List<ForecastSample> = emptyList()

    private var decodedFor: String? = null
    private var route: List<Pair<Double, Double>> = emptyList()
    private var elev: List<Pair<Double, Double>> = emptyList()
    private var fetchedFor: String? = null
    private var fetchedAt = 0L
    private var inFlight = false

    fun current(): RouteState = state

    private fun StreamState.v(field: String? = null): Double? {
        val dp = (this as? StreamState.Streaming)?.dataPoint ?: return null
        return if (field == null) dp.singleValue else dp.values[field] ?: dp.singleValue
    }

    fun start(karoo: KarooSystemService) {
        if (job != null) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            launch { karoo.consumerFlow<OnNavigationState>().collect { nav = it.state as? NavigationState.NavigatingRoute } }
            launch { karoo.consumerFlow<UserProfile>().collect { profile = it } }
            launch { karoo.streamDataFlow(Type.DISTANCE_TO_DESTINATION).collect { toDest = it.v(Field.DISTANCE_TO_DESTINATION) } }
            launch { karoo.streamDataFlow(Type.AVERAGE_POWER).collect { avgPower = it.v() } }
            launch { karoo.streamDataFlow(Type.ELAPSED_TIME).collect { elapsedMs = it.v() } }
            while (isActive) {
                try { tick(karoo) } catch (e: Exception) { Timber.e(e, "Ruta prevista") }
                delay(20_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun tick(karoo: KarooSystemService) {
        val n = nav
        if (n == null) {
            state = RouteState.EMPTY
            return
        }
        if (n.routePolyline != decodedFor) {
            route = Polyline.decode(n.routePolyline, 5)
            elev = n.routeElevationPolyline?.let { runCatching { Polyline.decode(it, 1) }.getOrDefault(emptyList()) } ?: emptyList()
            decodedFor = n.routePolyline
            samples = emptyList()
        }
        if (route.size < 2) return

        val now = System.currentTimeMillis()
        if (Settings.weatherOn.value && !inFlight && (fetchedFor != decodedFor || now - fetchedAt > REFRESH_MS)) fetch(karoo, n.routeDistance)

        val prof = profile
        val ftp = prof?.ftp?.takeIf { it > 0 }?.toDouble() ?: 250.0
        val weight = prof?.weight?.takeIf { it > 0 }?.toDouble() ?: 80.0
        val ridingLong = (elapsedMs ?: 0.0) > 10 * 60_000
        val power = avgPower?.takeIf { ridingLong && it > 0 }?.coerceAtLeast(ftp * 0.45) ?: ftp * 0.65
        val mtb = Settings.bikeType.value == 1
        val rider = RoutePlan.Rider(
            powerW = power, massKg = weight + Settings.bikeKg.value,
            cda = if (mtb) 0.40 else 0.32, crr = if (mtb) 0.012 else 0.005,
            maxKmh = if (mtb) 35.0 else 50.0, // en bajada se frena: tope realista
        )
        val progress = toDest?.let { n.routeDistance - it } ?: 0.0
        state = RoutePlan.compute(route, n.routeDistance, elev, progress, rider, now, samples)
    }

    /** Previsión de 8 puntos de la ruta en una sola petición (Open-Meteo admite varias coordenadas). */
    private fun fetch(karoo: KarooSystemService, routeDistance: Double) {
        val cum = RoutePlan.cumulative(route, routeDistance)
        val total = cum.last()
        val picks = (0 until SAMPLES).map { k ->
            val target = total * k / (SAMPLES - 1)
            val i = cum.indices.minByOrNull { kotlin.math.abs(cum[it] - target) } ?: 0
            Triple(cum[i], route[i].first, route[i].second)
        }
        val lats = picks.joinToString(",") { "%.3f".format(Locale.US, it.second) }
        val lngs = picks.joinToString(",") { "%.3f".format(Locale.US, it.third) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lngs" +
            "&hourly=temperature_2m,precipitation_probability,wind_speed_10m,wind_direction_10m,weather_code" +
            "&forecast_days=2&wind_speed_unit=kmh&timeformat=unixtime&timezone=GMT"
        val forRoute = decodedFor
        inFlight = true
        fetchedAt = System.currentTimeMillis()
        var id: String? = null
        id = karoo.addConsumer(
            OnHttpResponse.MakeHttpRequest("GET", url, waitForConnection = false),
            onError = { inFlight = false; id?.let { karoo.removeConsumer(it) } },
        ) { event: OnHttpResponse ->
            val st = event.state as? HttpResponseState.Complete ?: return@addConsumer
            inFlight = false
            id?.let { karoo.removeConsumer(it) }
            val body = st.body
            if (st.statusCode != 200 || body == null) {
                fetchedAt = System.currentTimeMillis() - REFRESH_MS + 2 * 60_000 // reintento en 2 min
                return@addConsumer
            }
            try {
                samples = parse(String(body), picks.map { it.first })
                fetchedFor = forRoute
            } catch (e: Exception) {
                Timber.e(e, "Ruta prevista: respuesta no válida")
            }
        }
    }

    /** Una ubicación devuelve un objeto; varias, una lista en el mismo orden que se pidieron. */
    internal fun parse(json: String, dists: List<Double>): List<ForecastSample> {
        val trimmed = json.trimStart()
        val items = if (trimmed.startsWith("[")) JSONArray(trimmed).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(trimmed))
        return items.mapIndexed { i, o ->
            val h = o.getJSONObject("hourly")
            val time = h.getJSONArray("time")
            fun d(k: String, j: Int) = h.optJSONArray(k)?.let { if (it.isNull(j)) null else it.optDouble(j) }
            val hours = HashMap<Long, ForecastSample.Hour>()
            for (j in 0 until time.length()) {
                hours[time.getLong(j)] = ForecastSample.Hour(
                    d("temperature_2m", j), d("precipitation_probability", j)?.toInt(),
                    d("wind_speed_10m", j), d("wind_direction_10m", j), d("weather_code", j)?.toInt(),
                )
            }
            ForecastSample(dists.getOrElse(i) { 0.0 }, hours)
        }
    }
}
