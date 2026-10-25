package com.alotube.app.ui.screens.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.local.FolderKind
import com.alotube.app.ui.screens.AudioFolderGrid
import com.alotube.app.ui.screens.EmptyMedia
import com.alotube.app.ui.screens.VideoFolderGrid
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LocalMediaViewModel

/** Ajustes > Archivados: dos secciones, "Música" y "Video Player". */
@Composable
fun ArchivedMenuScreen(onBack: () -> Unit, onMusic: () -> Unit, onVideo: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.fa_title), onBack)
        ArchivedEntry(stringResource(R.string.mp_title), onMusic)
        ArchivedEntry(stringResource(R.string.lp_title), onVideo)
    }
}

@Composable
private fun ArchivedEntry(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = AloColors.Ink,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    )
}

/**
 * Carpetas archivadas de Música o Video Player. Es la MISMA cuadrícula de las pestañas (AudioFolderGrid /
 * VideoFolderGrid) con la lista de archivadas: arrastrar para reordenar, ícono personalizado, contador, y el
 * menú de toque largo con "Desarchivar". Usa el ViewModel de la Activity (el mismo de las pestañas).
 */
@Composable
fun ArchivedFoldersScreen(kind: FolderKind, onBack: () -> Unit) {
    val activity = LocalContext.current.findComponentActivity()
    val viewModel: LocalMediaViewModel =
        if (activity != null) viewModel(viewModelStoreOwner = activity) else viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val music = kind == FolderKind.MUSIC
    val folders = if (music) state.archivedAudioFolders else state.archivedFolders

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(if (music) R.string.mp_title else R.string.lp_title), onBack)
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !state.loaded && state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.accents.accent
                )

                folders.isEmpty() -> EmptyMedia(
                    text = stringResource(R.string.fa_empty),
                    modifier = Modifier.align(Alignment.Center)
                )

                music -> AudioFolderGrid(
                    source = folders,
                    tracksOf = state::audioTracks,
                    onOpenFolder = {},
                    onOrder = { ids -> viewModel.setFolderOrder(FolderKind.MUSIC, ids) },
                    onArchive = { f -> viewModel.setArchived(FolderKind.MUSIC, f.id, false) },
                    archived = true
                )

                else -> VideoFolderGrid(
                    source = folders,
                    onOpenFolder = {},
                    onOrder = { ids -> viewModel.setFolderOrder(FolderKind.VIDEO, ids) },
                    onArchive = { f -> viewModel.setArchived(FolderKind.VIDEO, f.id, false) },
                    archived = true
                )
            }
        }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}
