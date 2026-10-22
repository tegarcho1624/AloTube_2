package com.alotube.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.mediaExists
import com.alotube.app.data.local.toVideoItem
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.LocalAudioRow
import com.alotube.app.ui.components.LocalVideoCard
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.gridColumns
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LocalMediaUiState
import com.alotube.app.viewmodel.LocalMediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "settings"
private const val KEY_ASKED = "media_permission_asked"

/** Android 13+: permisos por tipo de medio. Antes: almacenamiento externo. */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasPermissions(ctx: Context): Boolean = requiredPermissions().all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Si el usuario ya rechazó el permiso y Android no volverá a preguntar, hay que ir a Ajustes. */
private fun needsSettings(ctx: Context): Boolean {
    val activity = ctx.findActivity() ?: return false
    val asked = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    return asked && requiredPermissions().any {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

/**
 * Pestaña "Video Player": reproductor de los videos y audios del dispositivo (por defecto los de Descargas).
 * Al tocar un archivo se abre el mismo reproductor de AloTube con el content:// URI.
 */
@Composable
fun LocalPlayerScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: LocalMediaViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(hasPermissions(ctx)) }
    var attempt by remember { mutableIntStateOf(0) } // fuerza recomponer el botón tras un rechazo
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermissions(ctx)
        attempt++
    }

    // Al volver de Ajustes del sistema puede haberse concedido el permiso
    LifecycleResumeEffect(Unit) {
        granted = hasPermissions(ctx)
        onPauseOrDispose { }
    }

    // Se pide automáticamente la primera vez que se abre esta pestaña
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!granted && !prefs.getBoolean(KEY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            launcher.launch(requiredPermissions())
        }
    }

    // Cada vez que se entra a la pestaña (o se concede el permiso) se refresca la lista
    LaunchedEffect(granted) { if (granted) viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Título + alternar "solo descargas / todo el dispositivo" ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.lp_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.accents.hi
            )
            if (granted) {
                TextButton(onClick = { viewModel.setIncludeAll(!state.includeAll) }) {
                    Text(
                        stringResource(
                            if (state.includeAll) R.string.lp_show_downloads else R.string.lp_show_all
                        )
                    )
                }
            }
        }

        if (!granted) {
            PermissionContent(
                toSettings = remember(attempt, granted) { needsSettings(ctx) },
                onGrant = {
                    if (needsSettings(ctx)) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                        )
                    } else {
                        launcher.launch(requiredPermissions())
                    }
                }
            )
        } else {
            LocalLibraryContent(
                state = state,
                onRetry = viewModel::load,
                onPlay = { list, index ->
                    onVideoClick(list.map { it.toVideoItem() }, index)
                },
                onMissing = viewModel::load
            )
        }
    }
}

@Composable
private fun PermissionContent(toSettings: Boolean, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_media),
            contentDescription = null,
            tint = AloColors.Mut,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.lp_perm_text),
            style = MaterialTheme.typography.bodyLarge,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(if (toSettings) R.string.lp_perm_settings else R.string.lp_perm_grant))
        }
    }
}

@Composable
private fun LocalLibraryContent(
    state: LocalMediaUiState,
    onRetry: () -> Unit,
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit
) {
    val ctx = LocalContext.current
    val accents = MaterialTheme.accents
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Antes de abrir se comprueba que el archivo siga existiendo (el registro de MediaStore puede ser viejo)
    val play: (List<LocalMedia>, Int) -> Unit = { list, index ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { mediaExists(ctx, list[index].uri) }
            if (ok) {
                onPlay(list, index)
            } else {
                Toast.makeText(ctx, R.string.lp_missing, Toast.LENGTH_SHORT).show()
                onMissing()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = AloColors.Bg,
            contentColor = accents.hi,
            divider = { HorizontalDivider(color = AloColors.Ln) }
        ) {
            listOf(R.string.lp_tab_videos, R.string.lp_tab_audios).forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    selectedContentColor = accents.hi,
                    unselectedContentColor = AloColors.Mut,
                    text = { Text(stringResource(label)) }
                )
            }
        }

        // Deslizar entre Videos y Audios; la página vecina ya queda compuesta para que el cambio sea fluido
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val items = if (page == 0) state.videos else state.audios
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !state.loaded && state.loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = accents.accent
                    )

                    !state.loaded && state.error != null -> StatusMessage(
                        text = stringResource(R.string.lp_error) + "\n\n" + state.error,
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    items.isEmpty() -> EmptyMedia(
                        text = stringResource(
                            when {
                                state.videos.isEmpty() && state.audios.isEmpty() -> R.string.lp_empty_all
                                page == 0 -> R.string.lp_empty_videos
                                else -> R.string.lp_empty_audios
                            }
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    page == 0 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns()),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(items) { index, media ->
                            LocalVideoCard(media = media, onClick = { play(items, index) })
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns()),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(items) { index, media ->
                            LocalAudioRow(media = media, onClick = { play(items, index) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMedia(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_media),
            contentDescription = null,
            tint = AloColors.Mut,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
