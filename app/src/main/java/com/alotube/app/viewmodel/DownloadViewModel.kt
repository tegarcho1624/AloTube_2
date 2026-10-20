package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.download.DownloadChoice
import com.alotube.app.data.download.DownloadOptions
import com.alotube.app.data.download.DownloadRequest
import com.alotube.app.data.repository.StreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadMode { VIDEO, AUDIO }

data class DownloadUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val fileName: String = "",
    val mode: DownloadMode = DownloadMode.VIDEO,
    val options: DownloadOptions? = null,
    val videoIndex: Int = 0,
    val trackIndex: Int = 0,
    val audioIndex: Int = 0
) {
    val hasVideo: Boolean get() = options?.videoChoices?.isNotEmpty() == true
    val hasAudio: Boolean get() = options?.audioTracks?.isNotEmpty() == true

    val selectedChoice: DownloadChoice?
        get() = when (mode) {
            DownloadMode.VIDEO -> options?.videoChoices?.getOrNull(videoIndex)
            DownloadMode.AUDIO -> options?.audioTracks?.getOrNull(trackIndex)?.streams?.getOrNull(audioIndex)
        }
}

class DownloadViewModel(
    private val repository: StreamRepository = StreamRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Obtiene los formatos reales del video (StreamInfo) y rellena las listas. */
    fun open(url: String, title: String) {
        job?.cancel()
        _state.value = DownloadUiState(fileName = title)
        job = viewModelScope.launch {
            repository.getDownloadOptions(url).fold(
                onSuccess = { options ->
                    _state.update {
                        it.copy(
                            loading = false,
                            options = options,
                            // Sin video con audio incluido, se parte de Audio
                            mode = if (options.videoChoices.isNotEmpty()) DownloadMode.VIDEO else DownloadMode.AUDIO
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

    fun setFileName(value: String) = _state.update { it.copy(fileName = value) }
    fun setMode(mode: DownloadMode) = _state.update { it.copy(mode = mode) }
    fun selectVideo(index: Int) = _state.update { it.copy(videoIndex = index) }
    fun selectTrack(index: Int) = _state.update { it.copy(trackIndex = index, audioIndex = 0) }
    fun selectAudio(index: Int) = _state.update { it.copy(audioIndex = index) }

    /** Petición lista para el servicio, o null si falta algo. */
    fun buildRequest(): DownloadRequest? {
        val s = _state.value
        val choice = s.selectedChoice ?: return null
        return DownloadRequest(
            url = choice.url,
            name = sanitize(s.fileName, choice.extension),
            extension = choice.extension,
            mimeType = choice.mimeType
        )
    }

    /** Quita caracteres no válidos en nombres de archivo y la extensión repetida. */
    private fun sanitize(raw: String, extension: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimEnd('.')
            .removeSuffix(".$extension")
        return cleaned.take(120).ifBlank { "AloTube" }
    }
}
