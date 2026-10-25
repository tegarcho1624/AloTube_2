package com.alotube.app.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.data.local.FolderIconStore
import com.alotube.app.data.local.FolderKind
import com.alotube.app.data.local.LocalFolder
import com.alotube.app.ui.theme.AloColors
import kotlinx.coroutines.launch

/**
 * Ícono personalizado de una carpeta (o null si no tiene: se usa el ícono por defecto).
 * Se vuelve a leer cada vez que cambia algún ícono ([FolderIconStore.version]).
 */
@Composable
fun rememberFolderIcon(kind: FolderKind, folderId: Long): ImageBitmap? {
    val ctx = LocalContext.current
    val version by FolderIconStore.version.collectAsState()
    val image by produceState<ImageBitmap?>(initialValue = null, kind, folderId, version) {
        value = FolderIconStore.load(ctx, kind, folderId)?.asImageBitmap()
    }
    return image
}

/**
 * Menú que se abre con un toque largo SIN movimiento sobre una carpeta de Música o Video Player:
 *  - "Establecer icono": abre el selector de imágenes de Android (Photo Picker); la imagen se copia a la
 *    carpeta interna de la app (FolderIconStore) y reemplaza el ícono por defecto.
 *  - "Quitar fondo": borra esa copia y la carpeta recupera su ícono por defecto (solo si tiene uno).
 * [target] = carpeta elegida (null = menú cerrado). Debe estar siempre en la composición para que el
 * selector pueda devolver el resultado aunque el diálogo ya esté cerrado.
 */
@Composable
fun FolderIconMenu(
    kind: FolderKind,
    target: LocalFolder?,
    onDismiss: () -> Unit,
    /** Archivar (o Desarchivar si [archived]); null = sin esa opción. */
    onArchive: ((LocalFolder) -> Unit)? = null,
    archived: Boolean = false
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // rememberSaveable: por si Android recrea la Activity mientras está abierto el selector
    var pendingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pendingId
        pendingId = null
        if (uri != null && id != null) {
            scope.launch {
                val ok = FolderIconStore.set(ctx, kind, id, uri)
                Toast.makeText(
                    ctx,
                    if (ok) R.string.bg_applied else R.string.bg_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    if (target == null) return

    val hasIcon = FolderIconStore.has(ctx, kind, target.id)

    @Composable
    fun Option(@StringRes label: Int, color: Color, onClick: () -> Unit) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AloColors.Sheet,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = target.name,
                color = AloColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Option(R.string.fi_set, AloColors.Ink) {
                    pendingId = target.id
                    onDismiss()
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (onArchive != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onArchive(target)
                                onDismiss()
                                Toast.makeText(
                                    ctx,
                                    if (archived) R.string.fa_unarchived_toast else R.string.fa_archived_toast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                            contentDescription = null,
                            tint = AloColors.Ink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(if (archived) R.string.fa_unarchive else R.string.fa_archive),
                            style = MaterialTheme.typography.titleMedium,
                            color = AloColors.Ink
                        )
                    }
                }
                if (hasIcon) {
                    Option(R.string.fi_remove, AloColors.Danger) {
                        FolderIconStore.clear(ctx, kind, target.id)
                        onDismiss()
                        Toast.makeText(ctx, R.string.bg_removed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.accent_delete_cancel), color = AloColors.Mut)
            }
        }
    )
}
