package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.data.local.BgScope
import com.alotube.app.ui.components.AloNavBar
import com.alotube.app.ui.components.AppBackground
import com.alotube.app.ui.components.BackgroundLayer
import com.alotube.app.ui.components.MiniPlayerHost
import com.alotube.app.ui.theme.AloColors

/** Raíz de la app: navegación inferior + pestaña activa. La pantalla de canal se muestra encima. */
@Composable
fun AloTubeRoot(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    onAccentChanged: () -> Unit,
    onLoggedOut: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Canal abierto (null = ninguno). Se guarda con la Activity: sobrevive a cambiar el color de acento
    var channelUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // La navegación principal sigue "viva" debajo: al volver del canal se conservan resultados y scroll
        // Fondo de la pestaña activa (imagen elegida + capa oscura, o el oscuro de siempre)
        BackgroundLayer(
            scope = when (tab) {
                0, 3 -> BgScope.YOUTUBE
                1 -> BgScope.MUSIC
                2 -> BgScope.VIDEO
                else -> BgScope.SETTINGS
            }
        )
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    // Mini-reproductor: solo aparece si suena (o está en pausa) una música local
                    MiniPlayerHost(onOpen = onVideoClick)
                    AloNavBar(selected = tab, onSelect = { tab = it })
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> MainScreen(
                        onVideoClick = onVideoClick,
                        onChannelClick = { channel ->
                            channelName = channel.name
                            channelUrl = channel.url
                        }
                    )
                    1 -> MusicScreen(onVideoClick = onVideoClick)
                    2 -> LocalPlayerScreen(onVideoClick = onVideoClick)
                    3 -> LibraryScreen(onVideoClick = onVideoClick)
                    else -> SettingsScreen(onAccentChanged = onAccentChanged, onLoggedOut = onLoggedOut)
                }
            }
        }

        channelUrl?.let { url ->
            // Canal: mismo fondo que Inicio y Biblioteca (la pantalla va transparente encima)
            AppBackground(BgScope.YOUTUBE) {
                ChannelScreen(
                    channelUrl = url,
                    initialName = channelName,
                    onBack = { channelUrl = null },
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}
