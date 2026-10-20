package com.alotube.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors

/** Información bajo el reproductor: título, canal, vistas · fecha, likes y barra de acciones. */
@Composable
fun WatchInfo(
    title: String,
    author: String,
    meta: String,
    likes: String?,
    actions: List<ActionSpec>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = AloColors.Ink
        )
        if (author.isNotBlank()) {
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = AloColors.Ink,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (likes != null) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lib_like),
                    contentDescription = null,
                    tint = AloColors.Mut,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = likes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AloColors.Mut
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = AloColors.Ln)
        VideoActionBar(actions = actions, modifier = Modifier.padding(vertical = 6.dp))
        HorizontalDivider(color = AloColors.Ln)
    }
}
