package com.alotube.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.util.formatDuration
import com.alotube.app.util.formatViews

/**
 * Tarjeta de video: miniatura redondeada con la duración en un recuadro oscuro (abajo a la derecha),
 * título (máx. 2 líneas), canal y metadatos (vistas · fecha) en gris y más pequeño.
 * El espaciado entre tarjetas y con los bordes lo pone la cuadrícula (VideoGrid).
 */
@Composable
fun VideoListItem(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = AloColors.Sf3),
            border = BorderStroke(1.dp, AloColors.Ln),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = badge,
                            color = if (video.isLive) AloColors.Bg else AloColors.Ink,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (video.author.isNotBlank()) {
            Text(
                text = video.author,
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        val meta = listOfNotNull(formatViews(video.viewCount), video.uploadDateText)
            .joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
