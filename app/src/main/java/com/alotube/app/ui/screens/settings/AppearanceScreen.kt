package com.alotube.app.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.components.aloTextFieldColors
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.ThemeUtils
import com.alotube.app.ui.theme.accents

/**
 * Apariencia: color de acento (paleta + HEX propio + restablecer). Es el mismo código de antes:
 * al cambiar el color se guarda en SharedPreferences y se recrea la Activity (onAccentChanged).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, onAccentChanged: () -> Unit, onOpenBackgrounds: () -> Unit, onOpenFonts: () -> Unit) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Se incrementa al borrar un color para que la lista se vuelva a leer de SharedPreferences
    var refresh by remember { mutableIntStateOf(0) }
    val current = ThemeUtils.accent(ctx)
    val customSwatches = remember(refresh) {
        ThemeUtils.customColors(ctx).filter { it !in ThemeUtils.ACCENT_COLORS }
    }
    val swatches = ThemeUtils.ACCENT_COLORS.toList() + customSwatches

    // Color personalizado pendiente de confirmar su eliminación (null = ninguno)
    var colorToDelete by remember { mutableStateOf<Int?>(null) }

    var hex by remember { mutableStateOf("") }
    var hexError by remember { mutableStateOf(false) }

    fun pick(color: Int) {
        ThemeUtils.setAccent(ctx, color)
        onAccentChanged()
    }

    fun addHex() {
        val parsed = ThemeUtils.parseHex(hex)
        if (parsed == null) {
            hexError = true
        } else {
            ThemeUtils.addCustom(ctx, parsed)
            pick(parsed)
        }
    }

    // ---- Confirmación de eliminación ----
    val target = colorToDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { colorToDelete = null },
            containerColor = AloColors.Sheet,
            titleContentColor = AloColors.Ink,
            textContentColor = AloColors.Mut,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.accent_delete_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(target))
                            .border(1.dp, AloColors.Ln2, CircleShape)
                    )
                    Text(
                        text = "#%06X".format(target and 0x00FFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AloColors.Mut
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    colorToDelete = null
                    // Se quita de la lista guardada; si era el color activo, vuelve al dorado
                    val accentReset = ThemeUtils.removeCustom(ctx, target)
                    if (accentReset) onAccentChanged() else refresh++
                }) {
                    Text(stringResource(R.string.accent_delete_confirm), color = AloColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { colorToDelete = null }) {
                    Text(stringResource(R.string.accent_delete_cancel), color = AloColors.Mut)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SettingsTopBar(stringResource(R.string.set_appearance), onBack)

        // Fondo >  (abre la pantalla "Fondos")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBackgrounds)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.set_background),
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AloColors.Mut,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(color = AloColors.Ln)

        // Estilo de Escritura   Estilos >
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFonts)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.set_font_style),
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.set_font_styles),
                style = MaterialTheme.typography.bodyMedium,
                color = AloColors.Mut
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AloColors.Mut,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
        HorizontalDivider(color = AloColors.Ln)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.accent_title),
                style = MaterialTheme.typography.titleMedium,
                color = AloColors.Ink
            )
            Text(
                text = stringResource(R.string.accent_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            val selected = color == current
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) AloColors.Ink else AloColors.Ln2,
                                        shape = CircleShape
                                    )
                                    .combinedClickable(
                                        onClick = { pick(color) }, // toque corto: aplica el color
                                        // Mantener presionado: solo en colores propios (los 12 básicos no se borran)
                                        onLongClick = if (color in customSwatches) {
                                            {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                colorToDelete = color
                                            }
                                        } else null
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.accent_custom),
                style = MaterialTheme.typography.titleSmall,
                color = AloColors.Ink
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        hexError = false
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("#C9A24B") },
                    singleLine = true,
                    isError = hexError,
                    shape = RoundedCornerShape(16.dp),
                    colors = aloTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addHex() })
                )
                Button(onClick = { addHex() }) { Text(stringResource(R.string.accent_add)) }
            }
            if (hexError) {
                Text(
                    text = stringResource(R.string.accent_hex_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = {
                ThemeUtils.resetAccent(ctx)
                onAccentChanged()
            }) {
                Text(stringResource(R.string.accent_reset))
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.set_more_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.accents.hi
            )
        }
    }
}
