package com.alotube.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alotube.app.LoginActivity
import com.alotube.app.R
import com.alotube.app.data.library.LibraryChannel
import com.alotube.app.data.library.LibraryPlaylist
import com.alotube.app.data.library.LibraryRepository
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.VideoListItem
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LibraryViewModel
import com.alotube.app.viewmodel.LoadStatus
import com.alotube.app.viewmodel.PlaylistDetail
import com.alotube.app.viewmodel.SectionState

/** Pestaña "Biblioteca": datos de la cuenta de Google (listas y suscripciones). */
@Composable
fun LibraryScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // El estado se refresca solo cuando LoginActivity guarda la sesión (StateFlow)
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val detail = state.detail
    if (detail != null) {
        BackHandler { viewModel.closeDetail() }
        PlaylistDetailScreen(
            detail = detail,
            onBack = viewModel::closeDetail,
            onRetry = viewModel::retryDetail,
            onVideoClick = onVideoClick
        )
        return
    }

    val watchLater = LibraryPlaylist(LibraryRepository.WATCH_LATER, stringResource(R.string.lib_watch_later))
    val liked = LibraryPlaylist(LibraryRepository.LIKED, stringResource(R.string.lib_liked))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.lib_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.accents.hi
                )
                if (state.loggedIn) {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.lib_refresh))
                    }
                }
            }
        }

        if (!state.loggedIn) {
            item {
                StatusMessage(
                    text = stringResource(R.string.lib_login_hint),
                    actionLabel = stringResource(R.string.account_login),
                    onAction = { loginLauncher.launch(Intent(ctx, LoginActivity::class.java)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }
            return@LazyColumn
        }

        // "Ver después" y "Vídeos que me han gustado" (listas del sistema de YouTube)
        item { Spacer(Modifier.height(8.dp)) }
        item {
            LibraryRow(
                title = watchLater.title,
                iconRes = R.drawable.ic_lib_clock,
                onClick = { viewModel.openPlaylist(watchLater) }
            )
        }
        item {
            LibraryRow(
                title = liked.title,
                iconRes = R.drawable.ic_lib_like,
                onClick = { viewModel.openPlaylist(liked) }
            )
        }

        item { SectionTitle(R.string.lib_playlists) }
        sectionContent(state.playlists, R.string.lib_no_playlists) { playlist ->
            LibraryRow(
                title = playlist.title,
                subtitle = playlist.subtitle,
                imageUrl = playlist.thumbnailUrl,
                iconRes = if (playlist.thumbnailUrl == null) R.drawable.ic_lib_playlist else null,
                onClick = { viewModel.openPlaylist(playlist) }
            )
        }

        item { SectionTitle(R.string.lib_subs) }
        sectionContent(state.subscriptions, R.string.lib_no_subs) { channel: LibraryChannel ->
            LibraryRow(
                title = channel.name,
                subtitle = channel.subtitle,
                imageUrl = channel.avatarUrl,
                circle = true
            )
        }
    }
}

// ---------- Detalle de una lista ----------

@Composable
private fun PlaylistDetailScreen(
    detail: PlaylistDetail,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (List<VideoItem>, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.lib_back)
                )
            }
            Text(
                text = detail.playlist.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.accents.hi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val videos = detail.videos
        Box(modifier = Modifier.fillMaxSize()) {
            when (videos.status) {
                LoadStatus.IDLE, LoadStatus.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.accents.accent
                )

                LoadStatus.UNAVAILABLE -> StatusMessage(
                    text = stringResource(
                        if (videos.expired) R.string.lib_expired else R.string.lib_unavailable
                    ) + (videos.note?.let { "\n\n$it" } ?: ""),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    modifier = Modifier.align(Alignment.Center)
                )

                LoadStatus.READY ->
                    if (videos.items.isEmpty()) {
                        StatusMessage(
                            text = stringResource(R.string.lib_playlist_empty),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(videos.items) { index, video ->
                                VideoListItem(
                                    video = video,
                                    onClick = { onVideoClick(videos.items, index) }
                                )
                            }
                        }
                    }
            }
        }
    }
}

// ---------- Piezas ----------

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = AloColors.Ink,
        modifier = Modifier.padding(top = 28.dp, bottom = 6.dp)
    )
}

/** Carga / error ("No disponible") / vacío / filas. Cada sección falla por separado. */
private fun <T> LazyListScope.sectionContent(
    section: SectionState<T>,
    @StringRes emptyRes: Int,
    row: @Composable (T) -> Unit
) {
    when (section.status) {
        LoadStatus.IDLE -> Unit

        LoadStatus.LOADING -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.accents.accent)
            }
        }

        LoadStatus.UNAVAILABLE -> item {
            Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                Text(
                    text = stringResource(if (section.expired) R.string.lib_expired else R.string.lib_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AloColors.Mut
                )
                section.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AloColors.Mut.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        LoadStatus.READY ->
            if (section.items.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                        Text(
                            text = stringResource(emptyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AloColors.Mut
                        )
                        section.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = AloColors.Mut.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(section.items) { _, item -> row(item) }
            }
    }
}

/** Fila estilo captura: ícono o miniatura a la izquierda, título en blanco y subtítulo en gris. */
@Composable
private fun LibraryRow(
    title: String,
    subtitle: String? = null,
    iconRes: Int? = null,
    imageUrl: String? = null,
    circle: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = if (circle) CircleShape else RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(if (imageUrl != null || iconRes == null) AloColors.Sf3 else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = AloColors.Ink,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AloColors.Mut,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
