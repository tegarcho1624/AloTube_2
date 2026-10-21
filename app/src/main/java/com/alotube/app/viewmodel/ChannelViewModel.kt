package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.ChannelDetails
import com.alotube.app.data.model.VideoItem
import com.alotube.app.data.repository.ChannelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

data class ChannelUiState(
    val loading: Boolean = true,
    /** Detalle técnico si el canal no pudo cargar (pantalla de error con botón para volver). */
    val error: String? = null,
    val details: ChannelDetails? = null,
    val videos: List<VideoItem> = emptyList(),
    /** READY o UNAVAILABLE (si el encabezado cargó pero la lista de videos falló). */
    val videosStatus: LoadStatus = LoadStatus.IDLE,
    val videosNote: String? = null,
    val nextPage: Page? = null,
    val loadingMore: Boolean = false
)

class ChannelViewModel(
    private val repository: ChannelRepository = ChannelRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private var currentUrl: String? = null
    private var videosTab: ListLinkHandler? = null
    private var job: Job? = null
    private var moreJob: Job? = null

    /** Idempotente: si ya se cargó este canal no hace nada; si es otro, lo carga. */
    fun load(url: String) {
        if (currentUrl == url) return
        currentUrl = url
        start(url)
    }

    fun retry() {
        currentUrl?.let { start(it) }
    }

    private fun start(url: String) {
        job?.cancel()
        moreJob?.cancel()
        videosTab = null
        _state.value = ChannelUiState()
        job = viewModelScope.launch {
            repository.load(url).fold(
                onSuccess = { loaded ->
                    videosTab = loaded.videosTab
                    _state.update {
                        it.copy(
                            loading = false,
                            details = loaded.details,
                            videos = loaded.videos?.items.orEmpty(),
                            nextPage = loaded.videos?.nextPage,
                            videosStatus = if (loaded.videosError == null) LoadStatus.READY else LoadStatus.UNAVAILABLE,
                            videosNote = loaded.videosError
                        )
                    }
                },
                onFailure = { t ->
                    _state.update {
                        it.copy(loading = false, error = t.message?.take(160) ?: t.javaClass.simpleName)
                    }
                }
            )
        }
    }

    /** Scroll infinito de los videos del canal. */
    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        val tab = videosTab ?: return
        if (s.loadingMore || s.videosStatus != LoadStatus.READY) return
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            repository.moreVideos(tab, page, s.details?.name.orEmpty()).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(videos = it.videos + p.items, nextPage = p.nextPage, loadingMore = false)
                    }
                },
                onFailure = { _state.update { it.copy(loadingMore = false) } }
            )
        }
    }
}
