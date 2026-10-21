package com.alotube.app.ui.screens

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.AloTubeHeader
import com.alotube.app.ui.components.ChannelGrid
import com.alotube.app.ui.components.SearchTopBar
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.VideoGrid
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.YoutubeLinks
import com.alotube.app.viewmodel.LoadStatus
import com.alotube.app.viewmodel.SearchError
import com.alotube.app.viewmodel.SearchUiState
import com.alotube.app.viewmodel.SearchViewModel

@StringRes
private fun SearchError.messageRes(): Int = when (this) {
    SearchError.NETWORK -> R.string.error_network
    SearchError.BLOCKED -> R.string.error_blocked
    SearchError.EXTRACTION -> R.string.error_extraction
    SearchError.UNKNOWN -> R.string.error_unknown
    SearchError.INVALID_LINK -> R.string.error_link
}

/** Pestaña "Inicio": logo, buscador y resultados (pestañas Videos / Canales). */
@Composable
fun MainScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val accents = MaterialTheme.accents

    // Copias locales para poder hacer smart-cast
    val error = state.error
    val results = state.results

    // Si el texto era un enlace, el ViewModel pide abrir ese video directamente en el reproductor
    val currentOnVideoClick by rememberUpdatedState(onVideoClick)
    LaunchedEffect(Unit) {
        viewModel.openVideo.collect { video -> currentOnVideoClick(listOf(video), 0) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AloTubeHeader()
        SearchTopBar(
            query = state.query,
            isLink = YoutubeLinks.containsLink(state.query),
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                focusManager.clearFocus()
                viewModel.search()
            },
            onClear = viewModel::clearQuery
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accents.accent
                )

                error != null -> StatusMessage(
                    text = stringResource(error.messageRes()),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::search,
                    modifier = Modifier.align(Alignment.Center)
                )

                !state.hasSearched -> StatusMessage(
                    text = stringResource(R.string.hint_start),
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = state.tab,
                        containerColor = AloColors.Bg,
                        contentColor = accents.hi,
                        divider = { HorizontalDivider(color = AloColors.Ln) }
                    ) {
                        Tab(
                            selected = state.tab == 0,
                            onClick = { viewModel.selectTab(0) },
                            selectedContentColor = accents.hi,
                            unselectedContentColor = AloColors.Mut,
                            text = { Text(stringResource(R.string.search_tab_videos)) }
                        )
                        Tab(
                            selected = state.tab == 1,
                            onClick = { viewModel.selectTab(1) },
                            selectedContentColor = accents.hi,
                            unselectedContentColor = AloColors.Mut,
                            text = { Text(stringResource(R.string.search_tab_channels)) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (state.tab == 0) {
                            if (results.isEmpty()) {
                                StatusMessage(
                                    text = stringResource(R.string.no_results, state.lastQuery),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                VideoGrid(
                                    videos = results,
                                    onVideoClick = onVideoClick,
                                    onLoadMore = viewModel::loadMoreVideos,
                                    loadingMore = state.loadingMoreVideos
                                )
                            }
                        } else {
                            ChannelsTab(
                                state = state,
                                onLoadMore = viewModel::loadMoreChannels,
                                onChannelClick = { channel ->
                                    // La pantalla del canal llega en la siguiente fase
                                    Toast.makeText(
                                        ctx,
                                        ctx.getString(R.string.channel_open_soon, channel.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Contenido de la pestaña "Canales": cargando / no disponible / vacío / cuadrícula. */
@Composable
private fun ChannelsTab(
    state: SearchUiState,
    onLoadMore: () -> Unit,
    onChannelClick: (com.alotube.app.data.model.ChannelItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state.channelsStatus) {
            LoadStatus.IDLE, LoadStatus.LOADING -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.accents.accent
            )

            LoadStatus.UNAVAILABLE -> StatusMessage(
                text = stringResource(R.string.search_channels_error),
                modifier = Modifier.align(Alignment.Center)
            )

            LoadStatus.READY ->
                if (state.channels.isEmpty()) {
                    StatusMessage(
                        text = stringResource(R.string.search_no_channels),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    ChannelGrid(
                        channels = state.channels,
                        onChannelClick = onChannelClick,
                        onLoadMore = onLoadMore,
                        loadingMore = state.loadingMoreChannels
                    )
                }
        }
    }
}
