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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.LocalFolderCard
import com.alotube.app.ui.components.LocalVideoCard
import com.alotube.app.ui.components.StatusMessage
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

    // Atrás dentro de una carpeta = volver a la vista de carpetas
    BackHandler(enabled = folder != null) { viewModel.closeFolder() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (folder == null) {
            LocalHeader(
                title = stringResource(R.string.lp_title)
            )
        } else {
            Breadcrumb(name = folder.name, onBack = viewModel::closeFolder)
        }

        if (!permission.granted) {
            PermissionContent(permission)
        } else {
            VideoFoldersContent(
                state = state,
                onRetry = viewModel::load,
                onOpenFolder = viewModel::openFolder,
                onPlay = { list, index -> onVideoClick(list.map { it.toVideoItem() }, index) },
                onMissing = viewModel::load
            )
        }
    }
}

/** Nivel 2: flecha atrás + "Inicio > Nombre de la carpeta" (también lo usa Música con su propia raíz). */
@Composable
fun Breadcrumb(
    name: String,
    onBack: () -> Unit,
    root: String = stringResource(R.string.lp_breadcrumb_root)
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
    }
}

@Composable
private fun VideoFoldersContent(
    state: LocalMediaUiState,
    onRetry: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit
) {
    val accents = MaterialTheme.accents
    val play = rememberSafePlay(onPlay, onMissing)
    val folder = state.currentFolder

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

            // Nivel 2: videos de la carpeta
            folder != null -> {
                val videos = state.folderVideos
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns()),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(videos) { index, media ->
                        LocalVideoCard(media = media, onClick = { play(videos, index) })
                    }
                }
            }

            // Nivel 1: carpetas
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(state.folders, key = { it.id }) { f ->
                    LocalFolderCard(folder = f, onClick = { onOpenFolder(f.id) })
                }
            }
        }
    }
}
