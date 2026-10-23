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
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        pendingKey = target.key
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onLongClick = {
                        if (BackgroundStore.has(ctx, target)) {
                            BackgroundStore.clear(ctx, target)
                            Toast.makeText(ctx, R.string.bg_removed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
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
