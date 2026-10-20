package com.alotube.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.download.DownloadService
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.DownloadMode
import com.alotube.app.viewmodel.DownloadViewModel

/**
 * Diálogo de descarga: nombre del archivo, Video/Audio, formato y calidad, idioma de audio.
 * Las listas se llenan con los formatos reales del video (StreamInfo).
 */
@Composable
fun DownloadDialog(
    url: String,
    title: String,
    onDismiss: () -> Unit,
    viewModel: DownloadViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accents = MaterialTheme.accents

    // Cada vez que se abre se piden los formatos del video actual
    LaunchedEffect(url) { viewModel.open(url, title) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AloColors.Sheet,
            border = BorderStroke(1.dp, AloColors.Ln2),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.dl_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accents.hi
                )
                Spacer(Modifier.height(16.dp))

                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accents.accent)
                    }

                    state.error != null -> {
                        Text(
                            text = stringResource(R.string.dl_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AloColors.Mut,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = { viewModel.open(url, title) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(stringResource(R.string.retry)) }
                    }

                    else -> {
                        val options = state.options
                        Text(
                            text = stringResource(R.string.dl_file_name),
                            style = MaterialTheme.typography.bodySmall,
                            color = AloColors.Mut
                        )
                        OutlinedTextField(
                            value = state.fileName,
                            onValueChange = viewModel::setFileName,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = aloTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )

                        // Video / Audio
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModeOption(
                                label = stringResource(R.string.dl_video),
                                selected = state.mode == DownloadMode.VIDEO,
                                enabled = state.hasVideo,
                                onClick = { viewModel.setMode(DownloadMode.VIDEO) }
                            )
                            ModeOption(
                                label = stringResource(R.string.dl_audio),
                                selected = state.mode == DownloadMode.AUDIO,
                                enabled = state.hasAudio,
                                onClick = { viewModel.setMode(DownloadMode.AUDIO) }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        if (state.mode == DownloadMode.VIDEO) {
                            // Solo videos que ya traen audio incluido (sin FFmpeg)
                            val videos = options?.videoChoices.orEmpty()
                            DropdownField(
                                label = stringResource(R.string.dl_format),
                                options = videos.map { it.label },
                                selectedIndex = state.videoIndex,
                                onSelect = viewModel::selectVideo
                            )
                        } else {
                            val tracks = options?.audioTracks.orEmpty()
                            // El idioma solo se muestra si el video tiene varias pistas
                            if (tracks.size > 1) {
                                DropdownField(
                                    label = stringResource(R.string.dl_language),
                                    options = tracks.map { it.label },
                                    selectedIndex = state.trackIndex,
                                    onSelect = viewModel::selectTrack
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            val streams = tracks.getOrNull(state.trackIndex)?.streams.orEmpty()
                            DropdownField(
                                label = stringResource(R.string.dl_format),
                                options = streams.map { it.label },
                                selectedIndex = state.audioIndex,
                                onSelect = viewModel::selectAudio
                            )
                        }

                        if (!state.hasVideo) {
                            Text(
                                text = stringResource(R.string.dl_no_video),
                                style = MaterialTheme.typography.bodySmall,
                                color = AloColors.Mut,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dl_cancel), color = AloColors.Mut)
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Button(
                        enabled = !state.loading && state.error == null && state.selectedChoice != null,
                        onClick = {
                            val request = viewModel.buildRequest()
                            if (request != null) {
                                DownloadService.enqueue(ctx, request)
                                Toast.makeText(ctx, R.string.dl_started, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    ) { Text(stringResource(R.string.dl_ok)) }
                }
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accents = MaterialTheme.accents
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = accents.accent,
                unselectedColor = AloColors.Mut,
                disabledSelectedColor = AloColors.Ln2,
                disabledUnselectedColor = AloColors.Ln2
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) AloColors.Ink else AloColors.Mut.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Selector desplegable: etiqueta pequeña arriba, valor elegido debajo y flecha a la derecha. */
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = shape,
            color = AloColors.Sf,
            border = BorderStroke(1.dp, AloColors.Ln2),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(enabled = options.isNotEmpty()) { open = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = AloColors.Mut
                    )
                    Text(
                        text = options.getOrNull(selectedIndex) ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AloColors.Ink
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AloColors.Mut)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(if (index == selectedIndex) "✓ $text" else text) },
                    onClick = {
                        open = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}
