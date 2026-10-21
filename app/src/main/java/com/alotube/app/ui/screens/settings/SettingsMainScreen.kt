package com.alotube.app.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alotube.app.LoginActivity
import com.alotube.app.R
import com.alotube.app.data.auth.SessionStore
import com.alotube.app.data.auth.WebSessionCleaner
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

/** Menú principal de Ajustes: perfil arriba y la lista Ajustes / Buscar actualizaciones / Descargas. */
@Composable
fun SettingsMainScreen(
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val ctx = LocalContext.current
    val session by SessionStore.state.collectAsStateWithLifecycle()
    // El estado se actualiza solo (StateFlow) cuando LoginActivity guarda la sesión
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    var menuOpen by remember { mutableStateOf(false) }

    // Nombre a mostrar: el de la cuenta; si no se pudo leer, la parte inicial del correo
    val connected = stringResource(R.string.account_connected)
    val displayName = session.name
        ?: session.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
        ?: connected
    val initial = if (session.loggedIn) displayName.firstOrNull()?.uppercase() ?: "A" else "A"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AloColors.Bg)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.accents.hi,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // ---- Perfil ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.accents.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = AloColors.Bg
                )
            }
            Spacer(Modifier.width(16.dp))

            if (!session.loggedIn) {
                Button(onClick = {
                    loginLauncher.launch(Intent(ctx, LoginActivity::class.java))
                }) {
                    Text(stringResource(R.string.account_login))
                }
            } else {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = AloColors.Ink
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AloColors.Mut
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_logout)) },
                            onClick = {
                                menuOpen = false
                                SessionStore.clear(ctx)   // cookies guardadas
                                WebSessionCleaner.wipe()  // CookieManager y datos del WebView
                                onLoggedOut()             // reinicia el estado de la app
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---- Lista ----
        SettingsRow(R.drawable.ic_set_gear, stringResource(R.string.settings_item_settings), onOpenSettings)
        SettingsRow(R.drawable.ic_set_refresh, stringResource(R.string.settings_item_updates)) {
            Toast.makeText(ctx, R.string.settings_updates_soon, Toast.LENGTH_SHORT).show()
        }
        SettingsRow(R.drawable.ic_act_download, stringResource(R.string.settings_item_downloads)) {
            Toast.makeText(ctx, R.string.settings_downloads_soon, Toast.LENGTH_SHORT).show()
        }
    }
}
