package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.auth.SessionStore
import com.alotube.app.data.library.InnerTubeAuthException
import com.alotube.app.data.library.LibraryChannel
import com.alotube.app.data.library.LibraryPlaylist
import com.alotube.app.data.library.LibraryRepository
import com.alotube.app.data.library.LibraryResult
import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoadStatus { IDLE, LOADING, READY, UNAVAILABLE }

data class SectionState<T>(
    val status: LoadStatus = LoadStatus.IDLE,
    val items: List<T> = emptyList(),
    /** Detalle técnico (vacío/ error) para diagnosticar. */
    val note: String? = null,
    /** La sesión caducó: hay que volver a iniciar sesión. */
    val expired: Boolean = false
)

data class PlaylistDetail(
    val playlist: LibraryPlaylist,
    val videos: SectionState<VideoItem> = SectionState(LoadStatus.LOADING)
)

data class LibraryUiState(
    val loggedIn: Boolean = false,
    val playlists: SectionState<LibraryPlaylist> = SectionState(),
    val subscriptions: SectionState<LibraryChannel> = SectionState(),
    val detail: PlaylistDetail? = null
)

class LibraryViewModel(
    private val repository: LibraryRepository = LibraryRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var detailJob: Job? = null

    init {
        // Carga al entrar a Biblioteca con sesión, y reacciona a iniciar/cerrar sesión
        viewModelScope.launch {
            SessionStore.state.map { it.loggedIn }.distinctUntilChanged().collect { loggedIn ->
                loadJob?.cancel()
                detailJob?.cancel()
                _state.value = LibraryUiState(loggedIn = loggedIn)
                if (loggedIn) refresh()
            }
        }
    }

    fun refresh() {
        if (!_state.value.loggedIn) return
        loadJob?.cancel()
        _state.update {
            it.copy(
                playlists = SectionState(LoadStatus.LOADING),
                subscriptions = SectionState(LoadStatus.LOADING)
            )
        }
        loadJob = viewModelScope.launch {
            // Cada sección carga y falla por separado
            launch {
                val section = repository.playlists().toSection()
                _state.update { it.copy(playlists = section) }
            }
            launch {
                val section = repository.subscriptions().toSection()
                _state.update { it.copy(subscriptions = section) }
            }
        }
    }

    fun openPlaylist(playlist: LibraryPlaylist) {
        _state.update { it.copy(detail = PlaylistDetail(playlist)) }
        loadDetail(playlist)
    }

    fun retryDetail() {
        _state.value.detail?.playlist?.let { openPlaylist(it) }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null) }
    }

    private fun loadDetail(playlist: LibraryPlaylist) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val result = repository.playlistVideos(playlist.id)
            val section = result.fold(
                onSuccess = { SectionState(LoadStatus.READY, it) },
                onFailure = {
                    SectionState<VideoItem>(
                        LoadStatus.UNAVAILABLE,
                        note = it.message?.take(200),
                        expired = it is InnerTubeAuthException
                    )
                }
            )
            _state.update { s ->
                // Si el usuario ya cerró o cambió de lista, se ignora el resultado
                val d = s.detail
                if (d != null && d.playlist.id == playlist.id) s.copy(detail = d.copy(videos = section)) else s
            }
        }
    }

    private fun <T> Result<LibraryResult<T>>.toSection(): SectionState<T> = fold(
        onSuccess = { SectionState(LoadStatus.READY, it.items, it.note) },
        onFailure = {
            SectionState(
                LoadStatus.UNAVAILABLE,
                note = it.message?.take(200),
                expired = it is InnerTubeAuthException
            )
        }
    )
}
