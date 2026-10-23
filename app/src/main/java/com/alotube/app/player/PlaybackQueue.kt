package com.alotube.app.player

import androidx.media3.common.Player
import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de la cola.
 *
 * [shuffle] y [repeat] (Player.REPEAT_MODE_OFF / ALL / ONE) solo actúan cuando suena MÚSICA LOCAL;
 * con videos de YouTube la cola se comporta como siempre (se conservan guardados, pero no se aplican).
 *
 * Con barajeo, [order] es el orden de reproducción (índices de [items], el primero es la canción con la
 * que se activó) y [orderPos] la posición actual dentro de él. [index] siempre es la canción actual.
 */
data class QueueState(
    val items: List<VideoItem> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val order: List<Int> = emptyList(),
    val orderPos: Int = 0
) {
    val current: VideoItem? get() = items.getOrNull(index)

    /** ¿Barajeo realmente aplicado? (activado, música local y orden válido). */
    val shuffling: Boolean
        get() = shuffle && order.isNotEmpty() && order.size == items.size && isLocalAudioUrl(current?.url)

    private val repeatingAll: Boolean
        get() = repeat == Player.REPEAT_MODE_ALL && items.size > 1 && isLocalAudioUrl(current?.url)

    val hasNext: Boolean
        get() = when {
            index < 0 -> false
            shuffling -> orderPos < order.lastIndex || repeatingAll
            else -> index < items.lastIndex || repeatingAll
        }

    val hasPrevious: Boolean
        get() = if (shuffling) orderPos > 0 else index > 0
}

/**
 * Cola = lista de resultados de la búsqueda (o de canciones de la carpeta) desde la que se abrió el video.
 * Siguiente/Anterior (notificación, pantalla de bloqueo y botones del reproductor) solo mueven el
 * índice; PlayerViewModel observa [state] y carga el video que corresponda.
 *
 * Barajear y Repetir de la música local viven aquí (el ExoPlayer carga una canción a la vez, así que sus
 * propios shuffleModeEnabled / repeatMode no tienen una lista que recorrer). Como esta clase es un
 * objeto de la app, los modos se conservan al cambiar de canción y al cerrar/abrir la pantalla completa.
 */
object PlaybackQueue {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    fun set(items: List<VideoItem>, index: Int) {
        val cur = _state.value
        val idx = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        // Misma lista y misma canción (p. ej. abrir desde el mini-reproductor): no se toca el orden barajeado
        if (cur.items == items && cur.index == idx) return
        _state.value = QueueState(
            items = items,
            index = idx,
            shuffle = cur.shuffle,
            repeat = cur.repeat,
            order = if (cur.shuffle) newOrder(items.size, idx) else emptyList(),
            orderPos = 0
        )
    }

    fun next(): Boolean {
        val s = _state.value
        if (!s.hasNext) return false
        _state.value = if (s.shuffling) {
            if (s.orderPos < s.order.lastIndex) {
                s.copy(orderPos = s.orderPos + 1, index = s.order[s.orderPos + 1])
            } else {
                // Terminó el orden barajeado con "Repetir lista": nuevo orden, sin empezar por la misma canción
                var fresh = (0 until s.items.size).shuffled()
                if (fresh.first() == s.index && fresh.size > 1) fresh = fresh.drop(1) + fresh.first()
                s.copy(order = fresh, orderPos = 0, index = fresh.first())
            }
        } else {
            s.copy(index = if (s.index < s.items.lastIndex) s.index + 1 else 0)
        }
        return true
    }

    fun previous(): Boolean {
        val s = _state.value
        if (!s.hasPrevious) return false
        _state.value = if (s.shuffling) {
            s.copy(orderPos = s.orderPos - 1, index = s.order[s.orderPos - 1])
        } else {
            s.copy(index = s.index - 1)
        }
        return true
    }

    /** Activa o desactiva Barajear. Al activarlo, la canción actual queda primera y el resto en orden aleatorio. */
    fun setShuffle(enabled: Boolean) {
        val s = _state.value
        if (s.shuffle == enabled) return
        _state.value = if (enabled) {
            s.copy(shuffle = true, order = newOrder(s.items.size, s.index), orderPos = 0)
        } else {
            s.copy(shuffle = false, order = emptyList(), orderPos = 0)
        }
    }

    /** Fija el modo de repetición (Player.REPEAT_MODE_OFF / ALL / ONE). */
    fun setRepeat(mode: Int) {
        val s = _state.value
        if (s.repeat == mode) return
        _state.value = s.copy(repeat = mode)
    }

    /** Un solo botón, 3 estados: Inactivo -> Repetir lista -> Repetir canción -> Inactivo. Devuelve el nuevo modo. */
    fun cycleRepeat(): Int {
        val mode = when (_state.value.repeat) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeat(mode)
        return mode
    }

    fun clear() {
        _state.value = QueueState()
    }

    /** Orden aleatorio de [size] canciones con la actual ([current]) en primer lugar. */
    private fun newOrder(size: Int, current: Int): List<Int> {
        if (size <= 0 || current !in 0 until size) return emptyList()
        return listOf(current) + (0 until size).filter { it != current }.shuffled()
    }
}
