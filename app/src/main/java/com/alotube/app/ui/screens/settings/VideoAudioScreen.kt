package com.alotube.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.player.PlaybackPrefs
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/** Video y audio: "Reanudar tras interrupciones" (mismo ajuste de siempre: PlaybackPrefs). */
@Composable
fun VideoAudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var autoResume by remember { mutableStateOf(PlaybackPrefs.isAutoResume(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SettingsTopBar(stringResource(R.string.set_video_audio), onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.resume_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AloColors.Ink
                    )
                    Text(
                        text = stringResource(R.string.resume_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = AloColors.Mut
                    )
                }
                Switch(
                    checked = autoResume,
                    onCheckedChange = {
                        autoResume = it
                        PlaybackPrefs.setAutoResume(ctx, it)
                    }
                )
            }

            Text(
                text = stringResource(R.string.set_more_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.accents.hi,
                modifier = Modifier.padding(top = 28.dp)
            )
        }
    }
}
