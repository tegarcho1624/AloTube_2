package com.alotube.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.LocalMediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalMediaUiState(
    val loading: Boolean = false,
    /** true cuando ya hubo una lectura correcta (así el refresco no muestra spinner). */
    val loaded: Boolean = false,
    val error: String? = null,
    val videos: List<LocalMedia> = emptyList(),
    val audios: List<LocalMedia> = emptyList(),
    /** false = solo Download/; true = todo el dispositivo. */
    val includeAll: Boolean = false
)

class LocalMediaViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LocalMediaRepository()

    private val _state = MutableStateFlow(LocalMediaUiState())
    val state: StateFlow<LocalMediaUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Lee (o refresca) la lista. Si ya había datos se muestran mientras se actualiza. */
    fun load() {
        job?.cancel()
        if (!_state.value.loaded) _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            repository.load(getApplication(), _state.value.includeAll).fold(
                onSuccess = { library ->
                    _state.update {
                        it.copy(
                            loading = false, loaded = true, error = null,
                            videos = library.videos, audios = library.audios
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

    fun setIncludeAll(value: Boolean) {
        _state.update { it.copy(includeAll = value, loaded = false, videos = emptyList(), audios = emptyList()) }
        load()
    }
}
