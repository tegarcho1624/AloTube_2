package com.alotube.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alotube.app.LoginActivity
import com.alotube.app.R
import com.alotube.app.data.auth.SessionStore
import com.alotube.app.data.auth.WebSessionCleaner
import com.alotube.app.player.PlaybackPrefs
import com.alotube.app.ui.components.aloTextFieldColors
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.ThemeUtils
import com.alotube.app.ui.theme.accents

/** Pestaña "Ajustes": elegir el color de acento. Al cambiarlo se recrea la Activity (onAccentChanged). */
@Composable
fun SettingsScreen(onAccentChanged: () -> Unit, onLoggedOut: () -> Unit) {
    val ctx = LocalContext.current
    val current = ThemeUtils.accent(ctx)
    val customs = ThemeUtils.customColors(ctx)
    val swatches = ThemeUtils.ACCENT_COLORS.toList() + customs.filter { it !in ThemeUtils.ACCENT_COLORS }

    var hex by remember { mutableStateOf("") }
    var hexError by remember { mutableStateOf(false) }
    var autoResume by remember { mutableStateOf(PlaybackPrefs.isAutoResume(ctx)) }
    val session by SessionStore.state.collectAsStateWithLifecycle()
    // El estado se actualiza solo (StateFlow) cuando LoginActivity guarda la sesión
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.accents.hi
        )
        Spacer(Modifier.height(20.dp))
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
                                .clickable { pick(color) }
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
            text = stringResource(R.string.audio_title),
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.resume_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AloColors.Ink
                )
                Text(
                    text = stringResource(R.string.resume_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = AloColors.Mut
                )
            }
            Switch(
                checked = autoResume,
                onCheckedChange = {
                    autoResume = it
                    PlaybackPrefs.setAutoResume(ctx, it)
                }
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.account_title),
            style = MaterialTheme.typography.titleMedium,
            color = AloColors.Ink
        )
        if (session.loggedIn) {
            Text(
                text = session.email ?: stringResource(R.string.account_connected),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.accents.hi,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.account_logged_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            OutlinedButton(onClick = {
                SessionStore.clear(ctx)      // cookies guardadas
                WebSessionCleaner.wipe()     // CookieManager y datos del WebView
                onLoggedOut()                // reinicia el estado de la app
            }) {
                Text(stringResource(R.string.account_logout))
            }
        } else {
            Text(
                text = stringResource(R.string.account_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AloColors.Mut,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            Button(onClick = {
                loginLauncher.launch(Intent(ctx, LoginActivity::class.java))
            }) {
                Text(stringResource(R.string.account_login))
            }
        }
    }
}

/** Pestañas que llegarán en fases futuras (música, biblioteca). */
@Composable
fun ComingSoonScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.soon_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.accents.hi
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AloColors.Mut,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
