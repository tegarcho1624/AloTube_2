package com.alotube.app.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/** Rutas de la navegación interna de Ajustes (NavHost). */
object SettingsRoutes {
    const val MAIN = "settings_main"
    const val MENU = "settings_menu"
    const val APPEARANCE = "settings_appearance"
    const val FONTS = "settings_fonts"
    const val BACKGROUNDS = "settings_backgrounds"
    const val VIDEO_AUDIO = "settings_video_audio"
    const val HISTORY = "settings_history"
    const val NOTIFICATIONS = "settings_notifications"
    const val DOWNLOAD = "settings_download"
}

/** Barra superior de los submenús: flecha para volver + título. */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.lib_back),
                tint = AloColors.Mut
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = AloColors.Ink,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Fila de menú: ícono a la izquierda y título en blanco (como en la referencia). */
@Composable
fun SettingsRow(@DrawableRes icon: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AloColors.Ink,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink
        )
    }
}

/** Texto centrado "Próximamente" para las sub-pantallas vacías. */
@Composable
fun SettingsSoon(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.set_soon),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.accents.hi
        )
    }
}
