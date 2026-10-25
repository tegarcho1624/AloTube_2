package com.alotube.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.local.FolderKind
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.player.rememberNowPlaying
import com.alotube.app.ui.components.AudioFolderCard
import com.alotube.app.ui.components.AudioTrackRow
import com.alotube.app.ui.components.FolderIconMenu
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.dragVisual
import com.alotube.app.ui.components.move
import com.alotube.app.ui.components.rememberFolderIcon
import com.alotube.app.ui.components.rememberGridReorderState
import com.alotube.app.ui.components.rememberListReorderState
import com.alotube.app.ui.components.rememberReorderList
import com.alotube.app.ui.components.reorderGestures
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LocalMediaUiState
import com.alotube.app.viewmodel.LocalMediaViewModel

/**
 * Pestaña "Música": audios locales del dispositivo (por defecto solo Descargas) con aspecto de
 * app de música.
 *  - Nivel 1: cuadrícula de carpetas con la carátula del primer audio que la tenga (o una nota musical).
 *  - Nivel 2: canciones de la carpeta (`currentAudioFolderId` en el ViewModel) con carátula, título,
 *    artista y duración; la que suena se ilumina con un ecualizador.
 * Las etiquetas (MediaMetadataRetriever) se leen en segundo plano. Al tocar una canción se abre el
 * mismo reproductor de AloTube con el content:// URI. El mini-reproductor vive en AloTubeRoot.
 */
@Composable
fun MusicScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: LocalMediaViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberMediaPermission()

    LaunchedEffect(permission.granted) { if (permission.granted) viewModel.load() }

    val folder = state.currentAudioFolder

    if (folder != null && permission.granted && state.loaded) {
        // Nivel 2: canciones de la carpeta (la misma vista que se usa en Ajustes > Archivados)
        AudioFolderView(
            folder = folder,
            tracks = state.audioTracks(folder.id),
            root = stringResource(R.string.mp_root),
            onBack = viewModel::closeAudioFolder,
            onPlay = { list, index -> onVideoClick(list.map { it.toVideoItem() }, index) },
            onMissing = viewModel::load,
            onTrackOrder = { ids -> viewModel.setFileOrder(FolderKind.MUSIC, folder.id, ids) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LocalHeader(title = stringResource(R.string.mp_title))

        if (!permission.granted) {
            PermissionContent(permission)
        } else {
            MusicContent(
                state = state,
                onRetry = viewModel::load,
                onOpenFolder = viewModel::openAudioFolder,
                onFolderOrder = { ids -> viewModel.setFolderOrder(FolderKind.MUSIC, ids) },
                onFolderArchive = { f -> viewModel.setArchived(FolderKind.MUSIC, f.id, true) }
            )
        }
    }
}

/**
 * Canciones de UNA carpeta: "Raíz > Carpeta" con lupa, búsqueda local, lista con arrastre y reproducción.
 * La usan "Música" ([root] = Música) y Ajustes > Archivados ([root] = Archivados): no mira si la carpeta está
 * archivada, solo muestra las [tracks] que se le pasan. Atrás ([onBack]) cierra la búsqueda y luego la carpeta.
 */
@Composable
fun AudioFolderView(
    folder: LocalFolder,
    tracks: List<LocalMedia>,
    root: String,
    onBack: () -> Unit,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit,
    onTrackOrder: (List<Long>) -> Unit
) {
    val search = rememberFolderSearch(folder.id)
    val play = rememberSafePlay(onPlay, onMissing)
    val now = rememberNowPlaying()
    BackHandler { onBack() }
    // Con la búsqueda abierta, atrás la cierra primero
    BackHandler(enabled = search.active) { search.close() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (search.active) {
            FolderSearchBar(search = search, onBack = onBack)
        } else {
            Breadcrumb(name = folder.name, onBack = onBack, root = root, onSearch = { search.active = true })
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (search.query.isBlank()) {
                // Toque largo + arrastre = cambiar el orden
                AudioTrackList(
                    source = tracks,
                    currentUrl = now.item?.url,
                    playing = now.isPlaying,
                    onPlay = play,
                    onOrder = onTrackOrder
                )
            } else {
                AudioSearchResults(
                    results = tracks.filterByName(search.query),
                    currentUrl = now.item?.url,
                    playing = now.isPlaying,
                    onPlay = play
                )
            }
        }
    }
}

@Composable
private fun MusicContent(
    state: LocalMediaUiState,
    onRetry: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onFolderOrder: (List<Long>) -> Unit,
    onFolderArchive: (LocalFolder) -> Unit
) {
    val accents = MaterialTheme.accents

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !state.loaded && state.loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = accents.accent
            )

            !state.loaded && state.error != null -> StatusMessage(
                text = stringResource(R.string.lp_error) + "\n\n" + state.error,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )

            state.audios.isEmpty() -> EmptyMedia(
                text = stringResource(R.string.lp_empty_audios),
                modifier = Modifier.align(Alignment.Center)
            )

            // Nivel 1: carpetas (toque largo + arrastre = reordenar; toque largo quieto = menú del ícono)
            else -> AudioFolderGrid(
                source = state.visibleAudioFolders,
                tracksOf = state::audioTracks,
                onOpenFolder = onOpenFolder,
                onOrder = onFolderOrder,
                onArchive = onFolderArchive
            )
        }
    }
}

/** Resultado de la búsqueda en una carpeta de música: solo mostrar y reproducir (sin arrastrar, para no alterar el orden guardado). */
@Composable
private fun AudioSearchResults(
    results: List<LocalMedia>,
    currentUrl: String?,
    playing: Boolean,
    onPlay: (List<LocalMedia>, Int) -> Unit
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyMedia(text = stringResource(R.string.fs_none))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(results, key = { _, media -> media.id }) { index, media ->
            AudioTrackRow(
                media = media,
                current = currentUrl != null && currentUrl == media.uri.toString(),
                playing = playing,
                onClick = { onPlay(results, index) }
            )
        }
    }
}

/** Canciones de una carpeta: se pueden reordenar (solo dentro de la misma carpeta) con toque largo + arrastre. */
@Composable
private fun AudioTrackList(
    source: List<LocalMedia>,
    currentUrl: String?,
    playing: Boolean,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onOrder: (List<Long>) -> Unit
) {
    val tracks = rememberReorderList(source)
    val listState = rememberLazyListState()
    val reorder = rememberListReorderState(
        listState = listState,
        onMove = { from, to -> tracks.move(from, to) },
        onDrop = { _, moved -> if (moved) onOrder(tracks.map { it.id }) }
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .reorderGestures(reorder),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tracks, key = { _, media -> media.id }) { index, media ->
            AudioTrackRow(
                media = media,
                current = currentUrl != null && currentUrl == media.uri.toString(),
                playing = playing,
                onClick = { onPlay(tracks.toList(), index) },
                modifier = (
                    if (reorder.isDragged(media.id)) Modifier
                    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    ).dragVisual(reorder, media.id, RoundedCornerShape(14.dp))
            )
        }
    }
}

/**
 * Carpetas de música: toque largo + arrastre cambia su posición; toque largo sin mover abre el menú
 * "Establecer icono / Quitar fondo / Archivar". También se usa en Ajustes > Archivados (con [archived]). El orden y los íconos se guardan en la app (no tocan los archivos).
 */
@Composable
fun AudioFolderGrid(
    source: List<LocalFolder>,
    tracksOf: (Long) -> List<LocalMedia>,
    onOpenFolder: (Long) -> Unit,
    onOrder: (List<Long>) -> Unit,
    /** Archivar (o Desarchivar si [archived]) desde el menú de toque largo. */
    onArchive: (LocalFolder) -> Unit,
    archived: Boolean = false
) {
    val folders = rememberReorderList(source)
    val gridState = rememberLazyGridState()
    var menuFolder by remember { mutableStateOf<LocalFolder?>(null) }
    val reorder = rememberGridReorderState(
        gridState = gridState,
        onMove = { from, to -> folders.move(from, to) },
        onDrop = { key, moved ->
            if (moved) {
                onOrder(folders.map { it.id })
            } else {
                menuFolder = folders.firstOrNull { it.id == key }
            }
        }
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier
            .fillMaxSize()
            .reorderGestures(reorder),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(folders, key = { it.id }) { f ->
            AudioFolderCard(
                folder = f,
                tracks = tracksOf(f.id),
                onClick = { onOpenFolder(f.id) },
                icon = rememberFolderIcon(FolderKind.MUSIC, f.id),
                modifier = (
                    if (reorder.isDragged(f.id)) Modifier
                    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    ).dragVisual(reorder, f.id, RoundedCornerShape(16.dp))
            )
        }
    }
    FolderIconMenu(
        kind = FolderKind.MUSIC,
        target = menuFolder,
        onDismiss = { menuFolder = null },
        onArchive = onArchive,
        archived = archived
    )
}
