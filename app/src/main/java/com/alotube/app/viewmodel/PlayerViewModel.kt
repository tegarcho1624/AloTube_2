package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.data.repository.StreamFetchException
import com.alotube.app.player.PlaybackQueue
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
    NETWORK, BLOCKED, UNAVAILABLE, EXTRACTION, NO_STREAMS, PLAYBACK, UNKNOWN, LOCAL_FILE;

    companion object {
        fun from(t: Throwable): PlayerError = when (t) {
            // Tras agotar los intentos se clasifica por la última excepción real (si la hubo)
            is StreamFetchException -> t.cause?.let { from(it) } ?: NO_STREAMS
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
    val author: String = "",
    val thumbnailUrl: String? = null,
    val url: String = "",
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val uploadDate: String? = null,
    val isLoading: Boolean = true,
    /** Calidades del menú (de mayor a menor). */
    val options: List<PlaybackOption> = emptyList(),
    val fallback: PlaybackOption? = null,
    /** Lo que se está reproduciendo ahora. */
    val current: PlaybackOption? = null,
    val error: PlayerError? = null,
    /** Texto técnico para diagnosticar (intentos, excepciones, cliente...). */
    val errorDetail: String? = null
)

class PlayerViewModel(
    private val repository: StreamRepository = StreamRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var videoUrl: String? = null
    private var fallbackTried = false
    private val playbackNotes = mutableListOf<String>()
    private var job: Job? = null

    init {
        // Siguiente/Anterior (notificación, bloqueo, botones) solo mueven la cola: aquí se carga el video
        viewModelScope.launch {
            PlaybackQueue.state.collect { queue ->
                queue.current?.let { load(it.url, it.title, it.author, it.thumbnailUrl, it.viewCount, it.uploadDateText) }
            }
        }
    }

    /** Idempotente: si ya se cargó esta URL, no hace nada. */
    fun load(
        url: String,
        title: String,
        author: String = "",
        thumbnailUrl: String? = null,
        viewCount: Long = -1L,
        uploadDate: String? = null
    ) {
        if (videoUrl == url) return
        videoUrl = url
        _state.value = PlayerUiState(
            title = title, author = author, thumbnailUrl = thumbnailUrl,
            url = url, viewCount = viewCount, uploadDate = uploadDate
        )
        fetch()
    }

    /** Vuelve a pedir los enlaces (sirve también cuando un enlace caducó). */
    fun retry() {
        _state.update {
            PlayerUiState(
                title = it.title, author = it.author, thumbnailUrl = it.thumbnailUrl,
                url = it.url, viewCount = it.viewCount, uploadDate = it.uploadDate
            )
        }
        fetch()
    }

    fun selectQuality(option: PlaybackOption) {
        fallbackTried = false
        playbackNotes.clear()
        _state.update { it.copy(current = option) }
    }

    /**
     * El reproductor falló: se intenta una vez con el flujo de respaldo (directo o manifiesto);
     * si tampoco funciona, error en pantalla con el detalle de cada intento.
     */
    fun onPlaybackError(detail: String) {
        val s = _state.value
        if (s.error != null) return
        val cur = s.current
        playbackNotes += "Reproducción ${cur?.label ?: "?"} (${cur?.kindLabel ?: "?"}) → $detail"

        // Archivo local dañado o que ya no existe: no hay flujo de respaldo ni enlace que renovar
        if (cur?.isLocal == true) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = PlayerError.LOCAL_FILE,
                    errorDetail = playbackNotes.joinToString("\n")
                )
            }
            return
        }

        val fb = s.fallback
        if (fb != null && cur != fb && !fallbackTried) {
            fallbackTried = true
            _state.update { it.copy(current = fb) }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = PlayerError.PLAYBACK,
                    errorDetail = playbackNotes.joinToString("\n")
                )
            }
        }
    }

    private fun fetch() {
        val url = videoUrl ?: return
        fallbackTried = false
        playbackNotes.clear()
        job?.cancel()

        // Archivo del dispositivo: se reproduce directo, sin NewPipeExtractor
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val option = PlaybackOption(label = "Local", height = 0, videoUrl = url, isLocal = true)
            _state.update {
                it.copy(isLoading = false, options = listOf(option), fallback = null, current = option)
            }
            return
        }

        job = viewModelScope.launch {
            repository.getPlayable(url).fold(
                onSuccess = { video ->
                    // Por defecto la mejor calidad hasta 1080p (4K/8K suelen ir en VP9/AV1, más pesados)
                    val initial = video.options.firstOrNull { it.height <= 1080 }
                        ?: video.options.lastOrNull()
                        ?: video.fallback
                    if (initial == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = PlayerError.NO_STREAMS,
                                errorDetail = "Sin opciones de reproducción."
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                title = video.title.ifBlank { it.title },
                                author = video.author.ifBlank { it.author },
                                viewCount = if (video.viewCount >= 0) video.viewCount else it.viewCount,
                                likeCount = video.likeCount,
                                uploadDate = video.uploadDate ?: it.uploadDate,
                                isLoading = false,
                                options = video.options,
                                fallback = video.fallback,
                                current = initial
                            )
                        }
                    }
                },
                onFailure = { t ->
                    val detail = (t as? StreamFetchException)?.details
                        ?: "${t.javaClass.simpleName}: ${t.message}"
                    _state.update {
                        it.copy(isLoading = false, error = PlayerError.from(t), errorDetail = detail)
                    }
                }
            )
        }
    }
}
