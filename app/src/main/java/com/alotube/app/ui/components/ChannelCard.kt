package com.alotube.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alotube.app.R
import com.alotube.app.data.model.ChannelItem
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatSubscribers

/**
 * Tarjeta de canal: foto de perfil redonda, nombre en negrita, suscriptores y botón "Ver canal".
 * Por ahora tocarla (o el botón) solo avisa que la pantalla del canal llegará después.
 */
@Composable
fun ChannelCard(
    channel: ChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AloColors.Sf),
        border = BorderStroke(1.dp, AloColors.Ln),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AloColors.Sf3),
                contentAlignment = Alignment.Center
            ) {
                if (channel.avatarUrl != null) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Sin foto: inicial del canal
                    Text(
                        text = channel.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.accents.hi
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AloColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                formatSubscribers(channel.subscriberCount)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AloColors.Mut,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onClick) {
                Text(stringResource(R.string.channel_view))
            }
        }
    }
}

/** Cuadrícula de canales con scroll infinito (mismas columnas y espaciado que la de videos). */
@Composable
fun ChannelGrid(
    channels: List<ChannelItem>,
    onChannelClick: (ChannelItem) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(channels) { channel ->
            ChannelCard(channel = channel, onClick = { onChannelClick(channel) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingFooter() }
        }
    }
}
