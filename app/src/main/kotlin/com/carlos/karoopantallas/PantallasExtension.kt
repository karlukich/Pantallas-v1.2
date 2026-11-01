package com.carlos.karoopantallas

import com.carlos.karoopantallas.data.Coach
import com.carlos.karoopantallas.data.Polyline
import com.carlos.karoopantallas.data.WindLayer
import com.carlos.karoopantallas.data.consumerFlow
import com.carlos.karoopantallas.data.RideTracker
import com.carlos.karoopantallas.data.RouteForecast
import com.carlos.karoopantallas.data.Settings
import com.carlos.karoopantallas.data.Theme
import com.carlos.karoopantallas.data.Weather
import com.carlos.karoopantallas.render.ClimberLayout
import com.carlos.karoopantallas.render.TiempoLayout
import com.carlos.karoopantallas.render.RutaPrevistaLayout
import com.carlos.karoopantallas.render.VelocidadDistanciaCompactLayout
import com.carlos.karoopantallas.render.VientoCompactLayout
import com.carlos.karoopantallas.render.EntrenoLayout
import com.carlos.karoopantallas.render.PotenciaLayout
import com.carlos.karoopantallas.render.PulsoLayout
import com.carlos.karoopantallas.render.RutaLayout
import com.carlos.karoopantallas.render.VelocidadLayout
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Gregario Karoo. Servicio que el Karoo descubre gracias al intent-filter del AndroidManifest.
 * El id "pantallas", el paquete y los typeId se mantienen del nombre anterior para que se instale encima
 * y las páginas ya configuradas sigan funcionando.
 * Registra las seis pantallas como campos de datos gráficos; el color lo decide el ajuste global.
 * Los typeId conservan el sufijo "-oscura" de versiones anteriores para que las páginas que ya
 * usaban las pantallas B sigan funcionando sin volver a configurarlas.
 * El id "pantallas" y los typeId deben coincidir con res/xml/extension_info.xml.
 */
class PantallasExtension : KarooExtension("pantallas", "1.9") {

    private val karooSystem by lazy { KarooSystemService(applicationContext) }

    override val types by lazy {
        listOf(
            "ruta" to RutaLayout,
            "velocidad" to VelocidadLayout,
            "potencia" to PotenciaLayout,
            "pulso" to PulsoLayout,
            "subida" to ClimberLayout,
            "entreno" to EntrenoLayout,
            "tiempo" to TiempoLayout,
            "plan-ruta" to RutaPrevistaLayout,
        ).map { (key, layout) -> PageScreen(karooSystem, extension, "$key-oscura", layout) } + listOf(
            // Campos pequeños para la página del mapa
            PageScreen(karooSystem, extension, "viento", VientoCompactLayout),
            PageScreen(karooSystem, extension, "velocidad-distancia", VelocidadDistanciaCompactLayout),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Settings.init(applicationContext)
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        karooSystem.connect { connected ->
            Timber.i("Gregario Karoo conectado: $connected")
            if (connected) {
                RideTracker.start(karooSystem)
                Theme.start(karooSystem, applicationContext)
                Coach.start(karooSystem)
                Weather.start(karooSystem)
                RouteForecast.start(karooSystem)
            }
        }
    }

    /**
     * Capa de viento en el mapa: colorea la ruta cargada según te dará el viento previsto
     * (rojo de cara, ámbar lateral, verde a favor). Se recalcula al cambiar la ruta o la previsión
     * y se quita si no hay ruta, si el viento es flojo (< 8 km/h) o si se desactiva en ajustes.
     */
    override fun startMap(emitter: Emitter<MapEffect>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            var route: String? = null
            launch {
                karooSystem.consumerFlow<OnNavigationState>().collect { ev ->
                    route = (ev.state as? OnNavigationState.NavigationState.NavigatingRoute)?.routePolyline
                }
            }
            var shown = 0
            var lastKey = ""
            while (isActive) {
                val w = Weather.current()
                val r = route
                val on = Settings.windMap.value && r != null && w.windFrom != null && (w.windKmh ?: 0.0) >= 8.0
                // La dirección se redondea a 10° para no redibujar por cambios mínimos
                val key = if (on) "${r.hashCode()}|${((w.windFrom ?: 0.0) / 10).roundToInt()}" else "off"
                if (key != lastKey) {
                    lastKey = key
                    for (i in 0 until shown) emitter.onNext(HidePolyline("viento-$i"))
                    shown = 0
                    if (on) {
                        try {
                            val pts = Polyline.decode(r!!, 5)
                            WindLayer.runs(pts, w.windFrom!!).forEachIndexed { i, run ->
                                emitter.onNext(ShowPolyline("viento-$i", Polyline.encode(run.points), run.color, 8))
                                shown = i + 1
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Capa de viento")
                        }
                    }
                }
                delay(15_000)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun onDestroy() {
        RideTracker.stop()
        Theme.stop()
        Coach.stop()
        Weather.stop()
        RouteForecast.stop()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
