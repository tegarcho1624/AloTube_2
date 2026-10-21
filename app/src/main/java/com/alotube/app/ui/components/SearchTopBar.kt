package com.alotube.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/** Colores de los campos de texto: fondo oscuro, texto claro, foco y cursor en el acento. */
@Composable
fun aloTextFieldColors(): TextFieldColors {
    val accent = MaterialTheme.accents.accent
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = AloColors.Ink,
        unfocusedTextColor = AloColors.Ink,
        focusedContainerColor = AloColors.Sf,
        unfocusedContainerColor = AloColors.Sf,
        cursorColor = accent,
        focusedBorderColor = accent,
        unfocusedBorderColor = AloColors.Ln2,
        focusedLeadingIconColor = accent,
        unfocusedLeadingIconColor = AloColors.Mut,
        focusedTrailingIconColor = AloColors.Mut,
        unfocusedTrailingIconColor = AloColors.Mut,
        focusedPlaceholderColor = AloColors.Mut,
        unfocusedPlaceholderColor = AloColors.Mut
    )
}

/** Barra de búsqueda redondeada (misma lógica de siempre: solo cambió el estilo). */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** El texto contiene un enlace de YouTube: se muestra el ícono de enlace en el acento. */
    isLink: Boolean = false
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            IconButton(onClick = onSearch) {
                Icon(
                    painter = painterResource(if (isLink) R.drawable.ic_link else R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search_hint),
                    tint = if (isLink) MaterialTheme.accents.accent else LocalContentColor.current
                )
            }
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = aloTextFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}
