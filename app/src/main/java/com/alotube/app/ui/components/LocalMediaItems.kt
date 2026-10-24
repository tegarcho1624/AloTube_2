package com.alotube.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.LocalThumbnails
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration
import com.alotube.app.util.formatFileSize

/** Miniatura de un archivo local; si no tiene (o está dañado) muestra un ícono. */
@Composable
fun LocalThumbnail(
    uri: Uri,
    @DrawableRes placeholder: Int,
    modifier: Modifier = Modifier,
    width: Int = 480,
    height: Int = 270
) {
    val ctx = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = LocalThumbnails.load(ctx, uri, width, height)
    }
    Box(
        modifier = modifier.background(AloColors.Sf3),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(placeholder),
                contentDescription = null,
                tint = MaterialTheme.accents.hi.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** Tarjeta de video local: miniatura con duración, nombre y tamaño (mismo estilo que los videos de YouTube). */
@Composable
fun LocalVideoCard(media: LocalMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AloColors.Sf3),
            border = BorderStroke(1.dp, AloColors.Ln),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LocalThumbnail(
                    uri = media.uri,
                    placeholder = R.drawable.ic_nav_player,
                    modifier = Modifier.fillMaxSize()
                )
                if (media.durationMs > 0) {
                    Surface(
                        color = AloColors.Scrim,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatDuration(media.durationMs / 1000),
                            color = AloColors.Ink,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = media.title,
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = formatFileSize(media.size),
            style = MaterialTheme.typography.bodySmall,
            color = AloColors.Mut,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Fila de audio local: carátula (o ícono de música), nombre, tamaño y duración. */
@Composable
fun LocalAudioRow(media: LocalMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AloColors.Sf)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocalThumbnail(
            uri = media.uri,
            placeholder = R.drawable.ic_nav_music,
            width = 192,
            height = 192,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(media.size),
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (media.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatDuration(media.durationMs / 1000),
                style = MaterialTheme.typography.labelMedium,
                color = AloColors.Mut
            )
        }
    }
}

/**
 * Tarjeta de carpeta de videos: miniatura del video más reciente atenuada, ícono de carpeta con el
 * acento dinámico, nombre y cantidad de videos.
 */
@Composable
fun LocalFolderCard(
    folder: LocalFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ícono personalizado de la carpeta (FolderIconStore); si es null se ve la miniatura + ícono de siempre. */
    icon: ImageBitmap? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AloColors.Sf3),
            border = BorderStroke(1.dp, AloColors.Ln),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (icon != null) {
                    // Ícono personalizado: solo la imagen, estirada para llenar el 100 % de la tarjeta
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LocalThumbnail(
                        uri = folder.cover.uri,
                        placeholder = R.drawable.ic_folder,
                        width = 320,
                        height = 240,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Velo para que la miniatura quede de fondo y el ícono se lea bien
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AloColors.Scrim)
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        tint = MaterialTheme.accents.hi,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    )
                }
            }
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
                stringResource(R.string.lp_folder_one)
            } else {
                stringResource(R.string.lp_folder_many, folder.count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = AloColors.Mut
        )
    }
}
