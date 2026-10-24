package com.alotube.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alotube.app.R
import com.alotube.app.data.local.AudioDetails
import com.alotube.app.data.local.AudioDetailsReader
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration
import java.util.Locale

/*
 * Opciones de canción (botón ⋮ de la pantalla de reproducción de música local):
 *  - SongOptionsSheet : hoja con Etiquetas / Carátula del álbum / Detalles.
 *  - SongTagsScreen   : pantalla "Etiquetas", SOLO VISUAL (campos de solo lectura, el ✓ no guarda nada).
 *  - SongDetailsDialog: "Detalles" con los datos reales del archivo (AudioDetailsReader).
 * Nada de esto toca la reproducción ni modifica el archivo de audio.
 */

// ---------------------------------------------------------------------------------------------
// Hoja de opciones
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    onDismiss: () -> Unit,
    onTags: () -> Unit,
    onCover: () -> Unit,
    onDetails: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AloColors.Sheet,
        contentColor = AloColors.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AloColors.Ln2) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionCell(R.drawable.ic_song_tag, R.string.so_tags, Modifier.weight(1f), onTags)
                OptionCell(R.drawable.ic_song_image, R.string.so_cover, Modifier.weight(1f), onCover)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionCell(R.drawable.ic_song_info, R.string.so_details, Modifier.weight(1f), onDetails)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionCell(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.accents.hi,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Detalles (datos reales)
// ---------------------------------------------------------------------------------------------

/** Diálogo "Detalles": lee el archivo actual en segundo plano y muestra "Desconocido" en lo que falte. */
@Composable
fun SongDetailsDialog(uri: Uri?, fallbackName: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val details by produceState<AudioDetails?>(initialValue = null, uri) {
        value = AudioDetailsReader.read(ctx, uri, fallbackName)
    }
    val unknown = stringResource(R.string.so_unknown)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AloColors.Sheet,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AloColors.Sf3)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.so_details),
                        style = MaterialTheme.typography.titleLarge,
                        color = AloColors.Ink
                    )
                }
                val d = details
                if (d == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.accents.accent)
                    }
                } else {
                    val stereo = stringResource(R.string.so_stereo)
                    val mono = stringResource(R.string.so_mono)
                    val channelsText = when (val c = d.channels) {
                        null -> unknown
                        1 -> mono
                        2 -> stereo
                        else -> stringResource(R.string.so_channels_n, c)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailRow(R.string.so_f_name, d.name ?: unknown)
                        DetailRow(R.string.so_f_album, d.album ?: unknown)
                        DetailRow(R.string.so_f_artist, d.artist ?: unknown)
                        DetailRow(R.string.so_f_composer, d.composer ?: unknown)
                        DetailRow(R.string.so_f_album_artist, d.albumArtist ?: unknown)
                        DetailRow(R.string.so_f_genre, d.genre ?: unknown)
                        DetailRow(R.string.so_f_year, d.year ?: unknown)
                        DetailRow(R.string.so_f_track, d.track ?: unknown)
                        DetailRow(
                            R.string.so_f_duration,
                            d.durationMs?.takeIf { it > 0 }?.let { formatDuration(it / 1000) } ?: unknown
                        )
                        DetailRow(R.string.so_f_file, d.path ?: unknown)
                        DetailRow(
                            R.string.so_f_size,
                            d.sizeBytes?.let { String.format(Locale.getDefault(), "%.2f MB", it / (1024.0 * 1024.0)) }
                                ?: unknown
                        )
                        DetailRow(R.string.so_f_bitrate, d.bitrateKbps?.let { "$it kbps" } ?: unknown)
                        DetailRow(R.string.so_f_sample_rate, d.sampleRateHz?.let { "$it Hz" } ?: unknown)
                        DetailRow(R.string.so_f_channels, channelsText)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(@StringRes label: Int, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color = AloColors.Ink,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = AloColors.Mut,
                modifier = Modifier.weight(0.6f)
            )
        }
        HorizontalDivider(color = AloColors.Ln)
    }
}

// ---------------------------------------------------------------------------------------------
// Etiquetas (solo visual)
// ---------------------------------------------------------------------------------------------

/**
 * Pantalla "Etiquetas" a pantalla completa. SOLO VISUAL en esta fase: los campos son de solo lectura (se
 * muestran los datos reales del archivo), el ✓, ⋮, editar y buscar solo avisan "Próximamente" y no
 * guardan nada ni tocan el archivo.
 */
@Composable
fun SongTagsScreen(uri: Uri?, fallbackName: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val accents = MaterialTheme.accents
    val details by produceState<AudioDetails?>(initialValue = null, uri) {
        value = AudioDetailsReader.read(ctx, uri, fallbackName)
    }
    var editFile by remember { mutableStateOf(true) }
    val soon = { Toast.makeText(ctx, R.string.soon_toast, Toast.LENGTH_SHORT).show() }

    val unknown = stringResource(R.string.so_unknown)
    val d = details
    // Mientras se lee el archivo los campos van vacíos; después, el dato o "Desconocido"
    fun text(value: String?): String = if (d == null) "" else (value ?: unknown)

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = AloColors.Bg) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ---- Barra superior ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AloColors.Sf2)
                            .statusBarsPadding()
                            .height(64.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = AloColors.Ink
                            )
                        }
                        Text(
                            text = stringResource(R.string.so_tags),
                            style = MaterialTheme.typography.titleLarge,
                            color = AloColors.Ink,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        IconButton(onClick = soon) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accents.hi)
                        }
                        IconButton(onClick = soon) {
                            Icon(
                                painter = painterResource(R.drawable.ic_audio_more),
                                contentDescription = stringResource(R.string.mp_more),
                                tint = AloColors.Ink
                            )
                        }
                    }

                    // ---- Contenido ----
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                    ) {
                        // Carátula + botones editar / buscar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AloColors.Sf3),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(CircleShape)
                                        .background(AloColors.Ln2.copy(alpha = 0.6f))
                                )
                                Text(
                                    text = stringResource(R.string.so_no_cover),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AloColors.Ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(AloColors.Sf2)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = soon) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_song_edit),
                                        contentDescription = stringResource(R.string.so_edit_cd),
                                        tint = accents.hi,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = soon) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_search),
                                        contentDescription = stringResource(R.string.so_search_cd),
                                        tint = accents.hi,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Campos (solo lectura)
                        TagField(stringResource(R.string.so_f_name), text(d?.name))
                        TagField(stringResource(R.string.so_f_artist), text(d?.artist))
                        TagField(stringResource(R.string.so_f_album), text(d?.album))
                        TagField(stringResource(R.string.so_f_composer), text(d?.composer))
                        TagField(stringResource(R.string.so_f_album_artist), text(d?.albumArtist))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TagField(
                                stringResource(R.string.so_f_track),
                                if (d == null) "" else (d.track ?: "0"),
                                Modifier.weight(1f)
                            )
                            TagField(
                                stringResource(R.string.so_f_year),
                                if (d == null) "" else (d.year ?: "0"),
                                Modifier.weight(1f)
                            )
                        }
                        TagField(stringResource(R.string.so_f_genre), text(d?.genre))

                        // Espacio para que el último campo no quede tapado por el checkbox flotante
                        Spacer(Modifier.height(110.dp))
                    }
                }

                // ---- "Editar archivo de audio también" (flotante abajo) ----
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(AloColors.Sf3)
                        .clickable { editFile = !editFile }
                        .padding(start = 10.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editFile,
                        onCheckedChange = { editFile = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accents.accent,
                            checkmarkColor = AloColors.Bg,
                            uncheckedColor = AloColors.Mut
                        )
                    )
                    Text(
                        text = stringResource(R.string.so_edit_file),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AloColors.Ink
                    )
                }
            }
        }
    }
}

/** Campo de solo lectura con la etiqueta flotante y línea inferior (sin teclado ni edición). */
@Composable
private fun TagField(label: String, value: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedTextColor = AloColors.Ink,
            unfocusedTextColor = AloColors.Ink,
            focusedLabelColor = AloColors.Mut,
            unfocusedLabelColor = AloColors.Mut,
            focusedIndicatorColor = AloColors.Ln2,
            unfocusedIndicatorColor = AloColors.Ln2,
            cursorColor = Color.Transparent
        )
    )
}
