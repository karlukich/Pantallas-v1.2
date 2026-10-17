package com.carlos.karoopantallas

import android.content.Context
import android.widget.RemoteViews
import com.carlos.karoopantallas.data.RideTracker
import com.carlos.karoopantallas.data.Snapshot
import com.carlos.karoopantallas.data.consumerFlow
import com.carlos.karoopantallas.data.dataPoints
import com.carlos.karoopantallas.data.streamDataFlow
import com.carlos.karoopantallas.render.Layout
import com.carlos.karoopantallas.render.Painter
import com.carlos.karoopantallas.render.Palette
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DataType.Type
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Un campo gráfico que ocupa una página entera. [layout] decide qué datos pide y cómo se dibuja;
 * [palette] elige el modo oscuro (B) o claro (C). Se redibuja una vez por segundo,
 * que es el máximo que admite el Karoo para las vistas de extensiones.
 */
@OptIn(FlowPreview::class)
class PageScreen(
    private val karoo: KarooSystemService,
    extension: String,
    typeId: String,
    private val palette: Palette,
    private val layout: Layout,
) : DataTypeImpl(extension, typeId) {

    /** Un campo gráfico también necesita un valor numérico; emitimos el tiempo para que figure como activo. */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to 0.0))))
            karoo.streamDataFlow(Type.ELAPSED_TIME).collect { state ->
                val v = (state as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v))))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Ocupamos todo el campo, sin la cabecera estándar del Karoo
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val painter = Painter(context)
        val (w, h) = config.viewSize

        val job = CoroutineScope(Dispatchers.IO).launch {
            val profile: Flow<UserProfile?> = karoo.consumerFlow<UserProfile>()
                .map<UserProfile, UserProfile?> { it }
                .onStart { emit(null) }
            val points = karoo.dataPoints(layout.types).onStart { emit(emptyMap()) }
            val navigation: Flow<OnNavigationState.NavigationState?> =
                if (layout.navigation) {
                    karoo.consumerFlow<OnNavigationState>()
                        .map<OnNavigationState, OnNavigationState.NavigationState?> { it.state }
                        .onStart { emit(null) }
                } else {
                    flowOf(null)
                }
            val ride: Flow<RideState?> = karoo.consumerFlow<RideState>()
                .map<RideState, RideState?> { it }
                .onStart { emit(null) }

            combine(points, profile, navigation, ride) { p, prof, nav, rs ->
                Snapshot(p, prof, rs, nav, RideTracker.state())
            }
                .sample(1000)
                .collect { snap ->
                    try {
                        val bmp = painter.begin(w, h, palette.bg)
                        layout.draw(painter, snap, palette)
                        val views = RemoteViews(context.packageName, R.layout.pantalla)
                        views.setImageViewBitmap(R.id.imagen, bmp)
                        emitter.updateView(views)
                    } catch (e: Exception) {
                        Timber.e(e, "Error dibujando $typeId")
                    }
                }
        }
        emitter.setCancellable { job.cancel() }
    }
}
