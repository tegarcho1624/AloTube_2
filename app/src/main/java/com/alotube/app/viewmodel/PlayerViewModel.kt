package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.data.repository.StreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

enum class PlayerError {
    NETWORK, BLOCKED, UNAVAILABLE, EXTRACTION, NO_STREAMS, PLAYBACK, UNKNOWN;

    companion object {
        fun from(t: Throwable): PlayerError = when (t) {
            is ReCaptchaException -> BLOCKED
            is ContentNotAvailableException -> UNAVAILABLE
            is IOException -> NETWORK
            is ExtractionException -> EXTRACTION
            else -> UNKNOWN
        }
    }
}

data class PlayerUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    /** Calidades del menú (de mayor a menor). */
    val options: List<PlaybackOption> = emptyList(),
    val fallback: PlaybackOption? = null,
    /** Lo que se está reproduciendo ahora. */
    val current: PlaybackOption? = null,
    val error: PlayerError? = null
)

class PlayerViewModel(
    private val repository: StreamRepository = StreamRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var videoUrl: String? = null
    private var fallbackTried = false
    private var job: Job? = null

    /** Idempotente: si ya se cargó esta URL, no hace nada. */
    fun load(url: String, title: String) {
        if (videoUrl == url) return
        videoUrl = url
        _state.value = PlayerUiState(title = title)
        fetch()
    }

    /** Vuelve a pedir los enlaces (sirve también cuando un enlace caducó). */
    fun retry() {
        _state.update { PlayerUiState(title = it.title) }
        fetch()
    }

    fun selectQuality(option: PlaybackOption) {
        fallbackTried = false
        _state.update { it.copy(current = option) }
    }

    /** El reproductor falló: se intenta una vez con el flujo compatible; si no, error en pantalla. */
    fun onPlaybackError() {
        val s = _state.value
        if (s.error != null) return
        val fb = s.fallback
        if (fb != null && s.current != fb && !fallbackTried) {
            fallbackTried = true
            _state.update { it.copy(current = fb) }
        } else {
            _state.update { it.copy(isLoading = false, error = PlayerError.PLAYBACK) }
        }
    }

    private fun fetch() {
        val url = videoUrl ?: return
        fallbackTried = false
        job?.cancel()
        job = viewModelScope.launch {
            repository.getPlayable(url).fold(
                onSuccess = { video ->
                    // Por defecto la mejor calidad hasta 1080p (4K/8K suelen ir en VP9/AV1, más pesados)
                    val initial = video.options.firstOrNull { it.height <= 1080 }
                        ?: video.options.lastOrNull()
                        ?: video.fallback
                    if (initial == null) {
                        _state.update { it.copy(isLoading = false, error = PlayerError.NO_STREAMS) }
                    } else {
                        _state.update {
                            it.copy(
                                title = video.title.ifBlank { it.title },
                                isLoading = false,
                                options = video.options,
                                fallback = video.fallback,
                                current = initial
                            )
                        }
                    }
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = PlayerError.from(t)) }
                }
            )
        }
    }
}
