package com.alotube.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alotube.app.R
import com.alotube.app.ui.theme.accents

/** Barra superior: ícono de video propio + logo "AloTube" con la fuente de la app. */
@Composable
fun AloTubeHeader(modifier: Modifier = Modifier) {
    val accents = MaterialTheme.accents
    Row(
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = accents.accent,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "AloTube",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = accents.hi
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "2.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
