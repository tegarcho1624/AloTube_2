package com.alotube.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/**
 * Búsqueda local dentro de UNA carpeta (Música y Video Player). Solo guarda si la barra está desplegada y el
 * texto; no toca el orden guardado ni los archivos. Se crea con [rememberFolderSearch] ligado a la carpeta:
 * al salir y volver a entrar empieza vacía y cerrada.
 */
@Stable
class FolderSearch {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")

    fun close() {
        active = false
        query = ""
    }
}

@Composable
fun rememberFolderSearch(folderId: Long?): FolderSearch = remember(folderId) { FolderSearch() }

/** Archivos cuyo nombre contiene [query] (sin distinguir mayúsculas). Con texto vacío, todos. */
fun List<LocalMedia>.filterByName(query: String): List<LocalMedia> {
    val q = query.trim()
    return if (q.isEmpty()) this else filter { it.name.contains(q, ignoreCase = true) }
}

/** Barra de búsqueda que sustituye al "Música > Carpeta": atrás, campo de texto y X para cerrar. */
@Composable
fun FolderSearchBar(search: FolderSearch, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // El teclado aparece solo al desplegar la barra
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.lib_back),
                tint = AloColors.Mut
            )
        }
        TextField(
            value = search.query,
            onValueChange = { search.query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.fs_hint), color = AloColors.Mut) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = AloColors.Ink,
                unfocusedTextColor = AloColors.Ink,
                focusedIndicatorColor = AloColors.Ln2,
                unfocusedIndicatorColor = AloColors.Ln2,
                cursorColor = MaterialTheme.accents.accent
            )
        )
        IconButton(onClick = { search.close() }) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.fs_close),
                tint = AloColors.Ink
            )
        }
    }
}

/** Lupa de la barra normal de la carpeta. */
@Composable
fun FolderSearchButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.fs_open),
            tint = MaterialTheme.accents.hi,
            modifier = Modifier.size(24.dp)
        )
    }
}
