package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.AloNavBar
import com.alotube.app.ui.theme.AloColors

/** Raíz de la app: navegación inferior + pestaña activa. */
@Composable
fun AloTubeRoot(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    onAccentChanged: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AloColors.Bg,
        bottomBar = { AloNavBar(selected = tab, onSelect = { tab = it }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> MainScreen(onVideoClick = onVideoClick)
                1 -> ComingSoonScreen(stringResource(R.string.soon_music))
                2 -> ComingSoonScreen(stringResource(R.string.soon_library))
                else -> SettingsScreen(onAccentChanged = onAccentChanged)
            }
        }
    }
}
