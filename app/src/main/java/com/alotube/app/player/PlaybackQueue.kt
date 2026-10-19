package com.alotube.app.player

import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueState(
    val items: List<VideoItem> = emptyList(),
    val index: Int = -1
) {
    val current: VideoItem? get() = items.getOrNull(index)
    val hasNext: Boolean get() = index >= 0 && index < items.lastIndex
    val hasPrevious: Boolean get() = index > 0
}

/**
 * Cola = lista de resultados de la búsqueda desde la que se abrió el video.
 * Siguiente/Anterior (notificación, pantalla de bloqueo y botones del reproductor) solo mueven el
 * índice; PlayerViewModel observa [state] y carga el video que corresponda.
 */
object PlaybackQueue {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    fun set(items: List<VideoItem>, index: Int) {
        _state.value = QueueState(items, index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }

    fun next(): Boolean {
        val s = _state.value
        if (!s.hasNext) return false
        _state.value = s.copy(index = s.index + 1)
        return true
    }

    fun previous(): Boolean {
        val s = _state.value
        if (!s.hasPrevious) return false
        _state.value = s.copy(index = s.index - 1)
        return true
    }

    fun clear() {
        _state.value = QueueState()
    }
}
