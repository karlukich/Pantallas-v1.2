package com.carlos.karoopantallas.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/** Flujo de un tipo de dato del Karoo (velocidad, potencia...). */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val id = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        trySendBlocking(event.state)
    }
    awaitClose { removeConsumer(id) }
}

/** Flujo de un evento del sistema (perfil de usuario, estado de la ruta, navegación...). */
inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val id = addConsumer<T> { trySend(it) }
    awaitClose { removeConsumer(id) }
}

/**
 * Se suscribe a varios tipos de dato a la vez y emite un mapa tipo -> último valor.
 * Si un sensor deja de emitir, su entrada desaparece y la pantalla muestra "--".
 */
fun KarooSystemService.dataPoints(types: Collection<String>): Flow<Map<String, DataPoint>> =
    types.distinct()
        .map { type -> streamDataFlow(type).map { type to (it as? StreamState.Streaming)?.dataPoint } }
        .merge()
        .scan(emptyMap<String, DataPoint>()) { acc, (type, point) ->
            if (point == null) acc - type else acc + (type to point)
        }
