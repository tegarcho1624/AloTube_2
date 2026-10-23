package com.alotube.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.FontLoader
import com.alotube.app.ui.theme.accents
import kotlinx.coroutines.launch

/**
 * Estilo de Escritura: lista de fuentes (assets, importadas y del sistema), cada nombre dibujado en su
 * propia tipografía; ✓ en la elegida. "+" importa una .ttf/.otf (se copia a files/fonts/).
 * Tocar una fuente la guarda (SharedPreferences "settings", clave `font`) y cambia toda la app al instante.
 */
@Composable
fun FontsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(FontLoader.currentId(ctx)) }
    val fonts = remember(refresh) { FontLoader.list(ctx) }

    // Se acepta cualquier tipo MIME: muchos gestores marcan las fuentes como octet-stream.
    // La validación real (extensión + cabecera del archivo) la hace FontLoader.import.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = FontLoader.import(ctx, uri)
                if (name != null) {
                    refresh++
                    Toast.makeText(ctx, R.string.font_imported, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, R.string.font_invalid, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.lib_back),
                    tint = AloColors.Mut
                )
            }
            Text(
                text = stringResource(R.string.set_font_style),
                style = MaterialTheme.typography.titleLarge,
                color = AloColors.Ink,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.font_import),
                    tint = MaterialTheme.accents.hi
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(fonts, key = { it.id }) { font ->
                val isSelected = font.id == selected
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = font.id
                                FontLoader.select(ctx, font.id)
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vista previa: el nombre en su propia tipografía
                        Text(
                            text = font.label,
                            fontFamily = font.family,
                            fontSize = 22.sp,
                            color = if (isSelected) MaterialTheme.accents.hi else AloColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text(
                                text = "✓",
                                fontSize = 22.sp,
                                color = MaterialTheme.accents.hi
                            )
                        }
                    }
                    HorizontalDivider(color = AloColors.Ln)
                }
            }
        }
    }
}
