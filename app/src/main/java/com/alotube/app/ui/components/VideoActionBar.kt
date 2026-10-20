package com.alotube.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/** [active] = la acción ya funciona: ícono en el acento dinámico. Si no, gris. */
data class ActionSpec(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val active: Boolean,
    val onClick: () -> Unit
)

/**
 * Fila horizontal de acciones (ícono arriba, texto pequeño debajo).
 * En pantallas anchas se reparten el ancho por igual; en teléfono la fila se desplaza.
 */
@Composable
fun VideoActionBar(actions: List<ActionSpec>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 520.dp
        Row(
            modifier = if (wide) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top
        ) {
            actions.forEach { action ->
                ActionButton(
                    action = action,
                    modifier = if (wide) Modifier.weight(1f) else Modifier.width(84.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(action: ActionSpec, modifier: Modifier = Modifier) {
    val accents = MaterialTheme.accents
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = action.onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = null,
            tint = if (action.active) accents.hi else AloColors.Mut,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(action.label),
            style = MaterialTheme.typography.labelSmall,
            color = if (action.active) AloColors.Ink else AloColors.Mut,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
