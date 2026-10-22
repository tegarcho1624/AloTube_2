@file:OptIn(UnstableApi::class)

package com.alotube.app.ui.screens

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alotube.app.R
import com.alotube.app.data.local.AudioInfo
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.player.isLocalAudioUrl
import com.alotube.app.player.applyAloStyle
import com.alotube.app.ui.components.ActionSpec
import com.alotube.app.ui.components.AudioCover
import com.alotube.app.ui.components.DownloadDialog
import com.alotube.app.ui.components.WatchInfo
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatCount
import com.alotube.app.util.formatViews
import com.alotube.app.viewmodel.PlayerError
import com.alotube.app.viewmodel.PlayerUiState

@StringRes
private fun PlayerError.messageRes(): Int = when (this) {
    PlayerError.NETWORK -> R.string.perr_network
    PlayerError.BLOCKED -> R.string.perr_blocked
    PlayerError.UNAVAILABLE -> R.string.perr_unavailable
    PlayerError.EXTRACTION -> R.string.perr_extraction
    PlayerError.NO_STREAMS -> R.string.perr_no_streams
    PlayerError.PLAYBACK -> R.string.perr_playback
    PlayerError.UNKNOWN -> R.string.perr_unknown
    PlayerError.LOCAL_FILE -> R.string.perr_local
}

/**
 * Pantalla de reproducción. Normal: video arriba (16:9, máx. 55 % de la altura) + información y
 * acciones debajo. Pantalla completa, PiP o error: el área de video ocupa todo.
 * El PlayerView es siempre el primer hijo de la misma Column: al cambiar de modo solo cambia su
 * tamaño, no se recrea (así no se pierde el estado del botón de pantalla completa).
 */
@Composable
fun PlayerScreen(
    exoPlayer: Player,
    state: PlayerUiState,
    inPip: Boolean,
    onBack: () -> Unit,
    onSelectQuality: (PlaybackOption) -> Unit,
    onRetry: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    onEnterPip: (() -> Unit)?
) {
    val ctx = LocalContext.current
    val controlsVisible = remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val error = state.error
    val cs = MaterialTheme.colorScheme
    val accents = MaterialTheme.accents
    val accentInt = accents.accent.toArgb()
    val accentHiInt = accents.hi.toArgb()
    val inkInt = cs.onBackground.toArgb()
    val mutedInt = cs.onSurfaceVariant.toArgb()
    val bgInt = cs.background.toArgb()

    // ---- Acciones (solo visuales, salvo Emergente y Compartir) ----
    fun soon() {
        Toast.makeText(ctx, R.string.soon_toast, Toast.LENGTH_SHORT).show()
    }

    val isLocal = state.current?.isLocal == true

    // Música local: título / artista / álbum de las etiquetas (MediaMetadataRetriever) y la carátula
    // en el centro. Solo cambia lo que se MUESTRA; la reproducción es la de siempre.
    val isLocalAudio = isLocalAudioUrl(state.url)
    val audioInfo by produceState<AudioInfo?>(initialValue = null, state.url) {
        value = if (isLocalAudio) {
            AudioMetadataRepository.load(ctx, Uri.parse(state.url), state.title)
        } else {
            null
        }
    }
    val bigCover by produceState<Bitmap?>(initialValue = null, state.url) {
        value = if (isLocalAudio) {
            AudioMetadataRepository.loadLargeCover(ctx, Uri.parse(state.url), state.title)
        } else {
            null
        }
    }

    fun share() {
        if (isLocal) {
            // Archivo del dispositivo: se comparte el archivo, no un texto
            try {
                val uri = Uri.parse(state.url)
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = ctx.contentResolver.getType(uri) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        null
                    )
                )
            } catch (_: Exception) {
                // Sin apps para compartir o archivo no accesible: no pasa nada
            }
            return
        }
        val text = listOf(state.title, state.url).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return
        try {
            ctx.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    null
                )
            )
        } catch (_: ActivityNotFoundException) {
            // Sin apps para compartir: no pasa nada
        }
    }

    val allActions = listOf(
        ActionSpec(R.drawable.ic_act_playlist_add, R.string.act_add, false) { soon() },
        ActionSpec(R.drawable.ic_act_headphones, R.string.act_background, false) { soon() },
        ActionSpec(R.drawable.ic_act_pip, R.string.act_popup, true) {
            if (onEnterPip != null) onEnterPip()
            else Toast.makeText(ctx, R.string.pip_unavailable, Toast.LENGTH_SHORT).show()
        },
        ActionSpec(R.drawable.ic_act_share, R.string.act_share, true) { share() },
        ActionSpec(R.drawable.ic_act_music, R.string.act_music, false) { soon() },
        ActionSpec(R.drawable.ic_act_download, R.string.act_download, true) {
            if (state.url.isNotBlank()) showDownload = true else soon()
        }
    )
    // En un archivo local solo tienen sentido Emergente y Compartir
    val actions = if (isLocal) {
        allActions.filter { it.icon == R.drawable.ic_act_pip || it.icon == R.drawable.ic_act_share }
    } else {
        allActions
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        val expanded = isFullscreen || inPip || error != null
        // Música: el área es más alta (cuadrada) para que quepa la carátula
        val videoHeight = if (isLocalAudio) {
            minOf(maxWidth, maxHeight * 0.6f)
        } else {
            minOf(maxWidth * 9f / 16f, maxHeight * 0.55f)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = if (expanded) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(videoHeight)
                        .background(Color.Black)
                }
            ) {
                if (error != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(error.messageRes()),
                            color = cs.onBackground,
                            textAlign = TextAlign.Center
                        )
                        // Detalle técnico para diagnosticar (intentos, excepción, cliente...)
                        if (!inPip && !state.errorDetail.isNullOrBlank()) {
                            Text(
                                text = state.errorDetail,
                                color = cs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        if (!inPip) {
                            Row(
                                modifier = Modifier.padding(top = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = onBack) { Text(stringResource(R.string.back)) }
                                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            PlayerView(c).apply {
                                player = exoPlayer
                                applyAloStyle(accentInt, accentHiInt, inkInt, mutedInt, bgInt)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                setFullscreenButtonClickListener(
                                    PlayerView.FullscreenButtonClickListener { enter ->
                                        isFullscreen = enter
                                        onFullscreenToggle(enter)
                                    }
                                )
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility ->
                                        controlsVisible.value = visibility == View.VISIBLE
                                    }
                                )
                            }
                        },
                        update = { it.useController = !inPip },
                        onRelease = { it.player = null }
                    )

                    // Carátula (o nota musical con el acento) en el centro, entre la barra superior y los controles
                    if (isLocalAudio && !inPip) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 88.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AudioCover(
                                bitmap = bigCover ?: audioInfo?.cover,
                                iconSize = 72.dp,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        }
                    }

                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = accents.accent
                        )
                    }

                    if (controlsVisible.value && !inPip) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                    )
                                )
                                .safeDrawingPadding()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = cs.onBackground
                                )
                            }
                            // El título ya se muestra debajo; en pantalla completa se ve aquí
                            Text(
                                text = if (isFullscreen) state.title else "",
                                color = cs.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (onEnterPip != null && isFullscreen) {
                                TextButton(onClick = onEnterPip) {
                                    Text(stringResource(R.string.pip), color = cs.onBackground)
                                }
                            }
                            if (state.options.size > 1) {
                                Box {
                                    TextButton(onClick = { menuOpen = true }) {
                                        Text(state.current?.label ?: "", color = accents.hi)
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        state.options.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (option == state.current) "✓ ${option.label}"
                                                        else option.label
                                                    )
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    onSelectQuality(option)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!expanded) {
                WatchInfo(
                    title = if (isLocalAudio) audioInfo?.title ?: state.title else state.title,
                    author = if (isLocalAudio) audioInfo?.artist.orEmpty() else state.author,
                    meta = if (isLocalAudio) {
                        audioInfo?.album.orEmpty()
                    } else {
                        listOfNotNull(formatViews(state.viewCount), state.uploadDate)
                            .joinToString(" · ")
                    },
                    likes = formatCount(state.likeCount),
                    actions = actions,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showDownload) {
            DownloadDialog(
                url = state.url,
                title = state.title,
                onDismiss = { showDownload = false }
            )
        }
    }
}
