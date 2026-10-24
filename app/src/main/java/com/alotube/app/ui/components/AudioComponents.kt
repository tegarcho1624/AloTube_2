package com.alotube.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.data.local.AudioInfo
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration

/**
 * Carátula: la imagen del audio (recortada al cuadrado) o, si no tiene, una nota musical con el
 * color de acento sobre un fondo suave del acento.
 */
@Composable
fun AudioCover(bitmap: Bitmap?, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
    val accents = MaterialTheme.accents
    Box(
        modifier = modifier.background(AloColors.Sf3),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accents.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_act_music),
                    contentDescription = null,
                    tint = accents.hi,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/** Ecualizador de 3 barras: se anima mientras suena y queda quieto en pausa. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    val c by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "c"
    )
    Canvas(modifier = modifier) {
        val barWidth = size.width / 5f
        val heights = if (playing) listOf(a, b, c) else listOf(0.4f, 0.7f, 0.5f)
        heights.forEachIndexed { i, fraction ->
            val barHeight = size.height * fraction
            drawRect(
                color = color,
                topLeft = Offset(barWidth * 2f * i, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

/**
 * Fila de canción: carátula pequeña, título en negrita, artista en gris y duración a la derecha.
 * Las etiquetas se leen en segundo plano: mientras llegan se ve el nombre del archivo.
 * La canción actual se ilumina con el acento y muestra el ecualizador sobre la carátula.
 */
@Composable
fun AudioTrackRow(
    media: LocalMedia,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val accents = MaterialTheme.accents
    val tagsVersion by AudioMetadataRepository.version.collectAsState()
    val info by produceState<AudioInfo?>(initialValue = null, media.uri, tagsVersion) {
        value = AudioMetadataRepository.load(ctx, media.uri, media.title)
    }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) accents.bg else AloColors.Sf)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AudioCover(
                bitmap = info?.cover,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
            if (current) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AloColors.Scrim),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerBars(playing = playing, color = accents.hi)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info?.title ?: media.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (current) accents.hi else AloColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = AloColors.Mut,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (media.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatDuration(media.durationMs / 1000),
                style = MaterialTheme.typography.labelMedium,
                color = if (current) accents.hi else AloColors.Mut
            )
        }
    }
}

/**
 * Tarjeta de carpeta de audios: carátula del primer audio que la tenga (se buscan entre los
 * primeros de [tracks]); si ninguno tiene, nota musical con el acento. Nombre y cantidad debajo.
 */
@Composable
fun AudioFolderCard(
    folder: LocalFolder,
    tracks: List<LocalMedia>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ícono personalizado de la carpeta (FolderIconStore); si es null se ve la carátula / nota musical de siempre. */
    icon: ImageBitmap? = null
) {
    val ctx = LocalContext.current
    val cover by produceState<Bitmap?>(initialValue = null, folder.id, tracks, AudioMetadataRepository.version.collectAsState().value) {
        value = AudioMetadataRepository.firstCover(ctx, tracks)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        if (icon != null) {
            // Recuadro cuadrado de la carpeta; la imagen se estira para llenarlo por completo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            AudioCover(
                bitmap = cover,
                iconSize = 56.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = if (folder.count == 1) {
                stringResource(R.string.mp_songs_one)
            } else {
                stringResource(R.string.mp_songs_many, folder.count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = AloColors.Mut
        )
    }
}
