package com.alotube.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.local.FolderKind
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.LocalMediaRepository
import com.alotube.app.data.local.OrderStore
import com.alotube.app.data.local.applyOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalMediaUiState(
    val loading: Boolean = true,
    /** true cuando ya hubo una lectura correcta (así el refresco no muestra spinner). */
    val loaded: Boolean = false,
    val error: String? = null,
    val videos: List<LocalMedia> = emptyList(),
    val audios: List<LocalMedia> = emptyList(),
    /** true = todo el dispositivo (por defecto); false = solo Download/. Ya no hay selector en la interfaz. */
    val includeAll: Boolean = true,
    /** Carpeta de videos abierta (BUCKET_ID). null = se muestran las carpetas. */
    val currentFolderId: Long? = null,
    /** Carpeta de audios abierta en "Música" (BUCKET_ID). null = se muestran las carpetas. */
    val currentAudioFolderId: Long? = null,
    /** Orden elegido por el usuario (arrastrar y soltar), ver OrderStore. Vacío = orden de siempre. */
    val videoFolderOrder: List<Long> = emptyList(),
    val audioFolderOrder: List<Long> = emptyList(),
    /** Orden de archivos por carpeta; clave = OrderStore.fileKey(tipo, bucketId). */
    val fileOrders: Map<String, List<Long>> = emptyMap()
) {
    /** Carpetas con videos, ordenadas por nombre. */
    val folders: List<LocalFolder> by lazy(LazyThreadSafetyMode.NONE) {
        videos.groupBy { it.bucketId }
            .map { (id, list) ->
                // La lista ya viene con lo más reciente primero: el primero sirve de portada
                LocalFolder(id, list.first().bucketName.ifBlank { UNNAMED_FOLDER }, list.size, list.first())
            }
            .sortedBy { it.name.lowercase() }
            .applyOrder(videoFolderOrder) { it.id }
    }

    /** Videos agrupados por carpeta (lo más reciente primero) con el orden elegido por el usuario aplicado. */
    private val videoGroups: Map<Long, List<LocalMedia>> by lazy(LazyThreadSafetyMode.NONE) {
        videos.groupBy { it.bucketId }.mapValues { (id, list) ->
            list.applyOrder(fileOrders[OrderStore.fileKey(FolderKind.VIDEO, id)].orEmpty()) { it.id }
        }
    }

    val currentFolder: LocalFolder? get() = currentFolderId?.let { id -> folders.firstOrNull { it.id == id } }

    /** Audios agrupados por carpeta; dentro de cada una, por título. */
    private val audioGroups: Map<Long, List<LocalMedia>> by lazy(LazyThreadSafetyMode.NONE) {
        audios.groupBy { it.bucketId }.mapValues { (id, list) ->
            list.sortedBy { it.title.lowercase() }
                .applyOrder(fileOrders[OrderStore.fileKey(FolderKind.MUSIC, id)].orEmpty()) { it.id }
        }
    }

    /** Carpetas con audios, ordenadas por nombre. */
    val audioFolders: List<LocalFolder> by lazy(LazyThreadSafetyMode.NONE) {
        audioGroups
            .map { (id, list) ->
                LocalFolder(id, list.first().bucketName.ifBlank { UNNAMED_FOLDER }, list.size, list.first())
            }
            .sortedBy { it.name.lowercase() }
            .applyOrder(audioFolderOrder) { it.id }
    }

    val currentAudioFolder: LocalFolder?
        get() = currentAudioFolderId?.let { id -> audioFolders.firstOrNull { it.id == id } }

    /** Audios de una carpeta (en orden por título). */
    fun audioTracks(folderId: Long): List<LocalMedia> = audioGroups[folderId].orEmpty()

    /** Videos de la carpeta abierta (vacío si no hay ninguna abierta). */
    val folderVideos: List<LocalMedia>
        get() = currentFolderId?.let { id -> videoGroups[id] } ?: emptyList()
}

private const val UNNAMED_FOLDER = "(sin nombre)"


class LocalMediaViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LocalMediaRepository()

    private val _state = MutableStateFlow(LocalMediaUiState())
    val state: StateFlow<LocalMediaUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        // Orden guardado de carpetas y archivos (solo orden visual; no toca archivos)
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                videoFolderOrder = OrderStore.folders(app, FolderKind.VIDEO),
                audioFolderOrder = OrderStore.folders(app, FolderKind.MUSIC),
                fileOrders = OrderStore.fileOrders(app)
            )
        }
    }

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
                            videos = library.videos, audios = library.audios,
                            // Si la carpeta abierta ya no tiene videos, se vuelve a la vista de carpetas
                            currentFolderId = it.currentFolderId?.takeIf { id ->
                                library.videos.any { v -> v.bucketId == id }
                            },
                            currentAudioFolderId = it.currentAudioFolderId?.takeIf { id ->
                                library.audios.any { a -> a.bucketId == id }
                            }
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
        _state.update {
            it.copy(
                includeAll = value, loaded = false, videos = emptyList(), audios = emptyList(),
                currentFolderId = null, currentAudioFolderId = null
            )
        }
        load()
    }

    /** Guarda el nuevo orden de las carpetas (ids en el orden mostrado). */
    fun setFolderOrder(kind: FolderKind, ids: List<Long>) {
        OrderStore.saveFolders(getApplication(), kind, ids)
        _state.update {
            if (kind == FolderKind.VIDEO) it.copy(videoFolderOrder = ids) else it.copy(audioFolderOrder = ids)
        }
    }

    /** Guarda el nuevo orden de los archivos DENTRO de una carpeta (ids en el orden mostrado). */
    fun setFileOrder(kind: FolderKind, bucketId: Long, ids: List<Long>) {
        OrderStore.saveFiles(getApplication(), kind, bucketId, ids)
        _state.update { it.copy(fileOrders = it.fileOrders + (OrderStore.fileKey(kind, bucketId) to ids)) }
    }

    /** Abre una carpeta de videos (vista nivel 2). */
    fun openFolder(id: Long) = _state.update { it.copy(currentFolderId = id) }

    /** Vuelve a la vista de carpetas (vista nivel 1). */
    fun closeFolder() = _state.update { it.copy(currentFolderId = null) }

    /** Abre una carpeta de audios en "Música". */
    fun openAudioFolder(id: Long) = _state.update { it.copy(currentAudioFolderId = id) }

    fun closeAudioFolder() = _state.update { it.copy(currentAudioFolderId = null) }
}
