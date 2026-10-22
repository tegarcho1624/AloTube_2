package com.alotube.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.player.rememberNowPlaying
import com.alotube.app.ui.components.AudioFolderCard
import com.alotube.app.ui.components.AudioTrackRow
import com.alotube.app.ui.components.StatusMessage
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
    BackHandler(enabled = folder != null) { viewModel.closeAudioFolder() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (folder == null) {
            LocalHeader(
                title = stringResource(R.string.mp_title),
                showToggle = permission.granted,
                includeAll = state.includeAll,
                onToggle = { viewModel.setIncludeAll(!state.includeAll) }
            )
        } else {
            Breadcrumb(
                name = folder.name,
                onBack = viewModel::closeAudioFolder,
                root = stringResource(R.string.mp_root)
            )
        }

        if (!permission.granted) {
            PermissionContent(permission)
        } else {
            MusicContent(
                state = state,
                onRetry = viewModel::load,
                onOpenFolder = viewModel::openAudioFolder,
                onPlay = { list, index -> onVideoClick(list.map { it.toVideoItem() }, index) },
                onMissing = viewModel::load
            )
        }
    }
}

@Composable
private fun MusicContent(
    state: LocalMediaUiState,
    onRetry: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit
) {
    val accents = MaterialTheme.accents
    val play = rememberSafePlay(onPlay, onMissing)
    val now = rememberNowPlaying()
    val folder = state.currentAudioFolder

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

            // Nivel 2: canciones de la carpeta
            folder != null -> {
                val tracks = state.audioTracks(folder.id)
                val currentUrl = now.item?.url
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(tracks, key = { _, media -> media.id }) { index, media ->
                        AudioTrackRow(
                            media = media,
                            current = currentUrl != null && currentUrl == media.uri.toString(),
                            playing = now.isPlaying,
                            onClick = { play(tracks, index) }
                        )
                    }
                }
            }

            // Nivel 1: carpetas
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(state.audioFolders, key = { it.id }) { f ->
                    AudioFolderCard(
                        folder = f,
                        tracks = state.audioTracks(f.id),
                        onClick = { onOpenFolder(f.id) }
                    )
                }
            }
        }
    }
}
