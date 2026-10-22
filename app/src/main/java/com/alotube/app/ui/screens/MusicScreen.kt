package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.LocalAudioRow
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.gridColumns
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LocalMediaViewModel

/**
 * Pestaña "Música": audios locales del dispositivo (por defecto solo Descargas), con carátula,
 * nombre y duración. Comparte LocalMediaViewModel con "Video Player" (misma lectura de MediaStore).
 * Al tocar un audio se abre el mismo reproductor de AloTube con el content:// URI.
 */
@Composable
fun MusicScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: LocalMediaViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberMediaPermission()
    val accents = MaterialTheme.accents

    LaunchedEffect(permission.granted) { if (permission.granted) viewModel.load() }

    val play = rememberSafePlay(
        onPlay = { list, index -> onVideoClick(list.map { it.toVideoItem() }, index) },
        onMissing = viewModel::load
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LocalHeader(
            title = stringResource(R.string.mp_title),
            showToggle = permission.granted,
            includeAll = state.includeAll,
            onToggle = { viewModel.setIncludeAll(!state.includeAll) }
        )

        if (!permission.granted) {
            PermissionContent(permission)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !state.loaded && state.loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = accents.accent
                    )

                    !state.loaded && state.error != null -> StatusMessage(
                        text = stringResource(R.string.lp_error) + "\n\n" + state.error,
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::load,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    state.audios.isEmpty() -> EmptyMedia(
                        text = stringResource(R.string.lp_empty_audios),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns()),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.audios) { index, media ->
                            LocalAudioRow(media = media, onClick = { play(state.audios, index) })
                        }
                    }
                }
            }
        }
    }
}
