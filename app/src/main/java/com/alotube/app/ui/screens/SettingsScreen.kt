package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.data.local.FolderKind
import com.alotube.app.ui.screens.settings.AppearanceScreen
import com.alotube.app.ui.screens.settings.ArchivedFoldersScreen
import com.alotube.app.ui.screens.settings.ArchivedMenuScreen
import com.alotube.app.ui.screens.settings.BackgroundsScreen
import com.alotube.app.ui.screens.settings.FontsScreen
import com.alotube.app.ui.screens.settings.SettingsMainScreen
import com.alotube.app.ui.screens.settings.SettingsMenuScreen
import com.alotube.app.ui.screens.settings.SettingsPlaceholderScreen
import com.alotube.app.ui.screens.settings.SettingsRoutes
import com.alotube.app.ui.screens.settings.VideoAudioScreen
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/**
 * Pestaña "Ajustes": menú principal y submenús con Navigation Compose (todo dentro de Compose,
 * sin Activities extra). El NavController se guarda con la Activity, así que al cambiar el color
 * de acento (recreate) se vuelve a la misma pantalla.
 */
@Composable
fun SettingsScreen(
    onAccentChanged: () -> Unit,
    onLoggedOut: () -> Unit,
    onVideoClick: (List<VideoItem>, Int) -> Unit
) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(
        navController = nav,
        startDestination = SettingsRoutes.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(SettingsRoutes.MAIN) {
            SettingsMainScreen(
                onOpenSettings = { nav.navigate(SettingsRoutes.MENU) },
                onLoggedOut = onLoggedOut
            )
        }
        composable(SettingsRoutes.MENU) {
            SettingsMenuScreen(onBack = back, onNavigate = { nav.navigate(it) })
        }
        composable(SettingsRoutes.APPEARANCE) {
            AppearanceScreen(
                onBack = back,
                onAccentChanged = onAccentChanged,
                onOpenBackgrounds = { nav.navigate(SettingsRoutes.BACKGROUNDS) },
                onOpenFonts = { nav.navigate(SettingsRoutes.FONTS) }
            )
        }
        composable(SettingsRoutes.FONTS) {
            FontsScreen(onBack = back)
        }
        composable(SettingsRoutes.BACKGROUNDS) {
            BackgroundsScreen(onBack = back)
        }
        composable(SettingsRoutes.VIDEO_AUDIO) {
            VideoAudioScreen(onBack = back)
        }
        composable(SettingsRoutes.HISTORY) {
            SettingsPlaceholderScreen(stringResource(R.string.set_history), back)
        }
        composable(SettingsRoutes.NOTIFICATIONS) {
            SettingsPlaceholderScreen(stringResource(R.string.set_notifications), back)
        }
        composable(SettingsRoutes.DOWNLOAD) {
            SettingsPlaceholderScreen(stringResource(R.string.set_download), back)
        }
        composable(SettingsRoutes.ARCHIVED) {
            ArchivedMenuScreen(
                onBack = back,
                onMusic = { nav.navigate(SettingsRoutes.ARCHIVED_MUSIC) },
                onVideo = { nav.navigate(SettingsRoutes.ARCHIVED_VIDEO) }
            )
        }
        composable(SettingsRoutes.ARCHIVED_MUSIC) {
            ArchivedFoldersScreen(kind = FolderKind.MUSIC, onBack = back, onVideoClick = onVideoClick)
        }
        composable(SettingsRoutes.ARCHIVED_VIDEO) {
            ArchivedFoldersScreen(kind = FolderKind.VIDEO, onBack = back, onVideoClick = onVideoClick)
        }
    }
}

/** Pestañas que llegarán en fases futuras (p. ej. Música). */
@Composable
fun ComingSoonScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.soon_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.accents.hi
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
