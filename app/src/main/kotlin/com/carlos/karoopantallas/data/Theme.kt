package com.carlos.karoopantallas.data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnLocationChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Decide si las pantallas van en oscuro o en claro según el ajuste «Color de las pantallas».
 * En automático:
 *  1. Si el Karoo indica modo noche (modo noche de Android), oscuro.
 *  2. Si no, según el sol: claro entre el amanecer y el atardecer de donde estés (GPS), recortado
 *     por el «Margen de luz»; oscuro el resto.
 *     Antes del primer fix se usa la última posición guardada y, si nunca la hubo, de 8:00 a 18:30.
 */
object Theme {
    private const val PREFS = "tema"
    private var job: Job? = null

    @Volatile private var lat: Double? = null
    @Volatile private var lng: Double? = null
    @Volatile private var lastSaveMs = 0L

    // Caché: el cálculo solar se rehace como mucho una vez por minuto
    @Volatile private var cacheMinute = -1L
    @Volatile private var cacheDay = true
    @Volatile private var cacheMargin = -1

    fun start(karoo: KarooSystemService, context: Context) {
        if (job != null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains("lat")) {
            lat = prefs.getFloat("lat", 0f).toDouble()
            lng = prefs.getFloat("lng", 0f).toDouble()
        }
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            karoo.consumerFlow<OnLocationChanged>().collect { loc ->
                lat = loc.lat
                lng = loc.lng
                cacheMinute = -1L
                // Guardamos la posición como mucho cada 10 minutos
                val now = System.currentTimeMillis()
                if (now - lastSaveMs > 10 * 60_000) {
                    lastSaveMs = now
                    prefs.edit().putFloat("lat", loc.lat.toFloat()).putFloat("lng", loc.lng.toFloat()).apply()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** true = usar la paleta oscura */
    fun isDark(context: Context): Boolean = when (Settings.colorMode.value) {
        1 -> true
        2 -> false
        else -> karooNight(context) || !isDaylight()
    }

    private fun karooNight(context: Context): Boolean {
        val cfg = context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (cfg == Configuration.UI_MODE_NIGHT_YES) return true
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.nightMode == UiModeManager.MODE_NIGHT_YES
    }

    private fun isDaylight(): Boolean {
        val nowMs = System.currentTimeMillis()
        val minute = nowMs / 60_000
        val margin = Settings.lightMargin.value
        if (minute == cacheMinute && margin == cacheMargin) return cacheDay
        val la = lat
        val lo = lng
        val day = if (la == null || lo == null) {
            val c = Calendar.getInstance()
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            m in (8 * 60)..(18 * 60 + 30)
        } else {
            sunIsUp(nowMs, la, lo, margin)
        }
        cacheDay = day
        cacheMinute = minute
        cacheMargin = margin
        return day
    }

    /**
     * Amanecer y atardecer con la aproximación de la NOAA (error de un par de minutos),
     * con el sol a −0,833° (borde del disco y refracción). Todo en minutos UTC.
     * [marginMin] acorta el día por los dos lados: claro desde amanecer + margen hasta atardecer − margen.
     */
    internal fun sunIsUp(nowMs: Long, lat: Double, lng: Double, marginMin: Int = 0): Boolean {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val n = utc.get(Calendar.DAY_OF_YEAR)
        val nowMin = utc.get(Calendar.HOUR_OF_DAY) * 60.0 + utc.get(Calendar.MINUTE) + utc.get(Calendar.SECOND) / 60.0

        val g = 2 * PI / 365.0 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val arg = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (arg > 1) return false // noche polar
        if (arg < -1) return true // sol de medianoche
        val ha = Math.toDegrees(acos(arg))
        val riseRaw = 720 - 4 * (lng + ha) - eqTime + marginMin
        val setRaw = 720 - 4 * (lng - ha) - eqTime - marginMin
        if (setRaw <= riseRaw) return false // con el margen no queda día
        val rise = norm(riseRaw)
        val set = norm(setRaw)
        return if (rise < set) nowMin in rise..set else nowMin >= rise || nowMin <= set
    }

    private fun norm(m: Double) = ((m % 1440) + 1440) % 1440
}
