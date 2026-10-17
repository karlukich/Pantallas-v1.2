package com.carlos.karoopantallas

import com.carlos.karoopantallas.data.RideTracker
import com.carlos.karoopantallas.render.ClimberLayout
import com.carlos.karoopantallas.render.Palette
import com.carlos.karoopantallas.render.PotenciaLayout
import com.carlos.karoopantallas.render.PulsoLayout
import com.carlos.karoopantallas.render.RutaLayout
import com.carlos.karoopantallas.render.VelocidadLayout
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber

/**
 * Servicio que el Karoo descubre gracias al intent-filter del AndroidManifest.
 * Registra las pantallas 1B…5B (oscuras) y 1C…5C (claras) como campos de datos gráficos.
 * El id "pantallas" y los typeId deben coincidir con res/xml/extension_info.xml.
 */
class PantallasExtension : KarooExtension("pantallas", "1.2") {

    private val karooSystem by lazy { KarooSystemService(applicationContext) }

    override val types by lazy {
        listOf(
            "ruta" to RutaLayout,
            "velocidad" to VelocidadLayout,
            "potencia" to PotenciaLayout,
            "pulso" to PulsoLayout,
            "subida" to ClimberLayout,
        ).flatMap { (key, layout) ->
            listOf(
                PageScreen(karooSystem, extension, "$key-oscura", Palette.DARK, layout),
                PageScreen(karooSystem, extension, "$key-clara", Palette.LIGHT, layout),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        karooSystem.connect { connected ->
            Timber.i("Karoo Pantallas conectado: $connected")
            if (connected) RideTracker.start(karooSystem)
        }
    }

    override fun onDestroy() {
        RideTracker.stop()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
