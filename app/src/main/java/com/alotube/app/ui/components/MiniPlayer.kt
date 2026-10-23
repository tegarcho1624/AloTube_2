package com.alotube.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.data.local.AudioInfo
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.data.model.VideoItem
import com.alotube.app.player.LocalAudioController
import com.alotube.app.player.PlaybackQueue
import com.alotube.app.player.rememberNowPlaying
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/**
 * Mini-reproductor: aparece justo sobre la barra de navegación mientras suena (o está en pausa) una
 * música local. Tocar la barra abre el reproductor a pantalla completa con la misma cola.
 * [onOpen] recibe la cola y el índice actuales (igual que al tocar una canción en una lista).
 */
@Composable
fun MiniPlayerHost(onOpen: (List<VideoItem>, Int) -> Unit) {
    val ctx = LocalContext.current
    val now = rememberNowPlaying()
    val queue by PlaybackQueue.state.collectAsState()
    val item = now.item ?: return

    MiniPlayer(
        item = item,
        isPlaying = now.isPlaying,
        hasNext = now.hasNext,
        onOpen = { onOpen(queue.items, queue.index) },
        onToggle = { LocalAudioController.togglePlayPause() },
        onPrevious = { LocalAudioController.previous(ctx) },
        onNext = { LocalAudioController.next(ctx) }
    )
}

@Composable
private fun MiniPlayer(
    item: VideoItem,
    isPlaying: Boolean,
    hasNext: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    val accents = MaterialTheme.accents
    val info by produceState<AudioInfo?>(initialValue = null, item.url) {
        value = AudioMetadataRepository.load(ctx, Uri.parse(item.url), item.title)
    }

    Column {
        HorizontalDivider(color = AloColors.Ln)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AloColors.Sf2)
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioCover(
                bitmap = info?.cover,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
                iconSize = 24.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info?.title ?: item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AloColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = info?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AloColors.Mut,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(R.drawable.ic_mini_prev),
                    contentDescription = stringResource(R.string.mp_prev),
                    tint = AloColors.Ink,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_mini_pause else R.drawable.ic_mini_play),
                    contentDescription = stringResource(if (isPlaying) R.string.mp_pause else R.string.mp_play),
                    tint = accents.accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    painter = painterResource(R.drawable.ic_mini_next),
                    contentDescription = stringResource(R.string.mp_next),
                    tint = if (hasNext) AloColors.Ink else AloColors.Mut.copy(alpha = 0.4f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
