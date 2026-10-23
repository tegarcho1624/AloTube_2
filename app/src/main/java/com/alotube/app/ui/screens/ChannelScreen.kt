package com.alotube.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alotube.app.R
import com.alotube.app.data.model.ChannelDetails
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.LoadingFooter
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration
import com.alotube.app.util.formatSubscribers
import com.alotube.app.util.formatViews
import com.alotube.app.viewmodel.ChannelUiState
import com.alotube.app.viewmodel.ChannelViewModel
import com.alotube.app.viewmodel.LoadStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Pantalla de canal (se muestra encima de la navegación principal).
 * Barra superior con flecha y nombre, pestañas, banner, perfil, acciones y lista de videos.
 */
@Composable
fun ChannelScreen(
    channelUrl: String,
    initialName: String,
    onBack: () -> Unit,
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: ChannelViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(channelUrl) { viewModel.load(channelUrl) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val title = state.details?.name?.takeIf { it.isNotBlank() } ?: initialName

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Esta pantalla va encima de la otra: se evita que los toques "atraviesen"
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- Barra superior ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = AloColors.Ink
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AloColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.accents.accent)
            }

            state.error != null -> ChannelError(
                detail = state.error.orEmpty(),
                onBack = onBack,
                onRetry = viewModel::retry
            )

            else -> ChannelContent(
                state = state,
                initialName = initialName,
                tab = tab,
                onTabChange = { tab = it },
                onLoadMore = viewModel::loadMore,
                onVideoClick = onVideoClick
            )
        }
    }
}

@Composable
private fun ChannelError(detail: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.ch_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(
            modifier = Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ChannelContent(
    state: ChannelUiState,
    initialName: String,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (List<VideoItem>, Int) -> Unit
) {
    val ctx = LocalContext.current
    val accents = MaterialTheme.accents
    val listState = rememberLazyListState()
    LoadMoreListEffect(listState, onLoadMore)

    val details = state.details ?: ChannelDetails(initialName, null, null, -1L, null)
    fun soon() = Toast.makeText(ctx, R.string.soon_toast, Toast.LENGTH_SHORT).show()

    // ---- Pestañas: Inicio, Videos, Shorts, Listas ----
    val tabLabels = listOf(
        R.string.ch_tab_home, R.string.ch_tab_videos, R.string.ch_tab_shorts, R.string.ch_tab_lists
    )
    TabRow(
        selectedTabIndex = tab,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = accents.hi,
        divider = { HorizontalDivider(color = AloColors.Ln) }
    ) {
        tabLabels.forEachIndexed { index, label ->
            Tab(
                selected = tab == index,
                onClick = { onTabChange(index) },
                selectedContentColor = accents.hi,
                unselectedContentColor = AloColors.Mut,
                text = { Text(stringResource(label)) }
            )
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item { ChannelBanner(details.bannerUrl) }
        item { ChannelInfo(details, onSubscribe = ::soon) }
        item {
            ChannelActions(
                enabled = state.videos.isNotEmpty(),
                onBackground = ::soon,
                // "Reproducir todo": cola con los videos cargados, desde el primero
                onPlayAll = { onVideoClick(state.videos, 0) },
                // "Aleatorio": los mismos videos en orden aleatorio
                onShuffle = { onVideoClick(state.videos.shuffled(), 0) }
            )
        }

        if (tab > 1) {
            // Shorts y Listas llegan en una fase futura
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.set_soon),
                        style = MaterialTheme.typography.titleMedium,
                        color = accents.hi
                    )
                }
            }
        } else {
            when (state.videosStatus) {
                LoadStatus.UNAVAILABLE -> item {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.ch_videos_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AloColors.Mut
                        )
                        state.videosNote?.let {
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
                    if (state.videos.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.ch_no_videos),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AloColors.Mut,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        itemsIndexed(state.videos) { index, video ->
                            ChannelVideoRow(
                                video = video,
                                onClick = { onVideoClick(state.videos, index) }
                            )
                        }
                        if (state.loadingMore) item { LoadingFooter() }
                    }

                else -> item { LoadingFooter() }
            }
        }
    }
}

// ---------- Encabezado ----------

@Composable
private fun ChannelBanner(bannerUrl: String?) {
    // Altura proporcional al ancho (banners muy anchos), entre 80 y 200 dp
    val height = (LocalConfiguration.current.screenWidthDp / 6).coerceIn(80, 200).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(AloColors.Sf2) // sin banner: color oscuro
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ChannelInfo(details: ChannelDetails, onSubscribe: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Foto de perfil redonda, superpuesta al banner
        Box(
            modifier = Modifier
                .offset(y = (-28).dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(AloColors.Sf3),
            contentAlignment = Alignment.Center
        ) {
            if (details.avatarUrl != null) {
                AsyncImage(
                    model = details.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = details.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.accents.hi
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = details.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = AloColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            formatSubscribers(details.subscriberCount)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AloColors.Ink,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            details.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AloColors.Mut,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { expanded = !expanded }
                )
                if (!expanded && description.length > 90) {
                    Text(
                        text = stringResource(R.string.ch_see_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.accents.hi
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))
        // Solo visual: aún no está conectado a la cuenta
        Button(onClick = onSubscribe) { Text(stringResource(R.string.ch_subscribe)) }
    }
}

/** Fila "Fondo | Reproducir todo | Aleatorio", con separadores como en la referencia. */
@Composable
private fun ChannelActions(
    enabled: Boolean,
    onBackground: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionCell(R.drawable.ic_act_headphones, R.string.ch_background, true, onBackground, Modifier.weight(1f))
        VerticalSeparator()
        ActionCell(null, R.string.ch_play_all, enabled, onPlayAll, Modifier.weight(1f))
        VerticalSeparator()
        ActionCell(R.drawable.ic_act_shuffle, R.string.ch_shuffle, enabled, onShuffle, Modifier.weight(1f))
    }
}

@Composable
private fun VerticalSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(AloColors.Ln2)
    )
}

@Composable
private fun ActionCell(
    icon: Int?,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (enabled) AloColors.Ink else AloColors.Mut.copy(alpha = 0.5f)
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.accents.hi else color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

// ---------- Lista de videos ----------

/** Fila: miniatura a la izquierda; título y "vistas · fecha" a la derecha. */
@Composable
private fun ChannelVideoRow(video: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AloColors.Sf3)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = when {
                video.isLive -> stringResource(R.string.live)
                video.durationSeconds > 0 -> formatDuration(video.durationSeconds)
                else -> null
            }
            if (badge != null) {
                Surface(
                    color = if (video.isLive) AloColors.Danger else AloColors.Scrim,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (video.isLive) AloColors.Bg else AloColors.Ink,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(formatViews(video.viewCount), video.uploadDateText)
                .joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = AloColors.Mut,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Scroll infinito para la lista del canal. */
@Composable
private fun LoadMoreListEffect(state: LazyListState, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount to (info.totalItemsCount > 0 && last >= info.totalItemsCount - 5)
        }
            .distinctUntilChanged()
            .filter { it.second }
            .collect { currentOnLoadMore() }
    }
}
