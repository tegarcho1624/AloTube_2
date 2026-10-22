package com.alotube.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alotube.app.R
import com.alotube.app.data.local.LocalMedia
import com.alotube.app.data.local.mediaExists
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Piezas compartidas por "Video Player" (carpetas de videos) y "Música" (audios locales):
 * permisos de medios, encabezado con el interruptor "solo descargas / todo el dispositivo",
 * mensaje de vacío y reproducción segura (comprueba que el archivo exista).
 */

private const val PREFS = "settings"
private const val KEY_ASKED = "media_permission_asked"

/** Android 13+: permisos por tipo de medio. Antes: almacenamiento externo. */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasPermissions(ctx: Context): Boolean = requiredPermissions().all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Si el usuario ya rechazó el permiso y Android no volverá a preguntar, hay que ir a Ajustes. */
private fun needsSettings(ctx: Context): Boolean {
    val activity = ctx.findActivity() ?: return false
    val asked = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    return asked && requiredPermissions().any {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

/** Estado del permiso de medios: [request] lo pide, o abre Ajustes del sistema si ya no se puede pedir. */
class MediaPermission(val granted: Boolean, val toSettings: Boolean, val request: () -> Unit)

/** Pide el permiso la primera vez que se abre una pestaña local y lo vuelve a comprobar al volver de Ajustes. */
@Composable
fun rememberMediaPermission(): MediaPermission {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasPermissions(ctx)) }
    var attempt by remember { mutableIntStateOf(0) } // fuerza recomponer el botón tras un rechazo
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermissions(ctx)
        attempt++
    }

    // Al volver de Ajustes del sistema puede haberse concedido el permiso
    LifecycleResumeEffect(Unit) {
        granted = hasPermissions(ctx)
        onPauseOrDispose { }
    }

    // Se pide automáticamente la primera vez
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!granted && !prefs.getBoolean(KEY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            launcher.launch(requiredPermissions())
        }
    }

    return MediaPermission(
        granted = granted,
        toSettings = remember(attempt, granted) { needsSettings(ctx) },
        request = {
            if (needsSettings(ctx)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            } else {
                launcher.launch(requiredPermissions())
            }
        }
    )
}

/** Título de la pantalla + (si hay permiso) alternar "solo descargas / todo el dispositivo". */
@Composable
fun LocalHeader(title: String, showToggle: Boolean, includeAll: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.accents.hi
        )
        if (showToggle) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (includeAll) R.string.lp_show_downloads else R.string.lp_show_all))
            }
        }
    }
}

@Composable
fun PermissionContent(permission: MediaPermission) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_media),
            contentDescription = null,
            tint = AloColors.Mut,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.lp_perm_text),
            style = MaterialTheme.typography.bodyLarge,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(onClick = permission.request, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(if (permission.toSettings) R.string.lp_perm_settings else R.string.lp_perm_grant))
        }
    }
}

@Composable
fun EmptyMedia(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_media),
            contentDescription = null,
            tint = AloColors.Mut,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * Antes de abrir se comprueba que el archivo siga existiendo (el registro de MediaStore puede ser viejo).
 * Si no existe: aviso y [onMissing] (refrescar la lista).
 */
@Composable
fun rememberSafePlay(
    onPlay: (List<LocalMedia>, Int) -> Unit,
    onMissing: () -> Unit
): (List<LocalMedia>, Int) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return { list, index ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { mediaExists(ctx, list[index].uri) }
            if (ok) {
                onPlay(list, index)
            } else {
                Toast.makeText(ctx, R.string.lp_missing, Toast.LENGTH_SHORT).show()
                onMissing()
            }
        }
    }
}
