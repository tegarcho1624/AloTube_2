package com.alotube.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.local.FolderKind
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.LocalFolderCard
import com.alotube.app.ui.components.LocalVideoCard
import com.alotube.app.ui.components.FolderIconMenu
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.dragVisual
import com.alotube.app.ui.components.move
import com.alotube.app.ui.components.rememberFolderIcon
import com.alotube.app.ui.components.rememberGridReorderState
import com.alotube.app.ui.components.rememberReorderList
import com.alotube.app.ui.components.reorderGestures
import com.alotube.app.ui.components.gridColumns
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LocalMediaUiState
import com.alotube.app.viewmodel.LocalMediaViewModel

/**
 * Pestaña "Video Player": gestor de carpetas de videos del dispositivo (por defecto solo Descargas).
 *  - Nivel 1: cuadrícula de carpetas que contienen videos.
 *  - Nivel 2: videos de la carpeta abierta (estado `currentFolderId` en el ViewModel).
 * Al tocar un video se abre el mismo reproductor de AloTube con el content:// URI.
 * Los audios ahora viven en la pestaña "Música" (MusicScreen).
 */
@Composable
fun LocalPlayerScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: LocalMediaViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberMediaPermission()

    // Cada vez que se entra a la pestaña (o se concede el permiso) se refresca la lista
    LaunchedEffect(permission.granted) { if (permission.granted) viewModel.load() }

    val folder = state.currentFolder

    if (folder != null && permission.granted && state.loaded) {
        // Nivel 2: videos de la carpeta (la misma vista que se usa en Ajustes > Archivados)
        VideoFolderView(
            folder = folder,
            videos = state.folderVideos,
            root = stringResource(R.string.lp_breadcrumb_root),
            onBack = viewModel::closeFolder,
            onPlay = { list, index -> onVideoClick(list.map { it.toVideoItem() }, index) },
            onMissing = viewModel::load,
            onFileOrder = { ids -> viewModel.setFileOrder(FolderKind.VIDEO, folder.id, ids) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LocalHeader(title = stringResource(R.string.lp_title))

        if (!permission.granted) {
            PermissionContent(permission)
        } else {
            VideoFoldersContent(
                state = state,
                onRetry = viewModel::load,
                onOpenFolder = viewModel::openFolder,
                onFolderOrder = { ids -> viewModel.setFolderOrder(FolderKind.VIDEO, ids) },
                onFolderArchive = { f -> viewModel.setArchived(FolderKind.VIDEO, f.id, true) }
            )
        }
    }
}

/**
 * Videos de UNA carpeta: "Raíz > Carpeta" con lupa, búsqueda local, cuadrícula con arrastre y reproducción.
 * La usan "Video Player" y Ajustes > Archivados: no mira si la carpeta está archivada, solo muestra los
 * [videos] que se le pasan. Atrás ([onBack]) cierra la búsqueda y luego la carpeta.
 */
@Composable
fun VideoFolderView(
    folder: LocalFolder,
    videos: List<LocalMedia>,
    root: String,
    onBack: () -> Unit,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit,
    onFileOrder: (List<Long>) -> Unit
) {
    val search = rememberFolderSearch(folder.id)
    val play = rememberSafePlay(onPlay, onMissing)
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
                VideoFileGrid(source = videos, onPlay = play, onOrder = onFileOrder)
            } else {
                VideoSearchResults(results = videos.filterByName(search.query), onPlay = play)
            }
        }
    }
}

/** Nivel 2: flecha atrás + "Inicio > Nombre de la carpeta" (también lo usa Música con su propia raíz). */
@Composable
fun Breadcrumb(
    name: String,
    onBack: () -> Unit,
    root: String = stringResource(R.string.lp_breadcrumb_root),
    onSearch: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.lib_back),
                tint = AloColors.Mut
            )
        }
        Text(
            text = root,
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Mut,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp)
        )
        Text(
            text = "  >  ",
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Mut
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.accents.hi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSearch != null) FolderSearchButton(onClick = onSearch)
    }
}

@Composable
private fun VideoFoldersContent(
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

            state.videos.isEmpty() -> EmptyMedia(
                text = stringResource(R.string.lp_empty_videos),
                modifier = Modifier.align(Alignment.Center)
            )

            // Nivel 1: carpetas (toque largo + arrastre = reordenar; toque largo quieto = menú del ícono)
            else -> VideoFolderGrid(
                source = state.visibleFolders,
                onOpenFolder = onOpenFolder,
                onOrder = onFolderOrder,
                onArchive = onFolderArchive
            )
        }
    }
}

/** Resultado de la búsqueda en una carpeta de videos: solo mostrar y reproducir (sin arrastrar, para no alterar el orden guardado). */
@Composable
private fun VideoSearchResults(results: List<LocalMedia>, onPlay: (List<LocalMedia>, Int) -> Unit) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyMedia(text = stringResource(R.string.fs_none))
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(results, key = { _, media -> media.id }) { index, media ->
            LocalVideoCard(media = media, onClick = { onPlay(results, index) })
        }
    }
}

/** Videos de una carpeta: se pueden reordenar (solo dentro de la misma carpeta) con toque largo + arrastre. */
@Composable
private fun VideoFileGrid(
    source: List<LocalMedia>,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onOrder: (List<Long>) -> Unit
) {
    val videos = rememberReorderList(source)
    val gridState = rememberLazyGridState()
    val reorder = rememberGridReorderState(
        gridState = gridState,
        onMove = { from, to -> videos.move(from, to) },
        onDrop = { _, moved -> if (moved) onOrder(videos.map { it.id }) }
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns()),
        modifier = Modifier
            .fillMaxSize()
            .reorderGestures(reorder),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(videos, key = { _, media -> media.id }) { index, media ->
            LocalVideoCard(
                media = media,
                onClick = { onPlay(videos.toList(), index) },
                modifier = (
                    if (reorder.isDragged(media.id)) Modifier
                    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    ).dragVisual(reorder, media.id, RoundedCornerShape(16.dp))
            )
        }
    }
}

/**
 * Carpetas de video: toque largo + arrastre cambia su posición; toque largo sin mover abre el menú
 * "Establecer icono / Quitar fondo / Archivar". También se usa en Ajustes > Archivados (con [archived]). El orden y los íconos se guardan en la app (no tocan los archivos).
 */
@Composable
fun VideoFolderGrid(
    source: List<LocalFolder>,
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
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .reorderGestures(reorder),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(folders, key = { it.id }) { f ->
            LocalFolderCard(
                folder = f,
                onClick = { onOpenFolder(f.id) },
                icon = rememberFolderIcon(FolderKind.VIDEO, f.id),
                modifier = (
                    if (reorder.isDragged(f.id)) Modifier
                    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    ).dragVisual(reorder, f.id, RoundedCornerShape(16.dp))
            )
        }
    }
    FolderIconMenu(
        kind = FolderKind.VIDEO,
        target = menuFolder,
        onDismiss = { menuFolder = null },
        onArchive = onArchive,
        archived = archived
    )
}
