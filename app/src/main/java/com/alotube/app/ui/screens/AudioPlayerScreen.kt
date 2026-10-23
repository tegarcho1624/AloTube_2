package com.alotube.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player
import com.alotube.app.R
import com.alotube.app.player.PlaybackQueue
import com.alotube.app.ui.components.ActionSpec
import com.alotube.app.ui.components.AudioCover
import com.alotube.app.ui.components.VideoActionBar
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000L

/**
 * Pantalla de reproducción de MÚSICA local: carátula, título / artista / álbum, barra de progreso,
 * controles (anterior, -10 s, play/pausa, +10 s, siguiente) y, más abajo, Emergente / Compartir.
 * Solo es interfaz: todo se hace con los métodos normales del [Player] (la lógica no cambia).
 * Se desplaza si no cabe en la pantalla.
 */
@Composable
fun AudioPlayerContent(
    player: Player,
    title: String,
    artist: String,
    album: String?,
    cover: Bitmap?,
    isLoading: Boolean,
    actions: List<ActionSpec>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = MaterialTheme.accents
    val ctx = LocalContext.current
    val queue by PlaybackQueue.state.collectAsState()

    var playing by remember { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    // Lectura periódica del estado (igual que el mini-reproductor); no toca la reproducción
    LaunchedEffect(player) {
        while (true) {
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
            durationMs = player.duration.coerceAtLeast(0L)
            if (!dragging) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (dragging) (dragFraction * durationMs).toLong() else positionMs

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // ---- Carátula ----
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = min(maxWidth, 340.dp)
            Box(contentAlignment = Alignment.Center) {
                AudioCover(
                    bitmap = cover,
                    iconSize = 88.dp,
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(24.dp))
                )
                if (isLoading) {
                    CircularProgressIndicator(color = accents.accent)
                }
            }
        }

        // ---- Título, artista, álbum ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AloColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = accents.hi,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!album.isNullOrBlank()) {
                Text(
                    text = album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AloColors.Mut,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // ---- Barra de progreso ----
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Slider(
                value = if (dragging) dragFraction else fraction,
                onValueChange = {
                    dragging = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    if (durationMs > 0) {
                        val target = (dragFraction * durationMs).toLong()
                        player.seekTo(target)
                        positionMs = target
                    }
                    dragging = false
                },
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = accents.accent,
                    activeTrackColor = accents.accent,
                    inactiveTrackColor = AloColors.Sf3,
                    disabledThumbColor = AloColors.Mut,
                    disabledActiveTrackColor = AloColors.Sf3,
                    disabledInactiveTrackColor = AloColors.Sf3
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(shownMs / 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = AloColors.Mut
                )
                Text(
                    text = formatDuration(durationMs / 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = AloColors.Mut
                )
            }
        }

        // ---- Controles ----
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ControlButton(
                icon = R.drawable.ic_audio_more,
                description = R.string.mp_more,
                enabled = true,
                size = 44.dp,
                iconSize = 24.dp
            ) {
                Toast.makeText(ctx, R.string.mp_more_soon, Toast.LENGTH_SHORT).show()
            }
            ControlButton(
                icon = R.drawable.ic_audio_back10,
                description = R.string.mp_back10,
                enabled = durationMs > 0,
                size = 48.dp,
                iconSize = 26.dp
            ) {
                player.seekTo((player.currentPosition - SKIP_MS).coerceAtLeast(0L))
            }
            ControlButton(
                icon = R.drawable.ic_mini_prev,
                description = R.string.mp_prev,
                enabled = true,
                size = 56.dp,
                iconSize = 30.dp
            ) {
                player.seekToPrevious()
            }
            // Play / Pausa grande, con el acento dinámico
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accents.accent)
                    .clickable {
                        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (playing) R.drawable.ic_mini_pause else R.drawable.ic_mini_play),
                    contentDescription = stringResource(if (playing) R.string.mp_pause else R.string.mp_play),
                    tint = AloColors.Bg,
                    modifier = Modifier.size(38.dp)
                )
            }
            ControlButton(
                icon = R.drawable.ic_mini_next,
                description = R.string.mp_next,
                enabled = queue.hasNext,
                size = 56.dp,
                iconSize = 30.dp
            ) {
                player.seekToNext()
            }
            ControlButton(
                icon = R.drawable.ic_audio_fwd10,
                description = R.string.mp_fwd10,
                enabled = durationMs > 0,
                size = 48.dp,
                iconSize = 26.dp
            ) {
                val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
                player.seekTo((player.currentPosition + SKIP_MS).coerceAtMost(end))
            }
            ControlButton(
                icon = R.drawable.ic_audio_queue,
                description = R.string.mp_queue,
                enabled = true,
                size = 44.dp,
                iconSize = 24.dp
            ) {
                Toast.makeText(ctx, R.string.mp_queue_soon, Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Emergente / Compartir, más abajo para no estorbar a los controles ----
        Spacer(Modifier.height(20.dp))
        VideoActionBar(
            actions = actions,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val accents = MaterialTheme.accents
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = if (enabled) accents.hi else AloColors.Mut.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize)
        )
    }
}
