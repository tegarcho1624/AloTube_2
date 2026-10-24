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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.data.local.SaveError
import com.alotube.app.data.local.SaveResult
import com.alotube.app.data.local.TagEdits
import com.alotube.app.data.local.TagSaver
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.util.formatDuration
import java.util.Locale

/*
 * Opciones de canción (botón ⋮ de la pantalla de reproducción de música local):
 *  - SongOptionsSheet : hoja con Etiquetas / Carátula del álbum / Detalles.
 *  - SongTagsScreen   : pantalla "Etiquetas": campos editables y ✓ que guarda en el archivo (TagSaver).
 *  - SongDetailsDialog: "Detalles" con los datos reales del archivo (AudioDetailsReader).
 * Detalles solo lee; Etiquetas escribe el archivo solo al tocar ✓ (la reproducción la para/reanuda quien llama).
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
// Etiquetas (editables)
// ---------------------------------------------------------------------------------------------

/**
 * Pantalla "Etiquetas" a pantalla completa. Los campos se editan y el ✓ guarda los cambios en el archivo
 * ORIGINAL (TagSaver: respaldo, escritura en segundo plano y verificación). Al terminar muestra un Toast y
 * se cierra. El lápiz de la carátula, la lupa y ⋮ solo avisan "Próximamente".
 *
 * [beforeSave] se ejecuta justo antes de escribir (quien reproduce detiene el audio para soltar el archivo) y
 * [afterSave] justo después, haya salido bien o mal (recarga y reanuda). [changed] = el archivo pudo cambiar.
 */
@Composable
fun SongTagsScreen(
    uri: Uri?,
    fallbackName: String,
    onBack: () -> Unit,
    beforeSave: suspend () -> Unit = {},
    afterSave: suspend (changed: Boolean) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accents = MaterialTheme.accents
    val details by produceState<AudioDetails?>(initialValue = null, uri) {
        value = AudioDetailsReader.read(ctx, uri, fallbackName)
    }
    val d = details
    val soon = { Toast.makeText(ctx, R.string.soon_toast, Toast.LENGTH_SHORT).show() }

    // Valores de los campos: se rellenan cuando se termina de leer el archivo
    var name by remember(d) { mutableStateOf(d?.name.orEmpty()) }
    var artist by remember(d) { mutableStateOf(d?.artist.orEmpty()) }
    var album by remember(d) { mutableStateOf(d?.album.orEmpty()) }
    var composer by remember(d) { mutableStateOf(d?.composer.orEmpty()) }
    var albumArtist by remember(d) { mutableStateOf(d?.albumArtist.orEmpty()) }
    var track by remember(d) { mutableStateOf(d?.track.orEmpty()) }
    var year by remember(d) { mutableStateOf(d?.year.orEmpty()) }
    var genre by remember(d) { mutableStateOf(d?.genre.orEmpty()) }

    var saving by remember { mutableStateOf(false) }
    var pendingEdits by remember { mutableStateOf<TagEdits?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }

    fun toast(@StringRes res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()

    // Diálogo del sistema "¿Permitir modificar este archivo?" (solo si el archivo es de otra app)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            retryTick++
        } else {
            saving = false
            toast(R.string.so_err_permission_denied)
        }
    }

    suspend fun runSave(edits: TagEdits) {
        val target = uri ?: run { saving = false; return }
        saving = true
        try {
            beforeSave()
            val result = TagSaver.save(ctx, target, edits)
            when (result) {
                is SaveResult.NeedsPermission -> {
                    withContext(NonCancellable) { afterSave(false) }
                    permissionLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                    return // saving sigue true hasta que el usuario conteste
                }
                is SaveResult.Saved -> {
                    AudioMetadataRepository.invalidate(target)
                    withContext(NonCancellable) { afterSave(true) }
                    toast(R.string.so_saved)
                    saving = false
                    onBack()
                    return
                }
                is SaveResult.NoChanges -> {
                    withContext(NonCancellable) { afterSave(false) }
                    toast(R.string.so_no_changes)
                    saving = false
                    onBack()
                    return
                }
                is SaveResult.Failed -> {
                    if (result.touched) AudioMetadataRepository.invalidate(target)
                    withContext(NonCancellable) { afterSave(result.touched) }
                    toast(
                        when (result.error) {
                            SaveError.NOT_OPUS -> R.string.so_err_not_opus
                            SaveError.PERMISSION -> R.string.so_err_permission
                            SaveError.READ -> R.string.so_err_read
                            SaveError.WRITE -> R.string.so_err_write
                            SaveError.VERIFY -> R.string.so_err_verify
                            SaveError.STRUCTURE -> R.string.so_err_structure
                            SaveError.UNKNOWN -> R.string.so_err_unknown
                        }
                    )
                    saving = false
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { afterSave(true) }
            toast(R.string.so_err_unknown)
            saving = false
        }
    }

    LaunchedEffect(retryTick) {
        if (retryTick > 0) pendingEdits?.let { runSave(it) }
    }

    /** Solo los campos que el usuario cambió (null = no tocar). */
    fun buildEdits(): TagEdits {
        fun changed(now: String, before: String?): String? {
            val value = now.trim()
            return if (value != before.orEmpty().trim()) value else null
        }
        return TagEdits(
            title = changed(name, d?.name),
            artist = changed(artist, d?.artist),
            album = changed(album, d?.album),
            composer = changed(composer, d?.composer),
            albumArtist = changed(albumArtist, d?.albumArtist),
            track = changed(track, d?.track),
            year = changed(year, d?.year),
            genre = changed(genre, d?.genre)
        )
    }

    val onSave = save@{
        if (saving || d == null) return@save
        val edits = buildEdits()
        pendingEdits = edits
        scope.launch { runSave(edits) }
        Unit
    }
    val back = { if (!saving) onBack() }
    val editable = d != null && !saving

    Dialog(
        onDismissRequest = back,
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
                        IconButton(onClick = back) {
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
                        IconButton(onClick = onSave) {
                            if (saving) {
                                CircularProgressIndicator(
                                    color = accents.hi,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, tint = accents.hi)
                            }
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
                            .imePadding()
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
                                // Lápiz: la edición de la carátula llega en la siguiente fase
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

                        // Campos (editables)
                        TagField(stringResource(R.string.so_f_name), name, editable) { name = it }
                        TagField(stringResource(R.string.so_f_artist), artist, editable) { artist = it }
                        TagField(stringResource(R.string.so_f_album), album, editable) { album = it }
                        TagField(stringResource(R.string.so_f_composer), composer, editable) { composer = it }
                        TagField(stringResource(R.string.so_f_album_artist), albumArtist, editable) { albumArtist = it }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TagField(
                                stringResource(R.string.so_f_track), track, editable, Modifier.weight(1f),
                                KeyboardType.Number
                            ) { track = it.filter(Char::isDigit).take(4) }
                            TagField(
                                stringResource(R.string.so_f_year), year, editable, Modifier.weight(1f),
                                KeyboardType.Number
                            ) { year = it.filter(Char::isDigit).take(4) }
                        }
                        TagField(stringResource(R.string.so_f_genre), genre, editable) { genre = it }

                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

/** Campo editable con la etiqueta flotante y línea inferior. */
@Composable
private fun TagField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onChange,
        readOnly = !enabled,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
            cursorColor = MaterialTheme.accents.accent
        )
    )
}
