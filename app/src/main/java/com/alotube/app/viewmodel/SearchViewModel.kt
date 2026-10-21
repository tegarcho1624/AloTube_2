package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.VideoItem
import com.alotube.app.data.repository.VideoRepository
import com.alotube.app.util.YoutubeLinks
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

enum class SearchError {
    NETWORK, BLOCKED, EXTRACTION, UNKNOWN, INVALID_LINK;

    companion object {
        /** Errores al abrir un enlace: red y captcha se distinguen; el resto es "enlace no válido o video no disponible". */
        fun fromLink(t: Throwable): SearchError = when (t) {
            is ReCaptchaException -> BLOCKED
            is IOException -> NETWORK
            else -> INVALID_LINK
        }

        fun from(t: Throwable): SearchError = when (t) {
            is ReCaptchaException -> BLOCKED
            is IOException -> NETWORK
            is ExtractionException -> EXTRACTION
            else -> UNKNOWN
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val lastQuery: String = "",
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<VideoItem> = emptyList(),
    val error: SearchError? = null
)

class SearchViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    // Evento de una sola vez: "abre este video en el reproductor" (cuando el texto era un enlace)
    private val openVideoEvents = Channel<VideoItem>(Channel.BUFFERED)
    val openVideo: Flow<VideoItem> = openVideoEvents.receiveAsFlow()

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
    }

    /**
     * El texto era un enlace de YouTube: no se busca. Se obtiene el StreamInfo (con spinner) y se
     * emite el video para abrirlo en el reproductor. Los resultados anteriores se conservan.
     */
    private fun openLink(url: String) {
        searchJob?.cancel()
        val id = YoutubeLinks.extractVideoId(url)
        if (id == null) {
            _state.update { it.copy(isLoading = false, error = SearchError.INVALID_LINK) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            repository.resolveLink("https://www.youtube.com/watch?v=$id").fold(
                onSuccess = { video ->
                    _state.update { it.copy(isLoading = false) }
                    openVideoEvents.trySend(video)
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = SearchError.fromLink(t)) }
                }
            )
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        // ¿Es un enlace de YouTube? Entonces se abre directo en vez de buscar
        YoutubeLinks.extractUrl(query)?.let {
            openLink(it)
            return
        }

        searchJob?.cancel()
        _state.update {
            it.copy(
                lastQuery = query,
                isLoading = true,
                hasSearched = true,
                results = emptyList(),
                error = null
            )
        }
        searchJob = viewModelScope.launch {
            repository.search(query).fold(
                onSuccess = { videos ->
                    _state.update { it.copy(isLoading = false, results = videos) }
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = SearchError.from(t)) }
                }
            )
        }
    }
}
