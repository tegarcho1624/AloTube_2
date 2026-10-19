@file:OptIn(UnstableApi::class)

package com.alotube.app.ui.screens

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.player.applyAloStyle
import com.alotube.app.ui.theme.accents
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
}

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
    val controlsVisible = remember { mutableStateOf(true) }
    val error = state.error
    val cs = MaterialTheme.colorScheme
    val accents = MaterialTheme.accents
    val accentInt = accents.accent.toArgb()
    val accentHiInt = accents.hi.toArgb()
    val inkInt = cs.onBackground.toArgb()
    val mutedInt = cs.onSurfaceVariant.toArgb()
    val bgInt = cs.background.toArgb()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
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
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        applyAloStyle(accentInt, accentHiInt, inkInt, mutedInt, bgInt)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setFullscreenButtonClickListener(
                            PlayerView.FullscreenButtonClickListener { isFullScreen ->
                                onFullscreenToggle(isFullScreen)
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
                    Text(
                        text = state.title,
                        color = cs.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onEnterPip != null) {
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
}
