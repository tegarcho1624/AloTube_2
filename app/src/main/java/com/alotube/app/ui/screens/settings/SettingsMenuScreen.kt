package com.alotube.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors

/** Submenú: Video y audio, Apariencia, Historial y caché, Notificación, Descargar. */
@Composable
fun SettingsMenuScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SettingsTopBar(stringResource(R.string.settings_item_settings), onBack)
        SettingsRow(R.drawable.ic_act_headphones, stringResource(R.string.set_video_audio)) {
            onNavigate(SettingsRoutes.VIDEO_AUDIO)
        }
        SettingsRow(R.drawable.ic_set_palette, stringResource(R.string.set_appearance)) {
            onNavigate(SettingsRoutes.APPEARANCE)
        }
        SettingsRow(R.drawable.ic_set_history, stringResource(R.string.set_history)) {
            onNavigate(SettingsRoutes.HISTORY)
        }
        SettingsRow(R.drawable.ic_set_bell, stringResource(R.string.set_notifications)) {
            onNavigate(SettingsRoutes.NOTIFICATIONS)
        }
        SettingsRow(R.drawable.ic_act_download, stringResource(R.string.set_download)) {
            onNavigate(SettingsRoutes.DOWNLOAD)
        }
        SettingsRow(R.drawable.ic_archive, stringResource(R.string.fa_title)) {
            onNavigate(SettingsRoutes.ARCHIVED)
        }
    }
}

/** Sub-pantalla vacía: barra superior con flecha y "Próximamente" al centro. */
@Composable
fun SettingsPlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SettingsTopBar(title, onBack)
        SettingsSoon()
    }
}
