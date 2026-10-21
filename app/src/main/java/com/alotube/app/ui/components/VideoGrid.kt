package com.alotube.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.theme.accents
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Columnas de las cuadrículas: 2 en tablet (>= 600 dp de ancho), 1 en teléfono. */
@Composable
fun gridColumns(): Int = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1

/** Llama a [onLoadMore] cuando el usuario se acerca al final de la cuadrícula (scroll infinito). */
@Composable
fun LoadMoreEffect(state: LazyGridState, buffer: Int = 4, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // El total va en el valor para que, al añadirse items y seguir cerca del final, se vuelva a pedir
            info.totalItemsCount to (info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - buffer)
        }
            .distinctUntilChanged()
            .filter { it.second }
            .collect { currentOnLoadMore() }
    }
}

/** Spinner de "cargando más" al pie de una cuadrícula. */
@Composable
fun LoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.accents.accent)
    }
}

/** Cuadrícula de videos; con [onLoadMore] activa el scroll infinito. */
@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false
) {
    val gridState = rememberLazyGridState()
    if (onLoadMore != null) LoadMoreEffect(gridState, onLoadMore = onLoadMore)

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns()),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(videos) { index, video ->
            VideoListItem(video = video, onClick = { onVideoClick(videos, index) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingFooter() }
        }
    }
}
