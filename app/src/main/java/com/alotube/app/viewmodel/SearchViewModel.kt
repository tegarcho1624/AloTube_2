package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.ChannelItem
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
import org.schabi.newpipe.extractor.Page
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
    val error: SearchError? = null,

    // Paginación de videos (scroll infinito)
    val videosNextPage: Page? = null,
    val loadingMoreVideos: Boolean = false,

    // Canales: se cargan aparte, después de los videos
    val channelsStatus: LoadStatus = LoadStatus.IDLE,
    val channels: List<ChannelItem> = emptyList(),
    val channelsNextPage: Page? = null,
    val loadingMoreChannels: Boolean = false,

    /** 0 = Videos, 1 = Canales */
    val tab: Int = 0
)

class SearchViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var channelsJob: Job? = null
    private var moreVideosJob: Job? = null
    private var moreChannelsJob: Job? = null

    // Evento de una sola vez: "abre este video en el reproductor" (cuando el texto era un enlace)
    private val openVideoEvents = Channel<VideoItem>(Channel.BUFFERED)
    val openVideo: Flow<VideoItem> = openVideoEvents.receiveAsFlow()

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
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
        channelsJob?.cancel()
        moreVideosJob?.cancel()
        moreChannelsJob?.cancel()
        _state.update {
            it.copy(
                lastQuery = query,
                isLoading = true,
                hasSearched = true,
                results = emptyList(),
                error = null,
                videosNextPage = null,
                loadingMoreVideos = false,
                channelsStatus = LoadStatus.IDLE,
                channels = emptyList(),
                channelsNextPage = null,
                loadingMoreChannels = false,
                tab = 0
            )
        }
        searchJob = viewModelScope.launch {
            repository.searchVideos(query).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(isLoading = false, results = page.items, videosNextPage = page.nextPage)
                    }
                    // Primero los videos; los canales llegan después, sin bloquear la pantalla
                    loadChannels(query)
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = SearchError.from(t)) }
                }
            )
        }
    }

    // ---------- Videos: scroll infinito ----------

    fun loadMoreVideos() {
        val s = _state.value
        val page = s.videosNextPage ?: return
        if (s.loadingMoreVideos || s.isLoading) return
        val query = s.lastQuery
        _state.update { it.copy(loadingMoreVideos = true) }
        moreVideosJob = viewModelScope.launch {
            repository.moreVideos(query, page).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(
                            results = it.results + p.items,
                            videosNextPage = p.nextPage,
                            loadingMoreVideos = false
                        )
                    }
                },
                // Si falla, se conserva la referencia: al seguir bajando se vuelve a intentar
                onFailure = { _state.update { it.copy(loadingMoreVideos = false) } }
            )
        }
    }

    // ---------- Canales ----------

    private fun loadChannels(query: String) {
        channelsJob?.cancel()
        _state.update {
            it.copy(channelsStatus = LoadStatus.LOADING, channels = emptyList(), channelsNextPage = null)
        }
        channelsJob = viewModelScope.launch {
            repository.searchChannels(query).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(
                            channelsStatus = LoadStatus.READY,
                            channels = p.items,
                            channelsNextPage = p.nextPage
                        )
                    }
                },
                onFailure = { _state.update { it.copy(channelsStatus = LoadStatus.UNAVAILABLE) } }
            )
        }
    }

    fun loadMoreChannels() {
        val s = _state.value
        val page = s.channelsNextPage ?: return
        if (s.loadingMoreChannels || s.channelsStatus != LoadStatus.READY) return
        val query = s.lastQuery
        _state.update { it.copy(loadingMoreChannels = true) }
        moreChannelsJob = viewModelScope.launch {
            repository.moreChannels(query, page).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(
                            channels = it.channels + p.items,
                            channelsNextPage = p.nextPage,
                            loadingMoreChannels = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(loadingMoreChannels = false) } }
            )
        }
    }
}
