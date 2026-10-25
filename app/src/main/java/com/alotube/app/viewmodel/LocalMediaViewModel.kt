package com.alotube.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.local.ArchiveStore
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
    val fileOrders: Map<String, List<Long>> = emptyMap(),
    /** Carpetas archivadas (claves de ArchiveStore). */
    val archivedKeys: Set<String> = emptySet()
) {
    /** Carpetas con videos, ordenadas por nombre. */
    val folders: List<LocalFolder> by lazy(LazyThreadSafetyMode.NONE) {
        videos.groupBy { it.bucketId }
            .map { (id, list) ->
                // La lista ya viene con lo más reciente primero: el primero sirve de portada
                LocalFolder(
                    id, list.first().bucketName.ifBlank { UNNAMED_FOLDER }, list.size, list.first(),
                    isArchived = ArchiveStore.key(FolderKind.VIDEO, id) in archivedKeys
                )
            }
            .sortedBy { it.name.lowercase() }
            .applyOrder(videoFolderOrder) { it.id }
    }

    /** Carpetas de video que se ven en "Video Player" (sin las archivadas). */
    val visibleFolders: List<LocalFolder> get() = folders.filter { !it.isArchived }

    /** Carpetas de video archivadas (Ajustes > Archivados). */
    val archivedFolders: List<LocalFolder> get() = folders.filter { it.isArchived }

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
                LocalFolder(
                    id, list.first().bucketName.ifBlank { UNNAMED_FOLDER }, list.size, list.first(),
                    isArchived = ArchiveStore.key(FolderKind.MUSIC, id) in archivedKeys
                )
            }
            .sortedBy { it.name.lowercase() }
            .applyOrder(audioFolderOrder) { it.id }
    }

    /** Carpetas de música que se ven en "Música" (sin las archivadas). */
    val visibleAudioFolders: List<LocalFolder> get() = audioFolders.filter { !it.isArchived }

    /** Carpetas de música archivadas (Ajustes > Archivados). */
    val archivedAudioFolders: List<LocalFolder> get() = audioFolders.filter { it.isArchived }

    val currentAudioFolder: LocalFolder?
        get() = currentAudioFolderId?.let { id -> audioFolders.firstOrNull { it.id == id } }

    /** Audios de una carpeta (en orden por título). */
    fun audioTracks(folderId: Long): List<LocalMedia> = audioGroups[folderId].orEmpty()

    /** Videos de una carpeta cualquiera (en el orden elegido por el usuario). */
    fun videosOf(folderId: Long): List<LocalMedia> = videoGroups[folderId].orEmpty()

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
                fileOrders = OrderStore.fileOrders(app),
                archivedKeys = ArchiveStore.all(app)
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
        // [ids] puede ser solo una parte (las visibles o las archivadas): se reparte en los huecos que esas
        // carpetas ya ocupaban y las demás conservan su sitio, así un orden no desordena al otro.
        val current = _state.value
        val all = (if (kind == FolderKind.VIDEO) current.folders else current.audioFolders).map { it.id }
        val subset = ids.filter { it in all }.distinct()
        val inSubset = subset.toSet()
        val next = subset.iterator()
        val merged = all.map { if (it in inSubset) next.next() else it }
        OrderStore.saveFolders(getApplication(), kind, merged)
        _state.update {
            if (kind == FolderKind.VIDEO) it.copy(videoFolderOrder = merged) else it.copy(audioFolderOrder = merged)
        }
    }

    /** Archiva o desarchiva una carpeta (solo una marca en la app; no toca los archivos). */
    fun setArchived(kind: FolderKind, folderId: Long, archived: Boolean) {
        val key = ArchiveStore.key(kind, folderId)
        val next = if (archived) _state.value.archivedKeys + key else _state.value.archivedKeys - key
        ArchiveStore.save(getApplication(), next)
        _state.update { it.copy(archivedKeys = next) }
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
