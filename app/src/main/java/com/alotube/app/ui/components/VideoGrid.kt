package com.alotube.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.alotube.app.data.model.VideoItem

/** Cuadrícula de videos: 2 columnas en tablet (>= 600 dp de ancho), 1 en teléfono. */
@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(videos) { index, video ->
            VideoListItem(video = video, onClick = { onVideoClick(videos, index) })
        }
    }
}
