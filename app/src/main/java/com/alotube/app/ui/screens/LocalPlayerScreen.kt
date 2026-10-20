package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Box
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
import com.alotube.app.R
import com.alotube.app.ui.theme.accents

/** Pestaña "Video Player": reproductor de videos locales (aún sin lógica). */
@Composable
fun LocalPlayerScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.local_player_soon),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.accents.hi,
            textAlign = TextAlign.Center
        )
    }
}
