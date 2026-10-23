package com.alotube.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.theme.accents
import com.alotube.app.data.local.BgMode
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import com.alotube.app.data.local.BackgroundStore
import com.alotube.app.data.local.BgScope
import com.alotube.app.ui.theme.AloColors
import kotlinx.coroutines.launch

/**
 * Fondos: lista simple de pantallas. Tocar una abre al instante el selector de imágenes de Android
 * (Photo Picker); la imagen se copia a la carpeta interna (BackgroundStore). Mantener presionado
 * quita el fondo de esa pantalla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BackgroundsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Se guarda con rememberSaveable por si Android recrea la Activity mientras está abierto el selector
    var menuFor by remember { mutableStateOf<BgScope?>(null) }
    val version by BackgroundStore.version.collectAsState()
    var pendingKey by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = BgScope.values().firstOrNull { it.key == pendingKey }
        pendingKey = null
        if (uri != null && target != null) {
            scope.launch {
                val ok = BackgroundStore.set(ctx, target, uri)
                Toast.makeText(
                    ctx,
                    if (ok) R.string.bg_applied else R.string.bg_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @Composable
    fun Item(@StringRes label: Int, target: BgScope) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        pendingKey = target.key
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onLongClick = {
                        if (BackgroundStore.has(ctx, target)) {
                            menuFor = target
                        } else {
                            Toast.makeText(ctx, R.string.bg_pick_first, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink
            )
            // ✓ si esa pantalla ya tiene fondo
            if (version >= 0 && BackgroundStore.has(ctx, target)) {
                Text(
                    text = "  ✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.accents.hi
                )
            }
        }
    }

    // ---- Menú de mantener presionado: escalado / eliminar ----
    val menuTarget = menuFor
    if (menuTarget != null) {
        val current = BackgroundStore.mode(ctx, menuTarget)
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = AloColors.Sheet,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.bg_menu_title), color = AloColors.Ink) },
            text = {
                Column {
                    @Composable
                    fun Option(@StringRes label: Int, mode: BgMode) {
                        Text(
                            text = stringResource(label) + if (mode == current) "  ✓" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (mode == current) MaterialTheme.accents.hi else AloColors.Ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BackgroundStore.setMode(ctx, menuTarget, mode)
                                    menuFor = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                    Option(R.string.bg_mode_fill, BgMode.FILL)
                    Option(R.string.bg_mode_stretch, BgMode.STRETCH)
                    Option(R.string.bg_mode_center, BgMode.CENTER)
                    Text(
                        text = stringResource(R.string.bg_delete),
                        style = MaterialTheme.typography.titleMedium,
                        color = AloColors.Danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                BackgroundStore.clear(ctx, menuTarget)
                                menuFor = null
                                Toast.makeText(ctx, R.string.bg_removed, Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuFor = null }) {
                    Text(stringResource(R.string.accent_delete_cancel), color = AloColors.Mut)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AloColors.Bg)
    ) {
        SettingsTopBar(stringResource(R.string.bg_title), onBack)
        Item(R.string.bg_youtube, BgScope.YOUTUBE)
        Item(R.string.bg_music, BgScope.MUSIC)
        Item(R.string.bg_video, BgScope.VIDEO)
        Item(R.string.bg_settings, BgScope.SETTINGS)
    }
}
